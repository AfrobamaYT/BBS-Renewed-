// Coded by Afrobama/Claude — Tier 1c VeilCubicBatch full body.
//
// Sister dispatcher to VeilReplayBatchRenderer (which handles BOBJ-format
// skinned replays). This one handles CUBIC-format BBS replays: per-group
// MatrixStack, NO per-vertex skinning weights. The non-BOBJ gate inside
// VeilReplayBatchRenderer's mixin rejects these replays and they fall
// back to vanilla MC rendering at ~30 FPS for 200 replays.
//
// What the cubic batch does that the skinned one doesn't:
//   - Per-instance encoding shrinks from 96 B (mat4 + boneBase + color)
//     to 16 B (ssboBase + packedLight + argbColor + pad).
//   - The bones SSBO at 8 KiB / instance becomes a flat mat4 BANK at
//     64 B / instance (one model-matrix per instance — cubic models
//     don't need 128 bones because vertices are rigid to their owning
//     group). 128× memory bandwidth reduction on the hot upload path.
//   - One glDrawElementsInstanced per (CubicModel, Texture) group.
//
// Public lifecycle:
//   submit(cubicModel, texture, modelTransform, replay, light, color)
//       → bucket into a (model, texture) group.
//   flush(viewMatrix, projMatrix)
//       → for each group: upload SSBO+instance VBO, draw instanced.
//   invalidate()  → drop cached batches (model unload).
//
// Built-in debugger: /bbsdebug veil-cubic via IFeatureDebugger.
//
// Constraints honored:
//   - BBSPerfConfig.ENABLE_VEIL_CUBIC_BATCH toggle (default false).
//   - Iris-shadow-pass early-return + ShadowSkipStats bump.
//   - One-time LOG.info on first dispatch, suppressed after.
//   - Silent catch blocks log via SLF4J.
package com.afrobama.bbstools.client.bbs.veil;

import com.afrobama.bbstools.client.crowd.mass.BBSCrowdAcceleratedRenderer;
import com.afrobama.bbstools.debug.IFeatureDebugger;
import com.afrobama.bbstools.perf.BBSPerfConfig;
import com.afrobama.bbstools.perf.BBSPerfHud;
import com.afrobama.bbstools.perf.PerfStage;
import com.afrobama.bbstools.perf.ShadowSkipStats;

import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public final class VeilCubicBatchRenderer implements IFeatureDebugger
{
    public static final VeilCubicBatchRenderer INSTANCE = new VeilCubicBatchRenderer();
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/veil-cubic");

    /** Master kill switch. Mirrors BBSPerfConfig.ENABLE_VEIL_CUBIC_BATCH.
     *  Kept as a public volatile for fast read-path branching, also
     *  exposed to /bbsdebug veil-cubic toggle. */
    public static volatile boolean ENABLED = false;

    public final AtomicLong replaysSubmitted = new AtomicLong();
    public final AtomicLong framesBatched = new AtomicLong();
    public final AtomicLong framesFallback = new AtomicLong();
    public final AtomicLong cachedBatchCount = new AtomicLong();
    public final AtomicLong drawsDispatched = new AtomicLong();

    private final Map<GroupKey, List<CubicBatchEntry>> groups = new HashMap<>();
    private final Map<GroupKey, VeilCubicBatch> batchCache = new HashMap<>();

    /** One-time-log gate so the first successful dispatch logs and
     *  subsequent ones stay silent. */
    private volatile boolean firstDispatchLogged;

    private VeilCubicBatchRenderer() {}

    /** Submit one cubic replay into the per-frame buckets. Called from
     *  the cubic branch of BaseFilmControllerVeilMixin (when authored)
     *  or from any future site that wants to push a cubic-form instance
     *  through this path.
     *
     *  @return true if the entry was accepted (caller must NOT also fall
     *          back to BBS render for this replay); false if the path is
     *          disabled/unavailable and BBS should run normally. */
    public boolean submit(Object cubicModel, Object texture,
        Matrix4f modelTransform, Object replayHandle,
        int packedLight, int color)
    {
        if (!ENABLED || !BBSPerfConfig.ENABLE_VEIL_CUBIC_BATCH) return false;
        if (cubicModel == null) return false;
        BBSPerfHud.begin(PerfStage.VEIL_CUBIC_BAKE);
        try
        {
            GroupKey key = new GroupKey(cubicModel, texture);
            groups.computeIfAbsent(key, k -> new ArrayList<>(64))
                  .add(new CubicBatchEntry(modelTransform, replayHandle, packedLight, color));
            replaysSubmitted.incrementAndGet();
            BBSPerfConfig.veilBatchReplaysSubmitted++;
            return true;
        }
        catch (Throwable t)
        {
            LOG.warn("VeilCubicBatchRenderer.submit threw: {}", t.toString());
            return false;
        }
        finally
        {
            BBSPerfHud.end(PerfStage.VEIL_CUBIC_BAKE);
        }
    }

    /** Flush all batched groups to GPU. Called at frame end after every
     *  cubic replay has been submitted. */
    public void flush(Matrix4f viewMatrix, Matrix4f projMatrix)
    {
        if (groups.isEmpty()) return;

        /* Iris shadow-pass: replays cast shadows from the gbuffer depth,
         * no need to re-render in shadow phase. Same gate the skinned
         * renderer uses. */
        if (BBSCrowdAcceleratedRenderer.isIrisShadowPass())
        {
            ShadowSkipStats.INSTANCE.bump("veil-cubic");
            groups.clear();
            return;
        }

        BBSPerfHud.begin(PerfStage.VEIL_CUBIC_SUBMIT);
        try
        {
            cachedBatchCount.set(groups.size());
            int dispatchedThisFrame = 0;

            for (Map.Entry<GroupKey, List<CubicBatchEntry>> e : groups.entrySet())
            {
                GroupKey key = e.getKey();
                List<CubicBatchEntry> entries = e.getValue();
                if (entries.isEmpty()) continue;

                VeilCubicBatch batch;
                try
                {
                    batch = batchCache.computeIfAbsent(key, VeilCubicBatch::build);
                }
                catch (Throwable t)
                {
                    LOG.warn("VeilCubicBatch.build threw for {} — group falls back", key, t);
                    framesFallback.incrementAndGet();
                    BBSPerfConfig.veilBatchFramesFallback++;
                    continue;
                }

                try
                {
                    batch.uploadInstanceData(entries);
                    batch.draw(viewMatrix, projMatrix);
                    if (batch.lastInstanceCount() > 0)
                    {
                        dispatchedThisFrame += batch.lastInstanceCount();
                        drawsDispatched.incrementAndGet();
                    }
                }
                catch (Throwable t)
                {
                    LOG.warn("VeilCubicBatch dispatch threw for {} — falling back this frame", key, t);
                    framesFallback.incrementAndGet();
                    BBSPerfConfig.veilBatchFramesFallback++;
                }
            }

            if (dispatchedThisFrame > 0)
            {
                framesBatched.incrementAndGet();
                BBSPerfConfig.veilBatchFramesBatched++;
                if (!firstDispatchLogged)
                {
                    firstDispatchLogged = true;
                    LOG.info("VeilCubicBatch first dispatch: {} instances across {} groups (further dispatches will be silent)",
                        dispatchedThisFrame, groups.size());
                }
            }
        }
        finally
        {
            groups.clear();
            BBSPerfHud.end(PerfStage.VEIL_CUBIC_SUBMIT);
        }
    }

    /** Drop GPU resources for a model that's been unloaded. Symmetric to
     *  VeilReplayBatchRenderer.invalidate(Object). */
    public void invalidate(Object cubicModel)
    {
        batchCache.entrySet().removeIf(e ->
        {
            if (!Objects.equals(e.getKey().cubicModel, cubicModel)) return false;
            try { e.getValue().close(); }
            catch (Throwable t) { LOG.warn("VeilCubicBatch.close threw during invalidate", t); }
            return true;
        });
    }

    /** Drop ALL cached batches (full restart of the path). */
    public void invalidate()
    {
        groups.clear();
        for (VeilCubicBatch b : batchCache.values())
        {
            try { b.close(); }
            catch (Throwable t) { LOG.warn("VeilCubicBatch.close threw during full invalidate", t); }
        }
        batchCache.clear();
    }

    @Override public String getName() { return "veil-cubic"; }
    @Override public boolean isEnabled() { return ENABLED && BBSPerfConfig.ENABLE_VEIL_CUBIC_BATCH; }
    @Override
    public void setEnabled(boolean v)
    {
        ENABLED = v;
        BBSPerfConfig.ENABLE_VEIL_CUBIC_BATCH = v;
    }
    @Override public int priority() { return 30; }

    @Override
    public void writeStatus(StringBuilder sb)
    {
        sb.append("ENABLED(local)=").append(ENABLED).append('\n');
        sb.append("ENABLE_VEIL_CUBIC_BATCH(config)=").append(BBSPerfConfig.ENABLE_VEIL_CUBIC_BATCH).append('\n');
        sb.append("currentGroups=").append(groups.size()).append('\n');
        sb.append("cachedBatches=").append(batchCache.size()).append('\n');
        sb.append("firstDispatchLogged=").append(firstDispatchLogged).append('\n');
        sb.append("NOTE: vertex extraction for cubic meshes still needs the\n");
        sb.append("      BBS-side accessor mixin (see VeilCubicBatch.extractCubicVertexCount).\n");
        sb.append("      Until it lands the build() returns un-built batches and draws no-op.\n");
        sb.append("      SSBO / instance VBO / VAO / RenderType wiring is real and active.\n");
    }

    @Override
    public void writeStats(StringBuilder sb)
    {
        sb.append("totalReplaysSubmitted=").append(replaysSubmitted.get()).append('\n');
        sb.append("framesBatched=").append(framesBatched.get()).append('\n');
        sb.append("framesFallback=").append(framesFallback.get()).append('\n');
        sb.append("drawsDispatched=").append(drawsDispatched.get()).append('\n');
        sb.append("lastFrameGroupCount=").append(cachedBatchCount.get()).append('\n');
    }

    /** Group key: (Cubic Model, Texture). */
    public static final class GroupKey
    {
        public final Object cubicModel;
        public final Object texture;

        public GroupKey(Object cubicModel, Object texture)
        {
            this.cubicModel = cubicModel;
            this.texture = texture;
        }

        @Override
        public boolean equals(Object o)
        {
            if (!(o instanceof GroupKey)) return false;
            GroupKey other = (GroupKey) o;
            return Objects.equals(this.cubicModel, other.cubicModel)
                && Objects.equals(this.texture, other.texture);
        }

        @Override
        public int hashCode() { return Objects.hash(cubicModel, texture); }
    }

    /** One queued replay-instance ready for batched draw.
     *  Public final fields (no record — keep field access compatible
     *  with VeilCubicBatch.uploadInstanceData which reads these
     *  directly). */
    public static final class CubicBatchEntry
    {
        public final Matrix4f modelTransform;
        public final Object replayHandle;
        public final int packedLight;
        public final int color;

        public CubicBatchEntry(Matrix4f modelTransform, Object replayHandle,
            int packedLight, int color)
        {
            this.modelTransform = modelTransform;
            this.replayHandle = replayHandle;
            this.packedLight = packedLight;
            this.color = color;
        }
    }

}
