// Coded by Afrobama/Claude — Tier 2-e v2 persistent-allocator merged draw.
//
// v1 regression cause (200@50fps vs Tier 2-d's 70fps): per-frame
// `new BufferAllocator(totalBytes)` allocated and freed up to ~26 MB
// of native memory EVERY frame just for the merge. The single-
// drawArrays win was completely eaten by native malloc/free + GC.
//
// v2 fix — persistence on the merge side:
//   * MASTER_ALLOC = ONE BufferAllocator created lazily on first
//                    flush, reused across all subsequent frames.
//                    CloseableBuffer.close() (fired by
//                    BufferRenderer.drawWithGlobalProgram when it
//                    finishes the upload) pops MASTER_ALLOC's cursor
//                    back to 0, so the next frame's allocate(N) lands
//                    in the same native block. No malloc/free.
//
// Per-worker BufferAllocators are also pooled via Tier 3 (ALLOC_TL) —
// only the master-merge alloc was the leak in v1.
//
// Draw path stays on `BufferRenderer.drawWithGlobalProgram` (same as
// v1 + every other BBSTools renderer). MC internally caches a
// VertexBuffer per shader/format so the GL VBO id is stable across
// frames; we don't need to drive raw VertexBuffer ourselves.
//
// Iris-compat: identical to v1 — per-worker BufferBuilder triggers
// Iris's MixinBufferBuilder extension; the merged ByteBuffer carries
// the same extended VertexFormat (refParams.format()) into
// drawWithGlobalProgram.
//
// Tier 2-e v2 sits at the top of the dispatch ladder when
// ENABLE_TIER2_MERGE_V2 is on; everything else stays untouched.
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.IntStream;

public final class Tier2eMergedDrawV2
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/fast-path/tier2e-v2");

    private static volatile boolean LOGGED_FIRST_FLUSH   = false;
    private static volatile boolean LOGGED_FIRST_FAILURE = false;

    /* Persistent state — initialised exactly once on first flush,
     * reused frame-over-frame. */
    private static ByteBufferBuilder MASTER_ALLOC = null;
    private static int             MASTER_ALLOC_INITIAL_CAP = 0;

    /** Per-bucket scratch pool — main thread, single-threaded use. */
    private static CubicMatrixRenderer[] EXTRACTOR_POOL = new CubicMatrixRenderer[0];
    private static WorkerResult[]        WORKER_RESULTS = new WorkerResult[0];

    /** Iris-extended ENTITY vertex format estimate — 2× pad. */
    private static final int IRIS_EXT_BYTES_PER_VERT = 96;

    /* ---------------------------------------------------------------
     * Tier 3 per-thread pools shared with Tier 2-d. ALLOC_TL is the
     * per-worker BufferAllocator; EXTRACTOR_TL is the per-thread
     * CubicMatrixRenderer cache (extractor reuse keyed by model.id).
     * --------------------------------------------------------------- */
    private static final ThreadLocal<ByteBufferBuilder> ALLOC_TL = new ThreadLocal<>();
    private static final ThreadLocal<HashMap<String, CubicMatrixRenderer>> EXTRACTOR_TL =
        ThreadLocal.withInitial(HashMap::new);

    private Tier2eMergedDrawV2() {}

    public static void flushAll()
    {
        Map<String, List<FastPathBatch.Submission>> batch = FastPathBatch.snapshotBatch();
        if (batch.isEmpty()) return;

        long flushStartNs = System.nanoTime();
        BBSPerfConfig.tier2eV2Flushes++;

        int bucketsProcessed = 0;
        int bucketsFallback  = 0;
        int submissionsDrawn = 0;
        int mergedDraws      = 0;
        int failures         = 0;

        /* 26.2: enableBlend() + defaultBlendFunc() sind ersatzlos entfallen. Mischung ist kein
         * Schalter mehr, sie steht in der Pipeline des Draws. Der zusammengefasste Draw unten
         * zeichnet NEW_ENTITY-Dreiecke mit dem Entity-Translucent-Cull-Programm; die zugehoerige
         * Pipeline RenderPipelines.ENTITY_TRANSLUCENT_CULL traegt BlendFunction.TRANSLUCENT - genau
         * das, was defaultBlendFunc hier einstellte. */
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
                        LOG.warn("[tier2e-v2] drawBucket threw for model={} — falling back. cause={}",
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

            /* 26.2: disableBlend() ist ersatzlos entfallen - es gibt keinen globalen Blend-Schalter
             * mehr, den dieser Flush zuruecknehmen muesste. Was nach ihm zeichnet, bekommt seine
             * Mischung aus der eigenen Pipeline. */
        }

        BBSPerfConfig.tier2eV2BucketsProcessed += bucketsProcessed;
        BBSPerfConfig.tier2eV2BucketsFallback  += bucketsFallback;
        BBSPerfConfig.tier2eV2Submissions      += submissionsDrawn;
        BBSPerfConfig.tier2eV2MergedDraws      += mergedDraws;
        BBSPerfConfig.tier2eV2Failures         += failures;

        if (!LOGGED_FIRST_FLUSH && bucketsProcessed > 0)
        {
            LOGGED_FIRST_FLUSH = true;
            long elapsedNs = System.nanoTime() - flushStartNs;
            int workers = ForkJoinPool.commonPool().getParallelism();
            LOG.info("[tier2e-v2] FIRST persistent-allocator flush — buckets={} subs={} mergedDraws={} workers={} masterAllocInitial={} ns={}",
                bucketsProcessed, submissionsDrawn, mergedDraws, workers, MASTER_ALLOC_INITIAL_CAP, elapsedNs);
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
        BBSPerfConfig.tier2eV2PhaseANsAccum += System.nanoTime() - phaseANs;

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
                boolean pooledAlloc = false;
                if (BBSPerfConfig.ENABLE_TIER3_POOLS)
                {
                    ByteBufferBuilder pooled = ALLOC_TL.get();
                    if (pooled == null)
                    {
                        pooled = new ByteBufferBuilder(initialCapacity);
                        ALLOC_TL.set(pooled);
                        BBSPerfConfig.tier3AllocFreshAllocs++;
                    }
                    else
                    {
                        BBSPerfConfig.tier3AllocReuses++;
                    }
                    alloc = pooled;
                    pooledAlloc = true;
                }
                else
                {
                    alloc = new ByteBufferBuilder(initialCapacity);
                }

                BufferBuilder b;
                try
                {
                    b = new BufferBuilder(alloc,
                        com.mojang.blaze3d.PrimitiveTopology.TRIANGLES,
                        DefaultVertexFormat.ENTITY);
                }
                catch (Throwable t)
                {
                    if (!pooledAlloc) { try { alloc.close(); } catch (Throwable ig) {} }
                    if (!LOGGED_FIRST_FAILURE)
                    {
                        LOGGED_FIRST_FAILURE = true;
                        LOG.warn("[tier2e-v2] BufferBuilder construct threw — cause={}", t.toString(), t);
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
                        LOG.warn("[tier2e-v2] worker emit threw — cause={}", t.toString(), t);
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
                        if (!pooledAlloc) { try { alloc.close(); } catch (Throwable ig) {} }
                        return;
                    }
                    WORKER_RESULTS[w] = new WorkerResult(alloc, built, written, pooledAlloc);
                }
                else
                {
                    try { b.buildOrThrow().close(); } catch (Throwable ig) {}
                    if (!pooledAlloc) { try { alloc.close(); } catch (Throwable ig) {} }
                }
            });
        }
        catch (Throwable t)
        {
            cleanupWorkers(nWorkers);
            for (int i = 0; i < n; i++) EXTRACTOR_POOL[i] = null;
            throw t;
        }
        BBSPerfConfig.tier2eV2PhaseBCNsAccum += System.nanoTime() - phaseBCNs;

        /* ------------------ PHASE F — merge into persistent MASTER_ALLOC, single draw ------------------ */
        long phaseFNs = System.nanoTime();

        /* Sum bytes + verts across workers. */
        int totalBytes   = 0;
        int totalVerts   = 0;
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
            totalBytes   += remaining;
            totalVerts   += p.vertexCount();
            totalIndices += p.indexCount();
            totalWritten += r.writtenCount;
            if (refParams == null) refParams = p;
        }

        if (totalBytes == 0 || refParams == null)
        {
            cleanupWorkers(nWorkers);
            for (int i = 0; i < n; i++) EXTRACTOR_POOL[i] = null;
            BBSPerfConfig.tier2eV2PhaseFNsAccum += System.nanoTime() - phaseFNs;
            return 0;
        }

        /* Lazy-init the persistent master BufferAllocator. Sized to 2×
         * the first-frame total so future frames are unlikely to
         * trigger BufferAllocator's internal native realloc (which
         * would defeat the persistence win). */
        if (MASTER_ALLOC == null)
        {
            int initial = Math.max(totalBytes * 2, 1 << 20);  /* 1 MiB floor */
            MASTER_ALLOC = new ByteBufferBuilder(initial);
            MASTER_ALLOC_INITIAL_CAP = initial;
        }

        boolean drewOk = false;
        MeshData masterBuilt = null;
        try
        {
            /* allocate(totalBytes) advances the cursor from 0 (it was
             * reset by last frame's CloseableBuffer.close()), returns
             * native ptr. memByteBuffer wraps without copying. */
            long masterPtr = MASTER_ALLOC.reserve(totalBytes);
            ByteBuffer master = MemoryUtil.memByteBuffer(masterPtr, totalBytes);
            master.clear();

            /* Memcpy each worker's BuiltBuffer bytes into master. */
            for (int w = 0; w < nWorkers; w++)
            {
                WorkerResult r = WORKER_RESULTS[w];
                if (r == null) continue;
                ByteBuffer src = r.built.vertexBuffer();
                if (src.remaining() <= 0) continue;
                int srcPos = src.position();
                int srcLim = src.limit();
                master.put(src);
                src.position(srcPos).limit(srcLim);
            }
            master.flip();

            /* Wrap the slot in a CloseableBuffer + synthetic BuiltBuffer
             * with the summed vertex count. Closing the BuiltBuffer
             * (done by BufferRenderer.drawWithGlobalProgram below) pops
             * MASTER_ALLOC's cursor back to 0 so next frame starts
             * fresh — no per-frame native malloc/free. */
            ByteBufferBuilder.Result cb = MASTER_ALLOC.build();
            /* 26.2: DrawState.mode() heisst jetzt primitiveTopology() und liefert
             * PrimitiveTopology statt VertexFormat.Mode -- reine Umbenennung, gleiche Stelle
             * im Datensatz. */
            MeshData.DrawState mergedParams = new MeshData.DrawState(
                refParams.format(),
                totalVerts,
                totalIndices,
                refParams.primitiveTopology(),
                refParams.indexType());
            masterBuilt = new MeshData(cb, mergedParams);

            /* Texture + shader (shared across bucket). */
            Link texture = list.get(0).form.texture.get();
            if (texture == null) texture = list.get(0).instance.getTexture();
            if (texture != null)
            {
                try { BBSModClient.getTextures().bindTexture(texture); } catch (Throwable ig) {}
            }
            /* 26.2: setShader ist entfallen -- der Shader steht in der Pipeline, die der
             * Zeichenaufruf waehlt. Frueher galt er fuer alles Folgende. */

            /* Single drawArrays via MC's BufferRenderer. Internally MC
             * caches a VertexBuffer per shader/format so the GL VBO id
             * is stable across frames. drawWithGlobalProgram closes
             * masterBuilt → closes cb → MASTER_ALLOC cursor pops to 0
             * for next frame. */
            /* Der Shader stand frueher in einem setShader davor: ENTITY_TRANSLUCENT_CULL.
             * Jetzt waehlt ihn der Zeichenaufruf, und mit ihm Blend, Tiefe und Cull. */
            SofortZeichner.zeichnen(net.minecraft.client.renderer.RenderPipelines.ENTITY_TRANSLUCENT_CULL, masterBuilt,
                com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrixCopy(), null, null, null);
            masterBuilt = null;  /* drawWithGlobalProgram closes it */

            drewOk = true;
            BBSPerfConfig.tier2eV2MergedBytes += totalBytes;
        }
        catch (Throwable t)
        {
            if (!LOGGED_FIRST_FAILURE)
            {
                LOGGED_FIRST_FAILURE = true;
                LOG.warn("[tier2e-v2] merge/draw threw — cause={}", t.toString(), t);
            }
        }
        finally
        {
            if (masterBuilt != null)
            {
                try { masterBuilt.close(); } catch (Throwable ig) {}
            }
            cleanupWorkers(nWorkers);
            for (int i = 0; i < n; i++) EXTRACTOR_POOL[i] = null;
        }
        BBSPerfConfig.tier2eV2PhaseFNsAccum += System.nanoTime() - phaseFNs;

        return drewOk ? totalWritten : 0;
    }

    private static void cleanupWorkers(int nWorkers)
    {
        for (int w = 0; w < nWorkers; w++)
        {
            WorkerResult r = WORKER_RESULTS[w];
            if (r == null) continue;
            try { r.built.close(); } catch (Throwable ig) {}
            if (!r.pooledAlloc)
            {
                try { r.alloc.close(); } catch (Throwable ig) {}
            }
            /* Pooled allocator stays live in ALLOC_TL — close on the
             * BuiltBuffer popped its cursor back already. */
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

        CubicMatrixRenderer ex;
        if (BBSPerfConfig.ENABLE_TIER3_POOLS)
        {
            String modelId = instance.id;
            HashMap<String, CubicMatrixRenderer> map = EXTRACTOR_TL.get();
            ex = modelId == null ? null : map.get(modelId);
            if (ex == null)
            {
                ex = new CubicMatrixRenderer(cubic);
                if (modelId != null) map.put(modelId, ex);
                BBSPerfConfig.tier3ExtractorFresh++;
            }
            else
            {
                /* NOTE: do NOT call ex.matrices.clear() here —
                 * CubicRenderer.processRenderModel overwrites the
                 * matrices list in-place via set-by-index, so an
                 * emptied list throws IndexOutOfBoundsException on
                 * the first set(0,…). Matches Tier 2-d's pattern. */
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
        final boolean pooledAlloc;

        WorkerResult(ByteBufferBuilder alloc, MeshData built, int writtenCount, boolean pooledAlloc)
        {
            this.alloc = alloc;
            this.built = built;
            this.writtenCount = writtenCount;
            this.pooledAlloc = pooledAlloc;
        }
    }

    public static String snapshot()
    {
        long flushes = BBSPerfConfig.tier2eV2Flushes;
        double avgPhaseAMs  = flushes == 0 ? 0 : (BBSPerfConfig.tier2eV2PhaseANsAccum  / (double) flushes) / 1_000_000.0;
        double avgPhaseBCMs = flushes == 0 ? 0 : (BBSPerfConfig.tier2eV2PhaseBCNsAccum / (double) flushes) / 1_000_000.0;
        double avgPhaseFMs  = flushes == 0 ? 0 : (BBSPerfConfig.tier2eV2PhaseFNsAccum  / (double) flushes) / 1_000_000.0;
        long avgMergedKB = flushes == 0 ? 0 : (BBSPerfConfig.tier2eV2MergedBytes / flushes) / 1024;
        return String.format(
            "flushes=%d buckets=%d fallback=%d subs=%d mergedDraws=%d phaseA=%.2fms phaseBC=%.2fms phaseF=%.2fms avgMergedKB=%d masterAllocCap=%d",
            flushes,
            BBSPerfConfig.tier2eV2BucketsProcessed,
            BBSPerfConfig.tier2eV2BucketsFallback,
            BBSPerfConfig.tier2eV2Submissions,
            BBSPerfConfig.tier2eV2MergedDraws,
            avgPhaseAMs, avgPhaseBCMs, avgPhaseFMs, avgMergedKB,
            MASTER_ALLOC_INITIAL_CAP);
    }

    public static void resetCounters()
    {
        LOGGED_FIRST_FLUSH = false;
        LOGGED_FIRST_FAILURE = false;
        BBSPerfConfig.tier2eV2Flushes = 0L;
        BBSPerfConfig.tier2eV2BucketsProcessed = 0L;
        BBSPerfConfig.tier2eV2BucketsFallback = 0L;
        BBSPerfConfig.tier2eV2Submissions = 0L;
        BBSPerfConfig.tier2eV2MergedDraws = 0L;
        BBSPerfConfig.tier2eV2PhaseANsAccum = 0L;
        BBSPerfConfig.tier2eV2PhaseBCNsAccum = 0L;
        BBSPerfConfig.tier2eV2PhaseFNsAccum = 0L;
        BBSPerfConfig.tier2eV2MergedBytes = 0L;
        BBSPerfConfig.tier2eV2Failures = 0L;
    }

}
