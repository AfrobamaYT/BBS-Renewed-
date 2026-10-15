// Coded by Afrobama/Claude — Tier 1.5b fast-path per-frame batch.
//
// Accumulates routed replay submissions during BaseFilmController.render
// and flushes them at the end of the frame.
//
// Tier 1   = measure-only (counters + JFR).
// Tier 1.5a = cancel-only (vanilla render skipped, replays invisible).
// Tier 1.5b = render-replace via FastPathRenderer (this file's flush()
//             delegates to FastPathRenderer.flushAll() when render flag on).
//
// Thread-safety: this batch is touched from MC's main render thread only
// (BaseFilmController.render runs there). When Tier 2 introduces the
// partition-pool parallel animator, the submit() call site moves into
// worker threads — at that point FastPathBatch needs a thread-safe
// collector (per-worker buffer + flush-time merge). For Tier 1.5, plain
// ArrayList is fine.
package com.afrobama.bbstools.client.bbs.fastpath;

import com.afrobama.bbstools.perf.BBSPerfConfig;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;

import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class FastPathBatch
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/fast-path/batch");

    /* Per-model-id lists keep cache-friendly insertion order without
     * sorting at flush time. Tier 2 will tier this further via a
     * persistent-mapped SSBO ring buffer; Tier 1.5 uses transient
     * ArrayLists. */
    private static final Map<String, List<Submission>> BATCH = new HashMap<>();
    private static long flushCount = 0L;

    private FastPathBatch() {}

    /**
     * One captured replay-render call. Holds everything needed to re-run
     * the BBS vanilla render flow from FastPathRenderer.flushAll().
     *
     * Tier 1.5 captures the full state at HEAD time so flush can fire
     * after vanilla render3D returns from cancel — at that point the
     * BBS animator state on the renderer is still untouched (we
     * cancelled before it ran).
     */
    public static final class Submission
    {
        public final ModelFormRenderer renderer;
        public final IEntity entity;
        public final ModelInstance instance;
        public final ModelForm form;
        public final Matrix4f worldMatrix;
        public final int light;
        public final int overlay;
        public final int color;
        public final float transition;
        public final boolean renderEquipment;

        public Submission(ModelFormRenderer renderer, IEntity entity, ModelInstance instance,
                          ModelForm form, Matrix4f worldMatrix,
                          int light, int overlay, int color,
                          float transition, boolean renderEquipment)
        {
            this.renderer = renderer;
            this.entity = entity;
            this.instance = instance;
            this.form = form;
            this.worldMatrix = worldMatrix;
            this.light = light;
            this.overlay = overlay;
            this.color = color;
            this.transition = transition;
            this.renderEquipment = renderEquipment;
        }
    }

    public static void submit(Submission s)
    {
        if (s == null || s.instance == null) return;
        String modelId = s.instance.id == null ? "<null>" : s.instance.id;
        BATCH.computeIfAbsent(modelId, k -> new ArrayList<>()).add(s);
        BBSPerfConfig.fastPathSubmissions++;
    }

    public static int currentBatchSize()
    {
        int total = 0;
        for (List<Submission> list : BATCH.values()) total += list.size();
        return total;
    }

    public static Map<String, List<Submission>> snapshotBatch()
    {
        return BATCH;
    }

    /**
     * Called at @Inject(RETURN) of BaseFilmController.render. Emits the
     * per-frame metric event, delegates to FastPathRenderer when the
     * render flag is on, then clears the batch for the next frame.
     */
    public static void flush()
    {
        /* Rolling FPS sample — captured once per render frame (each
         * flush corresponds to a single render pass). */
        FpsSampler.recordCurrentFps();

        int totalSubmissions = currentBatchSize();
        int uniqueModels = BATCH.size();
        flushCount++;

        if (totalSubmissions > 0)
        {
            BBSPerfConfig.fastPathFlushes++;
            BBSPerfConfig.fastPathLastFrameSubmissions = totalSubmissions;
            BBSPerfConfig.fastPathLastFrameUniqueModels = uniqueModels;

            com.afrobama.bbstools.sonden.Meldesonde.HALTER.aktiv.schnellpfad(
                totalSubmissions, uniqueModels, flushCount);

            /* One log per session: prove the pipeline reached flush. */
            if (flushCount == 1L)
            {
                LOG.info("[fast-path/batch] FIRST FLUSH — submissions={} uniqueModels={}",
                    totalSubmissions, uniqueModels);
            }

            /* Tier 1.5b — when render flag is on, run our render
             * replacement. When false, submissions are silently
             * dropped (measure-only or cancel-only mode).
             *
             * Render dispatch ladder (lowest → highest, last-wins):
             *   1. FastPathRenderer.flushAll (Tier 1.5b direct path).
             *   2. Tier2Renderer.runScaffold (Tier 2-a IR build, runs
             *      alongside Tier 1.5b without displacing the draw).
             *   3. Tier2bDrawPath.flushAll (Tier 2-b merged-buffer draw,
             *      REPLACES FastPathRenderer.flushAll when on). */
            if (BBSPerfConfig.ENABLE_FAST_PATH_RENDER)
            {
                try
                {
                    if (BBSPerfConfig.ENABLE_TIER2_GPU)
                    {
                        Tier2Renderer.runScaffold();
                    }

                    if (BBSPerfConfig.ENABLE_TIER2_H
                        && BBSPerfConfig.ENABLE_TIER2_DRAW
                        && BBSPerfConfig.ENABLE_TIER2_GPU)
                    {
                        /* Tier 2-h (Option 2): capture the batch HERE
                         * (inside Iris's gbuffers pass) but defer the
                         * raw-GL instanced draw to AFTER_TRANSLUCENT so
                         * it runs outside Iris's geometry passes — raw GL
                         * inside Iris corrupts its state → particle crash. */
                        Tier2hInstancedDraw.captureForDeferred();
                    }
                    else if (BBSPerfConfig.ENABLE_TIER2_MERGE_V2
                        && BBSPerfConfig.ENABLE_TIER2_PARALLEL_EMIT
                        && BBSPerfConfig.ENABLE_TIER2_PARALLEL_SKIN
                        && BBSPerfConfig.ENABLE_TIER2_DRAW
                        && BBSPerfConfig.ENABLE_TIER2_GPU)
                    {
                        /* Tier 2-e v2: persistent BufferAllocator +
                         * persistent VertexBuffer. Replaces v1's
                         * per-frame native alloc. */
                        Tier2eMergedDrawV2.flushAll();
                    }
                    else if (BBSPerfConfig.ENABLE_TIER2_MERGE
                        && BBSPerfConfig.ENABLE_TIER2_PARALLEL_EMIT
                        && BBSPerfConfig.ENABLE_TIER2_PARALLEL_SKIN
                        && BBSPerfConfig.ENABLE_TIER2_DRAW
                        && BBSPerfConfig.ENABLE_TIER2_GPU)
                    {
                        /* Tier 2-e v1: parallel emit + merge into 1
                         * master BuiltBuffer + 1 drawArrays.
                         * REGRESSION default OFF. */
                        Tier2eMergedDraw.flushAll();
                    }
                    else if (BBSPerfConfig.ENABLE_TIER2_PARALLEL_EMIT
                        && BBSPerfConfig.ENABLE_TIER2_PARALLEL_SKIN
                        && BBSPerfConfig.ENABLE_TIER2_DRAW
                        && BBSPerfConfig.ENABLE_TIER2_GPU)
                    {
                        /* Tier 2-d: parallel BufferBuilder emit + N
                         * back-to-back drawArrays. Replaces Tier 2-c. */
                        Tier2dParallelEmit.flushAll();
                    }
                    else if (BBSPerfConfig.ENABLE_TIER2_PARALLEL_SKIN
                        && BBSPerfConfig.ENABLE_TIER2_DRAW
                        && BBSPerfConfig.ENABLE_TIER2_GPU)
                    {
                        /* Tier 2-c: 3-phase parallel-skin pipeline.
                         * Replaces both Tier 2-b and FastPathRenderer
                         * paths for buckets with cached IR (BOBJ falls
                         * back to renderOneFallback inside Tier2c). */
                        Tier2cParallelSkin.flushAll();
                    }
                    else if (BBSPerfConfig.ENABLE_TIER2_DRAW && BBSPerfConfig.ENABLE_TIER2_GPU)
                    {
                        /* Tier 2-b: merged-draw path. Skips
                         * FastPathRenderer.flushAll entirely so we don't
                         * double-render the same submissions. */
                        Tier2bDrawPath.flushAll();
                    }
                    else
                    {
                        FastPathRenderer.flushAll();
                    }
                }
                catch (Throwable t)
                {
                    LOG.warn("[fast-path/batch] flush threw: {}", t.toString(), t);
                }
            }
        }

        /* Clear for next frame. Tier 2 will recycle these lists instead
         * of throwing them at the GC. */
        BATCH.clear();
    }

    public static String snapshot()
    {
        return String.format("flushes=%d lastFrame{subs=%d models=%d} totalSubmissions=%d",
            BBSPerfConfig.fastPathFlushes,
            BBSPerfConfig.fastPathLastFrameSubmissions,
            BBSPerfConfig.fastPathLastFrameUniqueModels,
            BBSPerfConfig.fastPathSubmissions);
    }

    public static void resetCounters()
    {
        flushCount = 0L;
        BBSPerfConfig.fastPathFlushes = 0L;
        BBSPerfConfig.fastPathSubmissions = 0L;
        BBSPerfConfig.fastPathLastFrameSubmissions = 0;
        BBSPerfConfig.fastPathLastFrameUniqueModels = 0;
        BATCH.clear();
    }

}
