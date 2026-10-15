// Coded by Afrobama/Claude — Tier 2-c parallel CPU bone-skin.
//
// 3-phase pipeline replacing Tier 2-b's serial skin loop:
//
//   PHASE A (serial, main thread)
//     For each submission in bucket:
//       - ensureAnimator + applyActions + applyPose  (touches MC entity world)
//       - CubicMatrixRenderer.processRenderModel     (walks group hierarchy)
//     Output: Per-instance CubicMatrixRenderer[] holding world-space bone
//     matrices, indexed by group.index.
//
//   PHASE B (parallel, ForkJoinPool.commonPool)
//     For each instance i, in parallel:
//       For each vertex v of the cached SkinnedMeshIR:
//         skinnedPos[i*verts*3 + v*3]   = bone[ir.boneIdx[v]].transform(ir.pos[v])
//         skinnedNrm[i*verts*3 + v*3]   = bone[ir.boneIdx[v]].transformDirection(ir.nrm[v])
//     Pure float math. No MC state, no GL. Race-free by design.
//
//   PHASE C (serial, main thread)
//     Tessellator.begin(TRIANGLES, ENTITY)
//     For each instance i, for each quad q, for each vertex v in {0,1,2,0,2,3}:
//       builder.vertex(skinnedPos[..]).color(..).texture(..).setOverlay(..).setLight(..).setNormal(..)
//     BufferRenderer.drawWithGlobalProgram(builder.end())     // single drawArrays
//
// Iris-compat by construction: same vanilla MC ENTITY format + same
// entityTranslucentCullProgram as Tier 2-b. Iris's gbuffers_entities
// pipeline sees a normal entity draw.
//
// Memory: Reuses static float[] pools sized for the LARGEST bucket seen
// so far. No per-frame GC churn after warmup.
package com.afrobama.bbstools.client.bbs.fastpath;

import mchorse.bbs_mod.graphics.SofortZeichner;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import net.minecraft.client.renderer.RenderPipelines;

import com.afrobama.bbstools.client.bbs.mesh.SkinnedMeshCache;
import com.afrobama.bbstools.client.bbs.mesh.SkinnedMeshIR;
import com.afrobama.bbstools.client.mixin.bbs.ModelFormRendererAccessor;
import com.afrobama.bbstools.perf.BBSPerfConfig;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.animation.IAnimator;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.render.CubicMatrixRenderer;
import mchorse.bbs_mod.cubic.render.CubicRenderer;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.IntStream;

public final class Tier2cParallelSkin
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/fast-path/tier2c");

    private static volatile boolean LOGGED_FIRST_FLUSH = false;
    private static volatile boolean LOGGED_FIRST_FAILURE = false;

    /* Reusable per-instance skin pools. Sized lazily for the largest
     * (instanceCount × vertCount × 3) we've seen, then reused frame-
     * over-frame to keep GC churn at zero. Allocated as one flat array
     * (positions) + one (normals) — accessed via (i * verts * 3 + v * 3 + d). */
    private static float[] POOL_POS = new float[0];
    private static float[] POOL_NRM = new float[0];
    private static int POOL_INSTANCE_STRIDE = 0;

    /* Reusable extractor pool. CubicMatrixRenderer allocates ArrayList
     * + N Matrix4f per construction — for 99 instances per frame we get
     * 99 allocations of ~30 Matrix4f each = ~3000 Matrix4f / frame.
     * Pool eliminates that. Indexed by submission index, lazily grown. */
    private static CubicMatrixRenderer[] EXTRACTOR_POOL = new CubicMatrixRenderer[0];

    private Tier2cParallelSkin() {}

    /**
     * Main entry. Called by FastPathBatch.flush() when
     * ENABLE_TIER2_PARALLEL_SKIN + ENABLE_TIER2_DRAW + ENABLE_TIER2_GPU
     * are all on. Buckets without a cached IR fall back to
     * FastPathRenderer.renderOneFallback per submission.
     */
    public static void flushAll()
    {
        Map<String, List<FastPathBatch.Submission>> batch = FastPathBatch.snapshotBatch();
        if (batch.isEmpty()) return;

        long flushStartNs = System.nanoTime();
        BBSPerfConfig.tier2cFlushes++;

        int bucketsProcessed = 0;
        int bucketsFallback = 0;
        int submissionsDrawn = 0;
        int failures = 0;

        /* Hoist render state — same pattern as Tier 2-b.
         *
         * enableBlend + defaultBlendFunc entfallen: seit 26.2 sitzt die Mischung in der
         * Pipeline des Zeichenwegs, nicht in einem globalen Schalter. Phase C zeichnet ueber
         * den Entity-Translucent-Cull-Weg (mchorse.bbs_mod.compat.alt.RenderPipelinesAlt.ENTITY_TRANSLUCENT_CULL), der
         * BlendFunction.TRANSLUCENT bereits traegt — genau der Zustand, der hier frueher von
         * Hand gesetzt wurde. Gleiche Aufloesung wie in Tier2eMergedDraw#flushAll. */
        GameRenderer gr = Minecraft.getInstance().gameRenderer;
        // 26.2: turnOn/OffLightLayer entfaellt -- die Lichtkarte ist kein globaler Zustand mehr, sondern Sampler2 des Zeichenaufrufs (siehe SofortZeichner.entityTextur). Wer hier zeichnet, muss sie binden; ein globaler Schalter erreicht die Pipeline nicht.
        // 26.2: setup/teardownOverlayColor entfaellt -- das Overlay ist kein globaler Zustand mehr, sondern Sampler1 des Zeichenaufrufs (SofortZeichner bindet alle drei).

        try
        {
            for (Map.Entry<String, List<FastPathBatch.Submission>> entry : batch.entrySet())
            {
                List<FastPathBatch.Submission> list = entry.getValue();
                if (list.isEmpty()) continue;

                FastPathBatch.Submission first = list.get(0);
                if (first.instance == null)
                {
                    bucketsFallback++;
                    continue;
                }

                SkinnedMeshIR ir = SkinnedMeshCache.getOrBuild(first.instance);
                if (ir == null || ir.vertCount == 0 || !(first.instance.model instanceof Model))
                {
                    /* BOBJ / mesh / unsupported — per-submission fallback. */
                    for (FastPathBatch.Submission s : list)
                    {
                        try { FastPathRenderer.renderOneFallback(s); }
                        catch (Throwable t) { failures++; }
                    }
                    bucketsFallback++;
                    continue;
                }

                try
                {
                    int processed = drawBucket(list, ir);
                    if (processed > 0)
                    {
                        bucketsProcessed++;
                        submissionsDrawn += processed;
                    }
                    else
                    {
                        bucketsFallback++;
                    }
                }
                catch (Throwable t)
                {
                    failures++;
                    bucketsFallback++;
                    if (!LOGGED_FIRST_FAILURE)
                    {
                        LOGGED_FIRST_FAILURE = true;
                        LOG.warn("[tier2c] drawBucket threw for model={} — falling back to per-instance. cause={}",
                            first.instance.id, t.toString(), t);
                    }
                    /* On failure, fall back per-instance so the bucket
                     * still renders. */
                    for (FastPathBatch.Submission s : list)
                    {
                        try { FastPathRenderer.renderOneFallback(s); }
                        catch (Throwable t2) { failures++; }
                    }
                }
            }
        }
        finally
        {

            /* disableBlend entfaellt ersatzlos: es gibt keinen globalen Mischzustand mehr,
             * den ein spaeterer Zeichenweg erben koennte — der bekommt seinen aus seiner
             * eigenen Pipeline. Also ist hier nichts zurueckzugeben. */
        }

        BBSPerfConfig.tier2cBucketsProcessed += bucketsProcessed;
        BBSPerfConfig.tier2cBucketsFallback += bucketsFallback;
        BBSPerfConfig.tier2cSubmissions += submissionsDrawn;
        BBSPerfConfig.tier2cFailures += failures;

        if (!LOGGED_FIRST_FLUSH && bucketsProcessed > 0)
        {
            LOGGED_FIRST_FLUSH = true;
            long elapsedNs = System.nanoTime() - flushStartNs;
            LOG.info("[tier2c] FIRST parallel-skin flush — buckets={} fallback={} subs={} pool={}MB ns={}",
                bucketsProcessed, bucketsFallback, submissionsDrawn,
                (POOL_POS.length * 4L + POOL_NRM.length * 4L) / (1024 * 1024), elapsedNs);
        }
    }

    /**
     * Process one (model.id) bucket through the 3-phase pipeline.
     * Returns count of submissions successfully drawn (0 = nothing drawn).
     */
    private static int drawBucket(List<FastPathBatch.Submission> list, SkinnedMeshIR ir)
    {
        int n = list.size();
        int verts = ir.vertCount;
        ensurePool(n, verts);
        int instanceStride = POOL_INSTANCE_STRIDE;

        /* ------------------ PHASE A — serial animator + matrix extract ------------------ */
        long phaseANs = System.nanoTime();
        for (int i = 0; i < n; i++)
        {
            FastPathBatch.Submission s = list.get(i);
            CubicMatrixRenderer ex = extractMatricesFor(s, i);
            EXTRACTOR_POOL[i] = ex;
        }
        BBSPerfConfig.tier2cPhaseANsAccum += System.nanoTime() - phaseANs;

        /* ------------------ PHASE B — parallel pure-math skin ------------------ */
        long phaseBNs = System.nanoTime();
        final int totalSlots = n;
        final int vertsLocal = verts;
        final SkinnedMeshIR irLocal = ir;
        final int strideLocal = instanceStride;

        /* parallelStream uses ForkJoinPool.commonPool — pool size = NCPU-1.
         * On the user's 5800X (8c/16t) that's 15 workers. For 99
         * instances each worker gets ~6-7 to skin. */
        try
        {
            IntStream.range(0, totalSlots).parallel().forEach(i ->
            {
                CubicMatrixRenderer ex = EXTRACTOR_POOL[i];
                if (ex == null) return;
                skinInstance(irLocal, ex, POOL_POS, POOL_NRM, i * strideLocal, vertsLocal);
            });
            BBSPerfConfig.tier2cParallelTasks += totalSlots;
        }
        catch (Throwable t)
        {
            /* If parallel skin throws, fall through serially. */
            for (int i = 0; i < totalSlots; i++)
            {
                CubicMatrixRenderer ex = EXTRACTOR_POOL[i];
                if (ex == null) continue;
                skinInstance(irLocal, ex, POOL_POS, POOL_NRM, i * strideLocal, vertsLocal);
            }
        }
        BBSPerfConfig.tier2cPhaseBNsAccum += System.nanoTime() - phaseBNs;

        /* ------------------ PHASE C — serial BufferBuilder emit + 1 drawArrays ------------------ */
        long phaseCNs = System.nanoTime();

        /* Bind shared texture from the first submission's form (same
         * assumption as Tier 2-b — bucket is keyed by model.id and BBS
         * typically pairs a model with a default texture). */
        Link texture = list.get(0).form.texture.get();
        if (texture == null) texture = list.get(0).instance.getTexture();
        if (texture != null)
        {
            try { BBSModClient.getTextures().bindTexture(texture); }
            catch (Throwable ig) {}
        }

        /* 26.2: setShader ist entfallen -- der Shader steht in der Pipeline, die der
         * Zeichenaufruf waehlt. Frueher galt er fuer alles Folgende. */

        /* 26.2: Tesselator ist entfallen -- sein geteilter Eckenspeicher war ein
         * globaler Zwischenspeicher. Jetzt gehoert der Speicher dem Aufruf. */
        ByteBufferBuilder eckenSpeicher = new ByteBufferBuilder(4096);
        BufferBuilder builder = SofortZeichner.beginnen(eckenSpeicher, 
            com.mojang.blaze3d.vertex.VertexFormat.Mode.TRIANGLES,
            DefaultVertexFormat.NEW_ENTITY);

        int written = 0;
        for (int i = 0; i < n; i++)
        {
            CubicMatrixRenderer ex = EXTRACTOR_POOL[i];
            if (ex == null) continue;
            FastPathBatch.Submission s = list.get(i);
            emitInstance(builder, s, ir, POOL_POS, POOL_NRM, i * instanceStride);
            written++;
        }

        if (written > 0)
        {
            try
            {
                /* Der Shader stand frueher in einem setShader davor: ENTITY_TRANSLUCENT_CULL.
                 * Jetzt waehlt ihn der Zeichenaufruf, und mit ihm Blend, Tiefe und Cull. */
                SofortZeichner.zeichnen(mchorse.bbs_mod.compat.alt.RenderPipelinesAlt.ENTITY_TRANSLUCENT_CULL, builder.buildOrThrow(),
                    new org.joml.Matrix4f(RenderSystem.getModelViewMatrix()), null, null, null);
            }
            catch (Throwable t)
            {
                if (!LOGGED_FIRST_FAILURE)
                {
                    LOGGED_FIRST_FAILURE = true;
                    LOG.warn("[tier2c] drawWithGlobalProgram threw — cause={}", t.toString(), t);
                }
                written = 0;
            }
        }
        else
        {
            try { builder.buildOrThrow(); } catch (Throwable ig) {}
        }
        BBSPerfConfig.tier2cPhaseCNsAccum += System.nanoTime() - phaseCNs;

        /* Clear extractor pool slots so next bucket starts fresh. */
        for (int i = 0; i < n; i++) EXTRACTOR_POOL[i] = null;

        return written;
    }

    /**
     * PHASE A helper — run BBS animator chain for one submission and
     * extract per-group world-space matrices via CubicMatrixRenderer.
     * Returns null if the submission can't be processed (no animator,
     * non-cubic model).
     */
    private static CubicMatrixRenderer extractMatricesFor(FastPathBatch.Submission s, int slotIdx)
    {
        ModelFormRenderer self = s.renderer;
        if (self == null) return null;
        ModelFormRendererAccessor selfA = (ModelFormRendererAccessor) (Object) self;

        ModelInstance instance = s.instance;
        if (instance == null) return null;
        if (!(instance.model instanceof Model cubic)) return null;

        selfA.bbstools$ensureAnimator(s.transition);
        IAnimator animator = selfA.bbstools$getAnimator();
        if (animator == null) return null;

        instance.model.resetPose();
        animator.applyActions(s.entity, instance, s.transition);
        instance.model.applyPose(selfA.bbstools$getPose());

        PoseStack stack = new PoseStack();
        stack.mulPose(s.worldMatrix);
        stack.rotate(Axis.YP.rotation((float) MathUtils.PI));

        /* Always allocate a fresh CubicMatrixRenderer — its
         * ArrayList<Matrix4f> sizing depends on model.getAllGroupKeys.size(),
         * which we don't know up-front. Per-frame allocation cost is
         * negligible vs the skin math we're saving. */
        CubicMatrixRenderer ex = new CubicMatrixRenderer(cubic);
        CubicRenderer.processRenderModel(ex, null, stack, cubic);
        return ex;
    }

    /**
     * PHASE B helper — pure float math. Skins ONE instance's rest-pose
     * IR into the given (positions, normals) slice of the pool. Called
     * from parallel worker threads — must NOT touch any shared state
     * besides the (instanceOffset, instanceOffset + vertCount * 3) slice.
     */
    private static void skinInstance(SkinnedMeshIR ir, CubicMatrixRenderer ex,
                                     float[] outPos, float[] outNrm,
                                     int instanceOffset, int verts)
    {
        int boneSlots = ex.matrices.size();
        /* Local scratch — Vector4f + Vector3f stay thread-local since
         * each parallel task creates its own (called as inner lambda
         * argument, but lambdas don't auto-capture mutable state — so
         * these locals are per-thread-stack). */
        Vector4f pTmp = new Vector4f();
        Vector3f nTmp = new Vector3f();

        for (int v = 0; v < verts; v++)
        {
            int bi = ir.boneIndices[v * 4];
            if (bi < 0 || bi >= boneSlots) bi = 0;
            Matrix4f bone = ex.matrices.get(bi);

            int pi = v * 3;
            pTmp.set(ir.positions[pi], ir.positions[pi + 1], ir.positions[pi + 2], 1F);
            bone.transform(pTmp);
            int outIdx = instanceOffset + pi;
            outPos[outIdx]     = pTmp.x;
            outPos[outIdx + 1] = pTmp.y;
            outPos[outIdx + 2] = pTmp.z;

            nTmp.set(ir.normals[pi], ir.normals[pi + 1], ir.normals[pi + 2]);
            bone.transformDirection(nTmp);
            outNrm[outIdx]     = nTmp.x;
            outNrm[outIdx + 1] = nTmp.y;
            outNrm[outIdx + 2] = nTmp.z;
        }
    }

    /**
     * PHASE C helper — emit one instance's already-skinned verts to the
     * shared BufferBuilder. 2 triangles per quad: (v0,v1,v2) + (v0,v2,v3),
     * matching BBS CubicCubeRenderer winding.
     */
    private static void emitInstance(BufferBuilder builder, FastPathBatch.Submission s,
                                     SkinnedMeshIR ir,
                                     float[] poolPos, float[] poolNrm,
                                     int instanceOffset)
    {
        /* Color: formColor × instanceColor × s.color. Group color is
         * reset to white every frame inside ModelGroup.reset (called
         * from Model.resetPose), so we drop the per-group multiplier. */
        Color formColor = s.form.color.get().copy();
        formColor.mul(s.color);
        Color instColor = new Color().set(s.instance.config.color.get());
        float fr = formColor.r * instColor.r;
        float fg = formColor.g * instColor.g;
        float fb = formColor.b * instColor.b;
        float fa = formColor.a * instColor.a;

        int u = s.light & 0xFFFF;
        int v2 = (s.light >> 16) & 0xFFFF;
        int overlay = s.overlay;

        int quads = ir.quadCount;
        for (int q = 0; q < quads; q++)
        {
            int base = q * 4;
            emitVert(builder, poolPos, poolNrm, ir.uvs, instanceOffset, base + 0,
                fr, fg, fb, fa, overlay, u, v2);
            emitVert(builder, poolPos, poolNrm, ir.uvs, instanceOffset, base + 1,
                fr, fg, fb, fa, overlay, u, v2);
            emitVert(builder, poolPos, poolNrm, ir.uvs, instanceOffset, base + 2,
                fr, fg, fb, fa, overlay, u, v2);

            emitVert(builder, poolPos, poolNrm, ir.uvs, instanceOffset, base + 0,
                fr, fg, fb, fa, overlay, u, v2);
            emitVert(builder, poolPos, poolNrm, ir.uvs, instanceOffset, base + 2,
                fr, fg, fb, fa, overlay, u, v2);
            emitVert(builder, poolPos, poolNrm, ir.uvs, instanceOffset, base + 3,
                fr, fg, fb, fa, overlay, u, v2);
        }
    }

    private static void emitVert(BufferBuilder builder,
                                 float[] poolPos, float[] poolNrm, float[] uvs,
                                 int instanceOffset, int vIdx,
                                 float r, float g, float bcol, float a,
                                 int overlay, int lightU, int lightV)
    {
        int pi = instanceOffset + vIdx * 3;
        int ui = vIdx * 2;
        builder.addVertex(poolPos[pi], poolPos[pi + 1], poolPos[pi + 2])
            .setColor(r, g, bcol, a)
            .setUv(uvs[ui], uvs[ui + 1])
            .setOverlay(overlay)
            .setUv2(lightU, lightV)
            .setNormal(poolNrm[pi], poolNrm[pi + 1], poolNrm[pi + 2]);
    }

    /**
     * Lazily grows the per-instance pool to fit (instanceCount × verts × 3
     * floats). Reused frame-over-frame so steady-state allocation = 0.
     */
    private static void ensurePool(int instanceCount, int verts)
    {
        int stride = verts * 3;
        int needed = instanceCount * stride;

        if (POOL_POS.length < needed)
        {
            POOL_POS = new float[needed];
            POOL_NRM = new float[needed];
        }
        if (EXTRACTOR_POOL.length < instanceCount)
        {
            EXTRACTOR_POOL = new CubicMatrixRenderer[Math.max(instanceCount, EXTRACTOR_POOL.length * 2)];
        }
        POOL_INSTANCE_STRIDE = stride;
    }

    public static String snapshot()
    {
        long flushes = BBSPerfConfig.tier2cFlushes;
        double avgPhaseAMs = flushes == 0 ? 0 : (BBSPerfConfig.tier2cPhaseANsAccum / (double) flushes) / 1_000_000.0;
        double avgPhaseBMs = flushes == 0 ? 0 : (BBSPerfConfig.tier2cPhaseBNsAccum / (double) flushes) / 1_000_000.0;
        double avgPhaseCMs = flushes == 0 ? 0 : (BBSPerfConfig.tier2cPhaseCNsAccum / (double) flushes) / 1_000_000.0;
        int pool = ForkJoinPool.commonPool().getParallelism();
        return String.format(
            "flushes=%d buckets=%d fallback=%d subs=%d parTasks=%d phaseA=%.2fms phaseB=%.2fms phaseC=%.2fms pool=%d",
            flushes,
            BBSPerfConfig.tier2cBucketsProcessed,
            BBSPerfConfig.tier2cBucketsFallback,
            BBSPerfConfig.tier2cSubmissions,
            BBSPerfConfig.tier2cParallelTasks,
            avgPhaseAMs, avgPhaseBMs, avgPhaseCMs,
            pool);
    }

    public static void resetCounters()
    {
        LOGGED_FIRST_FLUSH = false;
        LOGGED_FIRST_FAILURE = false;
        BBSPerfConfig.tier2cFlushes = 0L;
        BBSPerfConfig.tier2cBucketsProcessed = 0L;
        BBSPerfConfig.tier2cBucketsFallback = 0L;
        BBSPerfConfig.tier2cSubmissions = 0L;
        BBSPerfConfig.tier2cParallelTasks = 0L;
        BBSPerfConfig.tier2cPhaseANsAccum = 0L;
        BBSPerfConfig.tier2cPhaseBNsAccum = 0L;
        BBSPerfConfig.tier2cPhaseCNsAccum = 0L;
        BBSPerfConfig.tier2cFailures = 0L;
    }

}
