// Coded by Afrobama/Claude — Veil-instanced BBS Replay batch renderer.
// FOUNDATION SKELETON: file structure + public API surface in place,
// concrete GL/Veil calls deferred to Phase 2 of the migration plan
// (docs/CROWD_MASS_AND_BBS_REPLAY_PLAN.md, "Veil-Migration Phase 1").
//
// Concept:
//   - BBS today renders each replay individually (50-60 cap).
//   - We group submitted replays by (BOBJModel, Texture) and issue ONE
//     instanced draw per group via Veil's RenderType → Iris auto-patches.
//   - Per-instance data: model transform matrix + bone matrices for this
//     replay's pose this frame, packed into a persistent-mapped VBO +
//     SSBO of mat4[].
//
// Target: 1500-3000+ simultaneous replays when ≥80% share a base model.
//
// Status:
//   - submit() / begin() / flush() public surface defined.
//   - BatchEntry record holds everything a draw needs.
//   - flush() walks groups but does NOT issue draws yet (TODO).
//   - Activation needs BaseFilmControllerMixin to call submit/flush from
//     its per-replay render loop (still future work).
package com.afrobama.bbstools.client.bbs.veil;

import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class VeilReplayBatchRenderer
{
    public static final VeilReplayBatchRenderer INSTANCE = new VeilReplayBatchRenderer();

    /** Stable key per group: same Model + Texture identity ⇒ one instanced
     *  draw call. Animation state is per-instance, so it doesn't enter
     *  the key. Model + Texture are opaque object references — we just
     *  rely on Object.hashCode() / equals() for now. */
    public record GroupKey(Object model, Object texture) {}

    /** One replay's contribution to a batch. */
    public record BatchEntry(
        Matrix4f modelTransform,
        Matrix4f[] boneMatrices,
        int packedLight,
        int packedOverlay,
        int argbColor)
    {
    }

    private final Map<GroupKey, List<BatchEntry>> groups = new HashMap<>();
    private final Map<GroupKey, VeilSkinnedBatch> batchCache = new HashMap<>();

    private VeilReplayBatchRenderer() {}

    /** Called at the start of each film render frame. Clears submissions. */
    public void begin()
    {
        groups.clear();
    }

    /** Called per-replay from {@code BaseFilmControllerMixin} instead of
     *  letting BBS run its individual draw call. */
    public void submit(Object bobjModel, Object textureLink, BatchEntry entry)
    {
        groups.computeIfAbsent(new GroupKey(bobjModel, textureLink),
            k -> new ArrayList<>(64)).add(entry);
    }

    /** Issues one instanced draw per group. Each (Model,Texture) becomes
     *  one VeilRenderSystem.drawInstancedWithRenderType call. */
    public void flush(Matrix4f viewMatrix, Matrix4f projMatrix)
    {
        /* Tier 2f: skip the entire render path during Iris shadow passes.
         * Replays cast shadows from the gbuffer depth, no need to re-
         * render in shadow phase. */
        if (mchorse.bbs_mod.client.BBSRendering.isIrisShadowPass())
        {
            com.afrobama.bbstools.perf.ShadowSkipStats.INSTANCE.bump("veil-replay");
            return;
        }

        /* Tier 3c: sort groups by material to minimize state changes.
         * Skipped for tiny batches where the radix-sort overhead exceeds
         * the savings (4 passes × 64K buckets has fixed cost). */
        java.util.List<Map.Entry<GroupKey, List<BatchEntry>>> ordered;
        if (groups.size() >= 4)
        {
            int n = groups.size();
            long[] keys = new long[n];
            int[] indices = new int[n];
            java.util.ArrayList<Map.Entry<GroupKey, List<BatchEntry>>> entries =
                new java.util.ArrayList<>(groups.entrySet());
            for (int i = 0; i < n; i++)
            {
                GroupKey gk = entries.get(i).getKey();
                int texHash = gk.texture() == null ? 0 : (gk.texture().hashCode() & 0xFFFF);
                int modelHash = gk.model() == null ? 0 : (gk.model().hashCode() & 0xFFFF);
                /* Skinned-replay batches all share blend/depth state, so
                 * we put texture in the high bits, model in the mid, and
                 * the array index in the low bits as a stable tiebreaker. */
                keys[i] = com.afrobama.bbstools.perf.MaterialSorter.packKey(
                    texHash, 0, 0, modelHash, 0, i & 0xFFFF);
                indices[i] = i;
            }
            com.afrobama.bbstools.perf.MaterialSorter.radixSort(keys, indices, n);
            ordered = new java.util.ArrayList<>(n);
            for (int i = 0; i < n; i++) ordered.add(entries.get(indices[i]));
        }
        else
        {
            ordered = new java.util.ArrayList<>(groups.entrySet());
        }

        for (Map.Entry<GroupKey, List<BatchEntry>> e : ordered)
        {
            VeilSkinnedBatch batch = batchCache.computeIfAbsent(e.getKey(), VeilSkinnedBatch::build);
            batch.uploadInstanceDataAndCount(e.getValue());
            batch.draw(viewMatrix, projMatrix);
        }
    }

    /** Returns the number of unique (model, texture) groups submitted in
     *  the current frame. Used by VeilGroupAuditor (Tier 3h). */
    public int submittedGroupCount() { return groups.size(); }

    /** Returns the total number of submitted replays across all groups
     *  in the current frame. Used by VeilGroupAuditor (Tier 3h). */
    public int submittedReplayCount()
    {
        int n = 0;
        for (List<BatchEntry> g : groups.values()) n += g.size();
        return n;
    }

    /** Drop GPU resources for a model that's been unloaded. */
    public void invalidate(Object model)
    {
        batchCache.entrySet().removeIf(e ->
        {
            if (!e.getKey().model().equals(model)) return false;
            e.getValue().close();
            return true;
        });
    }

    /** Diagnostic: how many GPU resource sets are currently cached. */
    public int cachedBatchCount() { return batchCache.size(); }

}
