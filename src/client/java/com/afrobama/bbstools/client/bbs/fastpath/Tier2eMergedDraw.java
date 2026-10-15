// Coded by Afrobama/Claude — Tier 2-e merged-buffer draw.
//
// Tier 2-d shipped parallel emit but kept N back-to-back drawArrays
// (~250µs each × 15 chunks = 3.65ms at 200 replays). Tier 2-e merges
// all worker BuiltBuffers into ONE master ByteBuffer + ONE drawArrays.
//
//   PHASE A   (serial) — animator chain + matrix extract.
//   PHASE B+C (parallel, N workers) — per-worker BufferBuilder emit
//     (each worker's MixinBufferBuilder triggers Iris extension to 54B
//     independently — format stays consistent across workers).
//   PHASE E   (serial) — collect worker BuiltBuffers, sum total bytes
//     + total vertices + total indices, allocate master BufferAllocator,
//     memcpy each worker's ByteBuffer into the master, construct a
//     synthetic BuiltBuffer via the public DrawParameters record, single
//     BufferRenderer.drawWithGlobalProgram call.
//
// Iris-compat: the merged ByteBuffer inherits the format from the per-
// worker BufferBuilders (all use same DrawMode.TRIANGLES + ENTITY
// format, Iris's MixinBufferBuilder extends each consistently).
package com.afrobama.bbstools.client.bbs.fastpath;

import mchorse.bbs_mod.graphics.SofortZeichner;
import net.minecraft.client.renderer.RenderPipelines;

import com.afrobama.bbstools.client.bbs.mesh.SkinnedMeshCache;
import com.afrobama.bbstools.client.bbs.mesh.SkinnedMeshIR;
import com.afrobama.bbstools.client.mixin.bbs.ModelFormRendererAccessor;
import com.afrobama.bbstools.perf.BBSPerfConfig;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
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
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.IntStream;

public final class Tier2eMergedDraw
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/fast-path/tier2e");

    private static volatile boolean LOGGED_FIRST_FLUSH = false;
    private static volatile boolean LOGGED_FIRST_FAILURE = false;

    private static CubicMatrixRenderer[] EXTRACTOR_POOL = new CubicMatrixRenderer[0];

    /** Per-worker result holders. */
    private static WorkerResult[] WORKER_RESULTS = new WorkerResult[0];

    /** Iris-extended ENTITY vertex format estimate = 54B; pad 2× to
     *  avoid BufferAllocator growth on the per-worker emit. */
    private static final int IRIS_EXT_BYTES_PER_VERT = 96;

    private Tier2eMergedDraw() {}

    public static void flushAll()
    {
        Map<String, List<FastPathBatch.Submission>> batch = FastPathBatch.snapshotBatch();
        if (batch.isEmpty()) return;

        long flushStartNs = System.nanoTime();
        BBSPerfConfig.tier2eFlushes++;

        int bucketsProcessed = 0;
        int bucketsFallback = 0;
        int submissionsDrawn = 0;
        int mergedDraws = 0;
        int failures = 0;

        /* enableBlend + defaultBlendFunc entfallen: seit 26.2 sitzt die Mischung in der
         * Pipeline des Zeichenwegs, nicht in einem globalen Schalter. Der zusammengefuehrte
         * Draw in drawBucket laeuft ueber den Entity-Translucent-Cull-Weg
         * (mchorse.bbs_mod.compat.alt.RenderPipelinesAlt.ENTITY_TRANSLUCENT_CULL), der BlendFunction.TRANSLUCENT bereits
         * traegt — genau der Zustand, der hier frueher von Hand gesetzt wurde. */
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
                if (first.instance == null) { bucketsFallback++; continue; }

                SkinnedMeshIR ir = SkinnedMeshCache.getOrBuild(first.instance);
                if (ir == null || ir.vertCount == 0 || !(first.instance.model instanceof Model))
                {
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
                    int written = drawBucket(list, ir);
                    if (written > 0)
                    {
                        bucketsProcessed++;
                        submissionsDrawn += written;
                        mergedDraws++;
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
                        LOG.warn("[tier2e] drawBucket threw for model={} — falling back. cause={}",
                            first.instance.id, t.toString(), t);
                    }
                    for (FastPathBatch.Submission s : list)
                    {
                        try { FastPathRenderer.renderOneFallback(s); } catch (Throwable ig) {}
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

        BBSPerfConfig.tier2eBucketsProcessed += bucketsProcessed;
        BBSPerfConfig.tier2eBucketsFallback += bucketsFallback;
        BBSPerfConfig.tier2eSubmissions += submissionsDrawn;
        BBSPerfConfig.tier2eMergedDraws += mergedDraws;
        BBSPerfConfig.tier2eFailures += failures;

        if (!LOGGED_FIRST_FLUSH && bucketsProcessed > 0)
        {
            LOGGED_FIRST_FLUSH = true;
            long elapsedNs = System.nanoTime() - flushStartNs;
            int workers = ForkJoinPool.commonPool().getParallelism();
            LOG.info("[tier2e] FIRST merged-buffer flush — buckets={} subs={} mergedDraws={} workers={} ns={}",
                bucketsProcessed, submissionsDrawn, mergedDraws, workers, elapsedNs);
        }
    }

    private static int drawBucket(List<FastPathBatch.Submission> list, SkinnedMeshIR ir)
    {
        int n = list.size();
        ensureExtractorPool(n);

        /* ------------------ PHASE A — serial animator + matrix extract ------------------ */
        long phaseANs = System.nanoTime();
        for (int i = 0; i < n; i++)
        {
            EXTRACTOR_POOL[i] = extractMatricesFor(list.get(i));
        }
        BBSPerfConfig.tier2ePhaseANsAccum += System.nanoTime() - phaseANs;

        /* ------------------ PHASE B+C — parallel chunked emit ------------------ */
        long phaseBCNs = System.nanoTime();

        int poolSize = ForkJoinPool.commonPool().getParallelism();
        if (poolSize < 1) poolSize = 1;
        int nWorkers = Math.min(n, poolSize);
        int chunkSize = (n + nWorkers - 1) / nWorkers;

        ensureWorkerResults(nWorkers);
        for (int w = 0; w < nWorkers; w++) WORKER_RESULTS[w] = null;

        final int fChunkSize = chunkSize;
        final int fN = n;
        final List<FastPathBatch.Submission> fList = list;
        final SkinnedMeshIR fIR = ir;
        final int fEstimatedBytesPerInstance = ir.vertCount * IRIS_EXT_BYTES_PER_VERT * 2;

        try
        {
            IntStream.range(0, nWorkers).parallel().forEach(w ->
            {
                int start = w * fChunkSize;
                int end = Math.min(start + fChunkSize, fN);
                if (start >= fN) return;

                int subsInChunk = end - start;
                int initialCapacity = fEstimatedBytesPerInstance * subsInChunk + 4096;

                ByteBufferBuilder alloc;
                BufferBuilder b;
                try
                {
                    alloc = new ByteBufferBuilder(initialCapacity);
                    b = mchorse.bbs_mod.compat.vertex.BauerAlt.neu(alloc,
                        com.mojang.blaze3d.vertex.VertexFormat.Mode.TRIANGLES,
                        DefaultVertexFormat.NEW_ENTITY);
                }
                catch (Throwable t)
                {
                    if (!LOGGED_FIRST_FAILURE)
                    {
                        LOGGED_FIRST_FAILURE = true;
                        LOG.warn("[tier2e] BufferBuilder construct threw — cause={}", t.toString(), t);
                    }
                    return;
                }

                int written = 0;
                try
                {
                    for (int i = start; i < end; i++)
                    {
                        CubicMatrixRenderer ex = EXTRACTOR_POOL[i];
                        if (ex == null) continue;
                        emitInstance(b, fList.get(i), fIR, ex);
                        written++;
                    }
                }
                catch (Throwable t)
                {
                    if (!LOGGED_FIRST_FAILURE)
                    {
                        LOGGED_FIRST_FAILURE = true;
                        LOG.warn("[tier2e] worker emit threw — cause={}", t.toString(), t);
                    }
                }

                if (written > 0)
                {
                    MeshData built;
                    try
                    {
                        built = b.buildOrThrow();
                    }
                    catch (Throwable t)
                    {
                        try { alloc.close(); } catch (Throwable ig) {}
                        return;
                    }
                    WORKER_RESULTS[w] = new WorkerResult(alloc, built, written);
                }
                else
                {
                    try { b.buildOrThrow().close(); } catch (Throwable ig) {}
                    try { alloc.close(); } catch (Throwable ig) {}
                }
            });
        }
        catch (Throwable t)
        {
            for (int w = 0; w < nWorkers; w++)
            {
                WorkerResult r = WORKER_RESULTS[w];
                if (r != null)
                {
                    try { r.built.close(); } catch (Throwable ig) {}
                    try { r.alloc.close(); } catch (Throwable ig) {}
                    WORKER_RESULTS[w] = null;
                }
            }
            throw t;
        }
        BBSPerfConfig.tier2ePhaseBCNsAccum += System.nanoTime() - phaseBCNs;

        /* ------------------ PHASE E — collect, merge, single draw ------------------ */
        long phaseENs = System.nanoTime();

        /* Collect worker results: sum bytes + vertices + indices. */
        int totalBytes = 0;
        int totalVerts = 0;
        int totalIndices = 0;
        int totalWritten = 0;
        MeshData.DrawState refParams = null;

        for (int w = 0; w < nWorkers; w++)
        {
            WorkerResult r = WORKER_RESULTS[w];
            if (r == null) continue;
            ByteBuffer buf = r.built.vertexBuffer();
            int remaining = buf.remaining();
            if (remaining <= 0) continue;
            MeshData.DrawState p = r.built.drawState();
            totalBytes += remaining;
            totalVerts += p.vertexCount();
            totalIndices += p.indexCount();
            totalWritten += r.writtenCount;
            if (refParams == null) refParams = p;
        }

        if (totalBytes == 0 || refParams == null)
        {
            /* Nothing to draw — clean up empty buckets. */
            cleanupWorkers(nWorkers);
            for (int i = 0; i < n; i++) EXTRACTOR_POOL[i] = null;
            BBSPerfConfig.tier2ePhaseENsAccum += System.nanoTime() - phaseENs;
            return 0;
        }

        /* Allocate master buffer + memcpy worker bytes. */
        ByteBufferBuilder masterAlloc = null;
        MeshData masterBuilt = null;
        boolean drewOk = false;
        try
        {
            masterAlloc = new ByteBufferBuilder(totalBytes);
            long masterPtr = masterAlloc.reserve(totalBytes);
            ByteBuffer master = MemoryUtil.memByteBuffer(masterPtr, totalBytes);
            master.clear();

            for (int w = 0; w < nWorkers; w++)
            {
                WorkerResult r = WORKER_RESULTS[w];
                if (r == null) continue;
                ByteBuffer src = r.built.vertexBuffer();
                if (src.remaining() <= 0) continue;
                int srcPos = src.position();
                int srcLim = src.limit();
                master.put(src);
                /* Restore src position in case anyone else reads it. */
                src.position(srcPos).limit(srcLim);
            }
            master.flip();

            ByteBufferBuilder.Result cb = masterAlloc.build();
            /* 26.2: DrawState.mode() heisst jetzt primitiveTopology() und liefert
             * VertexFormat.Mode statt VertexFormat.Mode -- reine Umbenennung, gleiche Stelle
             * im Datensatz. */
            MeshData.DrawState mergedParams = new MeshData.DrawState(
                refParams.format(),
                totalVerts,
                totalIndices,
                refParams.primitiveTopology(),
                refParams.indexType());
            masterBuilt = new MeshData(cb, mergedParams);

            /* Bind shared texture + shader. */
            Link texture = list.get(0).form.texture.get();
            if (texture == null) texture = list.get(0).instance.getTexture();
            if (texture != null)
            {
                try { BBSModClient.getTextures().bindTexture(texture); } catch (Throwable ig) {}
            }
            /* 26.2: setShader ist entfallen -- der Shader steht in der Pipeline, die der
             * Zeichenaufruf waehlt. Frueher galt er fuer alles Folgende. */

            /* ⛔ OFFEN 26.2 — steht absichtlich noch so da, nicht geraten:
             *  Der Zug zeichnet NEW_ENTITY-Dreiecke mit dem Entity-Translucent-Cull-Programm.
             *  Die passende Pipeline gibt es (mchorse.bbs_mod.compat.alt.RenderPipelinesAlt.ENTITY_TRANSLUCENT_CULL), aber
             *  sie braucht neben Sampler0 auch Overlay und Lightmap — genau das, was oben
             *  setupOverlayColor/turnOnLightLayer global stellen. SofortZeichner bindet nur
             *  Sampler0, FormPipelines deckt nur die Positions-/Texturformate ab. Ohne die
             *  beiden anderen Bindungen zeichnet der Durchgang nicht bloss falsch, er faellt
             *  aus — deshalb bleibt der alte Aufruf stehen, bis der Entity-Zeichenweg da ist. */
            /* Der Shader stand frueher in einem setShader davor: ENTITY_TRANSLUCENT_CULL.
             * Jetzt waehlt ihn der Zeichenaufruf, und mit ihm Blend, Tiefe und Cull. */
            SofortZeichner.zeichnen(mchorse.bbs_mod.compat.alt.RenderPipelinesAlt.ENTITY_TRANSLUCENT_CULL, masterBuilt,
                new org.joml.Matrix4f(RenderSystem.getModelViewMatrix()), null, null, null);
            drewOk = true;
            BBSPerfConfig.tier2eMergedBytes += totalBytes;
        }
        catch (Throwable t)
        {
            if (!LOGGED_FIRST_FAILURE)
            {
                LOGGED_FIRST_FAILURE = true;
                LOG.warn("[tier2e] merge/draw threw — cause={}", t.toString(), t);
            }
        }
        finally
        {
            if (masterBuilt != null)
            {
                try { masterBuilt.close(); } catch (Throwable ig) {}
            }
            if (masterAlloc != null)
            {
                try { masterAlloc.close(); } catch (Throwable ig) {}
            }
            cleanupWorkers(nWorkers);
            for (int i = 0; i < n; i++) EXTRACTOR_POOL[i] = null;
        }
        BBSPerfConfig.tier2ePhaseENsAccum += System.nanoTime() - phaseENs;

        return drewOk ? totalWritten : 0;
    }

    private static void cleanupWorkers(int nWorkers)
    {
        for (int w = 0; w < nWorkers; w++)
        {
            WorkerResult r = WORKER_RESULTS[w];
            if (r == null) continue;
            try { r.built.close(); } catch (Throwable ig) {}
            try { r.alloc.close(); } catch (Throwable ig) {}
            WORKER_RESULTS[w] = null;
        }
    }

    /* ----------------------- shared helpers ----------------------- */

    private static CubicMatrixRenderer extractMatricesFor(FastPathBatch.Submission s)
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

        CubicMatrixRenderer ex = new CubicMatrixRenderer(cubic);
        CubicRenderer.processRenderModel(ex, null, stack, cubic);
        return ex;
    }

    private static void emitInstance(BufferBuilder builder,
                                     FastPathBatch.Submission s,
                                     SkinnedMeshIR ir,
                                     CubicMatrixRenderer ex)
    {
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

        int boneSlots = ex.matrices.size();
        Vector4f pTmp = new Vector4f();
        Vector3f nTmp = new Vector3f();

        int quads = ir.quadCount;
        for (int q = 0; q < quads; q++)
        {
            int base = q * 4;
            emitVert(builder, ir, ex, boneSlots, base + 0, fr, fg, fb, fa, overlay, u, v2, pTmp, nTmp);
            emitVert(builder, ir, ex, boneSlots, base + 1, fr, fg, fb, fa, overlay, u, v2, pTmp, nTmp);
            emitVert(builder, ir, ex, boneSlots, base + 2, fr, fg, fb, fa, overlay, u, v2, pTmp, nTmp);
            emitVert(builder, ir, ex, boneSlots, base + 0, fr, fg, fb, fa, overlay, u, v2, pTmp, nTmp);
            emitVert(builder, ir, ex, boneSlots, base + 2, fr, fg, fb, fa, overlay, u, v2, pTmp, nTmp);
            emitVert(builder, ir, ex, boneSlots, base + 3, fr, fg, fb, fa, overlay, u, v2, pTmp, nTmp);
        }
    }

    private static void emitVert(BufferBuilder builder, SkinnedMeshIR ir, CubicMatrixRenderer ex,
                                 int boneSlots, int vIdx,
                                 float cr, float cg, float cb, float ca,
                                 int overlay, int lightU, int lightV,
                                 Vector4f pTmp, Vector3f nTmp)
    {
        int bi = ir.boneIndices[vIdx * 4];
        if (bi < 0 || bi >= boneSlots) bi = 0;
        Matrix4f bone = ex.matrices.get(bi);

        int pi = vIdx * 3;
        pTmp.set(ir.positions[pi], ir.positions[pi + 1], ir.positions[pi + 2], 1F);
        bone.transform(pTmp);

        nTmp.set(ir.normals[pi], ir.normals[pi + 1], ir.normals[pi + 2]);
        bone.transformDirection(nTmp);

        int ui = vIdx * 2;
        builder.addVertex(pTmp.x, pTmp.y, pTmp.z)
            .setColor(cr, cg, cb, ca)
            .setUv(ir.uvs[ui], ir.uvs[ui + 1])
            .setOverlay(overlay)
            .setUv2(lightU, lightV)
            .setNormal(nTmp.x, nTmp.y, nTmp.z);
    }

    private static void ensureExtractorPool(int needed)
    {
        if (EXTRACTOR_POOL.length < needed)
        {
            EXTRACTOR_POOL = new CubicMatrixRenderer[Math.max(needed, EXTRACTOR_POOL.length * 2)];
        }
    }

    private static void ensureWorkerResults(int needed)
    {
        if (WORKER_RESULTS.length < needed)
        {
            WORKER_RESULTS = new WorkerResult[Math.max(needed, WORKER_RESULTS.length * 2)];
        }
    }

    private static final class WorkerResult
    {
        final ByteBufferBuilder alloc;
        final MeshData built;
        final int writtenCount;

        WorkerResult(ByteBufferBuilder alloc, MeshData built, int writtenCount)
        {
            this.alloc = alloc;
            this.built = built;
            this.writtenCount = writtenCount;
        }
    }

    public static String snapshot()
    {
        long flushes = BBSPerfConfig.tier2eFlushes;
        double avgPhaseAMs = flushes == 0 ? 0 : (BBSPerfConfig.tier2ePhaseANsAccum / (double) flushes) / 1_000_000.0;
        double avgPhaseBCMs = flushes == 0 ? 0 : (BBSPerfConfig.tier2ePhaseBCNsAccum / (double) flushes) / 1_000_000.0;
        double avgPhaseEMs = flushes == 0 ? 0 : (BBSPerfConfig.tier2ePhaseENsAccum / (double) flushes) / 1_000_000.0;
        long avgMergedKB = flushes == 0 ? 0 : (BBSPerfConfig.tier2eMergedBytes / flushes) / 1024;
        return String.format(
            "flushes=%d buckets=%d fallback=%d subs=%d mergedDraws=%d phaseA=%.2fms phaseBC=%.2fms phaseE=%.2fms avgMergedKB=%d",
            flushes,
            BBSPerfConfig.tier2eBucketsProcessed,
            BBSPerfConfig.tier2eBucketsFallback,
            BBSPerfConfig.tier2eSubmissions,
            BBSPerfConfig.tier2eMergedDraws,
            avgPhaseAMs, avgPhaseBCMs, avgPhaseEMs, avgMergedKB);
    }

    public static void resetCounters()
    {
        LOGGED_FIRST_FLUSH = false;
        LOGGED_FIRST_FAILURE = false;
        BBSPerfConfig.tier2eFlushes = 0L;
        BBSPerfConfig.tier2eBucketsProcessed = 0L;
        BBSPerfConfig.tier2eBucketsFallback = 0L;
        BBSPerfConfig.tier2eSubmissions = 0L;
        BBSPerfConfig.tier2eMergedDraws = 0L;
        BBSPerfConfig.tier2ePhaseANsAccum = 0L;
        BBSPerfConfig.tier2ePhaseBCNsAccum = 0L;
        BBSPerfConfig.tier2ePhaseENsAccum = 0L;
        BBSPerfConfig.tier2eMergedBytes = 0L;
        BBSPerfConfig.tier2eFailures = 0L;
    }

}
