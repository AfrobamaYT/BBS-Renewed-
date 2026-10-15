// Coded by Afrobama/Claude — Tier 2-d parallel BufferBuilder emit.
//
// Closes the Tier 2-c gap where Phase C (BufferBuilder emit) stayed
// serial. Architecture:
//
//   PHASE A (serial, main thread)
//     For each submission: animator chain + CubicMatrixRenderer extract.
//     Same as Tier 2-c — touches MC entity world, can't parallelize.
//
//   PHASE B+C (parallel, N=min(submissions, ForkJoinPool.commonPool.size))
//     Partition submissions into N chunks. Each worker:
//       1. Allocates its OWN BufferAllocator + BufferBuilder.
//       2. Iris's MixinBufferBuilder hooks the per-worker instance and
//          extends each vertex emit to 54B Iris format transparently.
//       3. For each submission in chunk: skin verts via bone matrix +
//          emit through builder.vertex/.color/.texture/etc chain.
//       4. builder.end() → BuiltBuffer. Returned to main thread.
//
//   PHASE D (serial, main thread)
//     Hoist render state (blend, lightmap, overlay, texture, shader)
//     ONCE for the whole bucket. Then N back-to-back
//     BufferRenderer.drawWithGlobalProgram calls. State stays bound —
//     driver pipelines efficiently (~30-50µs per draw vs ~5ms emit).
//
// Iris-compat by construction: each per-worker BufferBuilder is patched
// independently by Iris's mixin. drawWithGlobalProgram is the same path
// Tier 2-b/c use, so Iris's gbuffers_entities handles it normally.
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.IntStream;

public final class Tier2dParallelEmit
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/fast-path/tier2d");

    private static volatile boolean LOGGED_FIRST_FLUSH = false;
    private static volatile boolean LOGGED_FIRST_FAILURE = false;

    /** Extractor pool — lazily grown to fit largest bucket. */
    private static CubicMatrixRenderer[] EXTRACTOR_POOL = new CubicMatrixRenderer[0];

    /** Worker-result slots — one per active worker. */
    private static WorkerResult[] WORKER_RESULTS = new WorkerResult[0];

    /* Tier 3 lite pools — per-WORKER-SLOT, opt-in via ENABLE_TIER3_POOLS. */

    /** Per-worker-slot BufferAllocator pool, indexed by the work-item index {@code w}.
     *
     *  WAS a {@code ThreadLocal<BufferAllocator>} — that was a USE-AFTER-RESET bug:
     *  emit dispatches {@code nWorkers} tasks over a SMALLER ForkJoinPool, so one
     *  thread runs multiple work-items per pass. Task w1 builds a BuiltBuffer from the
     *  thread's pooled allocator, stores it in WORKER_RESULTS[w1] (drawn later, serially,
     *  in Phase D). Task w2 on the SAME thread then does ALLOC_TL.get().reset() →
     *  wipes w1's still-undrawn buffer → Phase D draws it → "Buffer is no longer valid",
     *  leaving a half-bound VAO/EBO that the next vanilla draw (particles) inherits →
     *  native driver null-deref (EXCEPTION_ACCESS_VIOLATION in glDrawElements).
     *
     *  Per-SLOT is correct: each pass runs work-item w exactly once and Phase D fully
     *  consumes (draws) every slot's buffer before the next pass resets that slot, so
     *  no two live BuiltBuffers ever share an allocator. Only task w touches slot w, so
     *  no cross-thread contention. Grown (preserving entries) in ensureWorkerResults. */
    private static ByteBufferBuilder[] ALLOC_POOL = new ByteBufferBuilder[0];

    /** Per-thread CubicMatrixRenderer pool, keyed by modelId. Phase A
     *  runs on main thread (single-threaded ThreadLocal context); each
     *  unique model.id gets its own extractor — the matrices ArrayList
     *  is overwritten in-place by CubicRenderer.processRenderModel each
     *  call. */
    private static final ThreadLocal<java.util.HashMap<String, CubicMatrixRenderer>> EXTRACTOR_TL
        = ThreadLocal.withInitial(java.util.HashMap::new);

    /** Estimated bytes per vertex Iris-extended ENTITY format = 54B.
     *  We add ~50% margin so BufferAllocator avoids growth (grow = native
     *  realloc, slower than oversizing). */
    private static final int IRIS_EXT_BYTES_PER_VERT = 96;

    private Tier2dParallelEmit() {}

    public static void flushAll()
    {
        Map<String, List<FastPathBatch.Submission>> batch = FastPathBatch.snapshotBatch();
        if (batch.isEmpty()) return;

        long flushStartNs = System.nanoTime();
        BBSPerfConfig.tier2dFlushes++;

        int bucketsProcessed = 0;
        int bucketsFallback = 0;
        int submissionsDrawn = 0;
        int totalDraws = 0;
        int failures = 0;

        /* Hoist render state. lightmap/overlay set ONCE for the
         * entire fast-path flush. */
        /* 26.2: Blend ist kein umschaltbarer Zustand mehr. enableBlend +
         * defaultBlendFunc (SRC_ALPHA/ONE_MINUS_SRC_ALPHA) sind wortgleich das, was
         * BlendFunction.TRANSLUCENT in einer Pipeline traegt — und die Pipeline zu dem
         * Shader, den Phase D setzt (getRendertypeEntityTranslucentCullShader), ist
         * mchorse.bbs_mod.compat.alt.RenderPipelinesAlt.ENTITY_TRANSLUCENT_CULL. Der Zustand ist nicht weg, er sitzt
         * jetzt am Zeichenaufruf.
         * ⚠️ Er wirkt erst, wenn Phase D vom drawWithShader-Weg auf einen RenderPass mit
         * dieser Pipeline umgestellt ist — bis dahin fehlt die Mischung. */
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
                    int[] drawOut = new int[1];
                    int written = drawBucket(list, ir, drawOut);
                    if (written > 0)
                    {
                        bucketsProcessed++;
                        submissionsDrawn += written;
                        totalDraws += drawOut[0];
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
                        LOG.warn("[tier2d] drawBucket threw for model={} — falling back. cause={}",
                            first.instance.id, t.toString(), t);
                    }
                    for (FastPathBatch.Submission s : list)
                    {
                        try { FastPathRenderer.renderOneFallback(s); }
                        catch (Throwable ig) {}
                    }
                }
            }
        }
        finally
        {
            /* 26.2: kein disableBlend — es gab nichts einzuschalten, also auch nichts
             * abzuschalten. Blend haengt an der Pipeline des Zeichenaufrufs. */
        }

        BBSPerfConfig.tier2dBucketsProcessed += bucketsProcessed;
        BBSPerfConfig.tier2dBucketsFallback += bucketsFallback;
        BBSPerfConfig.tier2dSubmissions += submissionsDrawn;
        BBSPerfConfig.tier2dTotalDraws += totalDraws;
        BBSPerfConfig.tier2dFailures += failures;

        if (!LOGGED_FIRST_FLUSH && bucketsProcessed > 0)
        {
            LOGGED_FIRST_FLUSH = true;
            long elapsedNs = System.nanoTime() - flushStartNs;
            int workers = ForkJoinPool.commonPool().getParallelism();
            LOG.info("[tier2d] FIRST parallel-emit flush — buckets={} subs={} draws={} workers={} ns={}",
                bucketsProcessed, submissionsDrawn, totalDraws, workers, elapsedNs);
        }
    }

    private static int drawBucket(List<FastPathBatch.Submission> list, SkinnedMeshIR ir, int[] outDrawCount)
    {
        int n = list.size();
        ensureExtractorPool(n);

        /* ------------------ PHASE A — animator + matrix extract ------------------
         * Serial by default. When ENABLE_TIER2_PHASE_A_PARALLEL is on,
         * fans extraction across ForkJoinPool.commonPool. Safety: each
         * submission owns its own ModelFormRenderer + ModelInstance +
         * StubEntity, so animator state writes are per-task. Falls back
         * to serial on any exception so visual correctness wins over
         * speed. */
        long phaseANs = System.nanoTime();
        if (BBSPerfConfig.ENABLE_TIER2_PHASE_A_PARALLEL && n > 1)
        {
            final List<FastPathBatch.Submission> fListA = list;
            final int fNA = n;
            try
            {
                IntStream.range(0, fNA).parallel().forEach(i ->
                {
                    EXTRACTOR_POOL[i] = extractMatricesFor(fListA.get(i));
                });
                BBSPerfConfig.tier2fPhaseATasks += n;
            }
            catch (Throwable t)
            {
                BBSPerfConfig.tier2fPhaseAFallbacks++;
                if (!LOGGED_FIRST_FAILURE)
                {
                    LOGGED_FIRST_FAILURE = true;
                    LOG.warn("[tier2d] Phase A parallel threw — falling back to serial. cause={}",
                        t.toString(), t);
                }
                for (int i = 0; i < n; i++)
                {
                    EXTRACTOR_POOL[i] = extractMatricesFor(list.get(i));
                }
            }
        }
        else
        {
            for (int i = 0; i < n; i++)
            {
                EXTRACTOR_POOL[i] = extractMatricesFor(list.get(i));
            }
        }
        BBSPerfConfig.tier2dPhaseANsAccum += System.nanoTime() - phaseANs;
        if (BBSPerfConfig.ENABLE_TIER2_PHASE_A_PARALLEL && n > 1)
        {
            BBSPerfConfig.tier2fPhaseAParallelNs += System.nanoTime() - phaseANs;
        }

        /* ------------------ PHASE B+C — parallel chunked emit ------------------ */
        long phaseBCNs = System.nanoTime();

        int poolSize = ForkJoinPool.commonPool().getParallelism();
        if (poolSize < 1) poolSize = 1;
        int nWorkers = Math.min(n, poolSize);
        int chunkSize = (n + nWorkers - 1) / nWorkers;

        ensureWorkerResults(nWorkers);
        for (int w = 0; w < nWorkers; w++) WORKER_RESULTS[w] = null;

        /* Capture finals for lambda closure. */
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
                BufferBuilder builder;
                boolean pooledAlloc = false;
                try
                {
                    if (BBSPerfConfig.ENABLE_TIER3_POOLS)
                    {
                        /* Per-SLOT (w), NOT per-thread — see ALLOC_POOL doc. Only task w
                         * touches ALLOC_POOL[w], and slot w's buffer from the previous pass
                         * was already drawn in Phase D, so reset() here can never wipe a
                         * still-undrawn buffer. */
                        ByteBufferBuilder pooled = ALLOC_POOL[w];
                        if (pooled == null)
                        {
                            pooled = new ByteBufferBuilder(initialCapacity);
                            ALLOC_POOL[w] = pooled;
                            BBSPerfConfig.tier3AllocFreshAllocs++;
                        }
                        else
                        {
                            pooled.discard();
                            BBSPerfConfig.tier3AllocReuses++;
                        }
                        alloc = pooled;
                        pooledAlloc = true;
                    }
                    else
                    {
                        alloc = new ByteBufferBuilder(initialCapacity);
                    }
                    builder = new BufferBuilder(alloc,
                        com.mojang.blaze3d.vertex.VertexFormat.Mode.TRIANGLES,
                        DefaultVertexFormat.NEW_ENTITY);
                }
                catch (Throwable t)
                {
                    if (!LOGGED_FIRST_FAILURE)
                    {
                        LOGGED_FIRST_FAILURE = true;
                        LOG.warn("[tier2d] BufferBuilder construct threw — cause={}", t.toString(), t);
                    }
                    return;
                }

                /* Capture pooledAlloc for cleanup decision later. Stored
                 * inline in WorkerResult to keep the parallel-task
                 * boundary clean. */
                final boolean fPooledAlloc = pooledAlloc;

                int written = 0;
                try
                {
                    for (int i = start; i < end; i++)
                    {
                        CubicMatrixRenderer ex = EXTRACTOR_POOL[i];
                        if (ex == null) continue;
                        emitInstance(builder, fList.get(i), fIR, ex);
                        written++;
                    }
                }
                catch (Throwable t)
                {
                    if (!LOGGED_FIRST_FAILURE)
                    {
                        LOGGED_FIRST_FAILURE = true;
                        LOG.warn("[tier2d] worker emit threw — cause={}", t.toString(), t);
                    }
                }

                if (written > 0)
                {
                    MeshData built;
                    try
                    {
                        built = builder.buildOrThrow();
                    }
                    catch (Throwable t)
                    {
                        if (!fPooledAlloc)
                        {
                            try { alloc.close(); } catch (Throwable ig) {}
                        }
                        if (!LOGGED_FIRST_FAILURE)
                        {
                            LOGGED_FIRST_FAILURE = true;
                            LOG.warn("[tier2d] builder.end threw — cause={}", t.toString(), t);
                        }
                        return;
                    }
                    WORKER_RESULTS[w] = new WorkerResult(alloc, built, written, fPooledAlloc);
                    BBSPerfConfig.tier2dParallelTasks++;
                }
                else
                {
                    /* Empty buffer — release allocator immediately unless pooled. */
                    try { builder.buildOrThrow().close(); } catch (Throwable ig) {}
                    if (!fPooledAlloc)
                    {
                        try { alloc.close(); } catch (Throwable ig) {}
                    }
                }
            });
        }
        catch (Throwable t)
        {
            /* Parallel stream threw — clean up all worker results. */
            for (int w = 0; w < nWorkers; w++)
            {
                WorkerResult r = WORKER_RESULTS[w];
                if (r != null)
                {
                    try { r.built.close(); } catch (Throwable ig) {}
                    if (!r.pooled)
                    {
                        try { r.alloc.close(); } catch (Throwable ig) {}
                    }
                    WORKER_RESULTS[w] = null;
                }
            }
            throw t;
        }
        BBSPerfConfig.tier2dPhaseBCNsAccum += System.nanoTime() - phaseBCNs;

        /* ------------------ PHASE D — serial back-to-back drawArrays ------------------ */
        long phaseDNs = System.nanoTime();

        /* Bind texture + shader ONCE for all N draws (same model.id =
         * same texture by router's bucket assumption). */
        Link texture = list.get(0).form.texture.get();
        if (texture == null) texture = list.get(0).instance.getTexture();
        if (texture != null)
        {
            try { BBSModClient.getTextures().bindTexture(texture); }
            catch (Throwable ig) {}
        }
        /* 26.2: setShader ist entfallen -- der Shader steht in der Pipeline, die der
         * Zeichenaufruf waehlt. Frueher galt er fuer alles Folgende. */

        int drawCount = 0;
        int totalWritten = 0;
        for (int w = 0; w < nWorkers; w++)
        {
            WorkerResult r = WORKER_RESULTS[w];
            if (r == null) continue;
            try
            {
                /* Der Shader stand frueher in einem setShader davor: ENTITY_TRANSLUCENT_CULL.
                 * Jetzt waehlt ihn der Zeichenaufruf, und mit ihm Blend, Tiefe und Cull. */
                SofortZeichner.zeichnen(mchorse.bbs_mod.compat.alt.RenderPipelinesAlt.ENTITY_TRANSLUCENT_CULL, r.built,
                    new org.joml.Matrix4f(RenderSystem.getModelViewMatrix()), null, null, null);
                drawCount++;
                totalWritten += r.writtenCount;
            }
            catch (Throwable t)
            {
                if (!LOGGED_FIRST_FAILURE)
                {
                    LOGGED_FIRST_FAILURE = true;
                    LOG.warn("[tier2d] drawWithGlobalProgram threw — cause={}", t.toString(), t);
                }
            }
            try { r.built.close(); } catch (Throwable ig) {}
            if (!r.pooled)
            {
                try { r.alloc.close(); } catch (Throwable ig) {}
            }
            WORKER_RESULTS[w] = null;
        }
        BBSPerfConfig.tier2dPhaseDNsAccum += System.nanoTime() - phaseDNs;

        /* Clear extractor slots for next bucket. */
        for (int i = 0; i < n; i++) EXTRACTOR_POOL[i] = null;

        outDrawCount[0] = drawCount;
        return totalWritten;
    }

    /**
     * PHASE A helper — animator chain + matrix extract.
     */
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

        CubicMatrixRenderer ex;
        if (BBSPerfConfig.ENABLE_TIER3_POOLS)
        {
            String modelId = instance.id;
            java.util.HashMap<String, CubicMatrixRenderer> cache = EXTRACTOR_TL.get();
            ex = modelId == null ? null : cache.get(modelId);
            if (ex == null)
            {
                ex = new CubicMatrixRenderer(cubic);
                if (modelId != null) cache.put(modelId, ex);
                BBSPerfConfig.tier3ExtractorFresh++;
            }
            else
            {
                BBSPerfConfig.tier3ExtractorReuses++;
            }
        }
        else
        {
            ex = new CubicMatrixRenderer(cubic);
        }
        CubicRenderer.processRenderModel(ex, null, stack, cubic);
        return ex;
    }

    /**
     * PHASE B+C helper — skin + emit ONE instance into the per-worker
     * BufferBuilder. Called from parallel worker threads. Local Vector4f
     * / Vector3f stay on each worker's call stack — race-free.
     */
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
        /* Grow the per-slot allocator pool PRESERVING existing entries (they hold
         * reusable native buffers — must not be dropped/leaked on resize). */
        if (ALLOC_POOL.length < needed)
        {
            ALLOC_POOL = java.util.Arrays.copyOf(ALLOC_POOL, Math.max(needed, ALLOC_POOL.length * 2));
        }
    }

    private static final class WorkerResult
    {
        final ByteBufferBuilder alloc;
        final MeshData built;
        final int writtenCount;
        final boolean pooled;

        WorkerResult(ByteBufferBuilder alloc, MeshData built, int writtenCount, boolean pooled)
        {
            this.alloc = alloc;
            this.built = built;
            this.writtenCount = writtenCount;
            this.pooled = pooled;
        }
    }

    public static String snapshot()
    {
        long flushes = BBSPerfConfig.tier2dFlushes;
        double avgPhaseAMs = flushes == 0 ? 0 : (BBSPerfConfig.tier2dPhaseANsAccum / (double) flushes) / 1_000_000.0;
        double avgPhaseBCMs = flushes == 0 ? 0 : (BBSPerfConfig.tier2dPhaseBCNsAccum / (double) flushes) / 1_000_000.0;
        double avgPhaseDMs = flushes == 0 ? 0 : (BBSPerfConfig.tier2dPhaseDNsAccum / (double) flushes) / 1_000_000.0;
        double avgDrawsPerFlush = flushes == 0 ? 0 : BBSPerfConfig.tier2dTotalDraws / (double) flushes;
        return String.format(
            "flushes=%d buckets=%d fallback=%d subs=%d parTasks=%d draws=%d (avg %.1f/flush) phaseA=%.2fms phaseBC=%.2fms phaseD=%.2fms",
            flushes,
            BBSPerfConfig.tier2dBucketsProcessed,
            BBSPerfConfig.tier2dBucketsFallback,
            BBSPerfConfig.tier2dSubmissions,
            BBSPerfConfig.tier2dParallelTasks,
            BBSPerfConfig.tier2dTotalDraws,
            avgDrawsPerFlush,
            avgPhaseAMs, avgPhaseBCMs, avgPhaseDMs);
    }

    public static void resetCounters()
    {
        LOGGED_FIRST_FLUSH = false;
        LOGGED_FIRST_FAILURE = false;
        BBSPerfConfig.tier2dFlushes = 0L;
        BBSPerfConfig.tier2dBucketsProcessed = 0L;
        BBSPerfConfig.tier2dBucketsFallback = 0L;
        BBSPerfConfig.tier2dSubmissions = 0L;
        BBSPerfConfig.tier2dParallelTasks = 0L;
        BBSPerfConfig.tier2dTotalDraws = 0L;
        BBSPerfConfig.tier2dPhaseANsAccum = 0L;
        BBSPerfConfig.tier2dPhaseBCNsAccum = 0L;
        BBSPerfConfig.tier2dPhaseDNsAccum = 0L;
        BBSPerfConfig.tier2dFailures = 0L;
    }

}
