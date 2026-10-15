// Coded by Afrobama/Claude — Tier 3g static-batch paused replays.
//
// Per-replay bone-matrix cache keyed by replay UUID. The cache stores
// the flat float[] bone-matrix array that the BOBJModelVAO uploads to
// GPU each frame. While the film is paused at a given tick the matrices
// don't change, so we can skip the entire rebuild + GPU upload by
// returning the cached array.
//
// CACHE-KEY DESIGN
//   - replay UUID  : stable across the film's lifetime (Replay.uuid)
//   - tick         : the controller's current play-head tick when the
//                    entry was captured. If the user scrubs the timeline
//                    while still paused, the cache MUST miss so the
//                    new pose is computed. We invalidate-on-tick-change
//                    automatically inside isPausedAndCacheValid().
//
// INVALIDATION
//   - resume          → invalidateAll() (mixin on togglePause)
//   - scrub paused    → per-replay miss via tick != cached.tick
//   - replay deletion → invalidate(uuid) (best-effort; weak refs to
//                       the float[] are not necessary because the map
//                       itself is bounded by active replay count)
//
// SAFETY
//   - Default OFF (BBSPerfConfig.ENABLE_PAUSED_BONE_CACHE = false).
//   - ConcurrentHashMap is render-thread-safe.
//   - cacheHits / cacheMisses are volatile longs (racy increments are
//     acceptable for diagnostic counters; we don't bill anyone).
package com.afrobama.bbstools.perf;

import com.afrobama.bbstools.debug.IFeatureDebugger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PausedReplayBoneCache implements IFeatureDebugger
{
    public static final PausedReplayBoneCache INSTANCE = new PausedReplayBoneCache();

    /** Per-replay cached matrices. Value is the flat float[] (16 floats
     *  per bone matrix, bones * 16 total) AS UPLOADED last frame. */
    public static final Map<UUID, float[]> cached = new ConcurrentHashMap<>();

    /** Tick at which the cache entry for each replay was captured. If
     *  the controller advances to a new tick (e.g. user scrubs the
     *  timeline while paused) the entry must miss and recompute. */
    private static final Map<UUID, Integer> cachedTick = new ConcurrentHashMap<>();

    public static volatile long cacheHits = 0L;
    public static volatile long cacheMisses = 0L;

    private PausedReplayBoneCache() {}

    /**
     * @return true if cache for {@code replay} exists AND tick matches
     *         the cached tick. Caller may then reuse the cached matrices
     *         and skip the rebuild/upload.
     */
    public boolean isPausedAndCacheValid(UUID replay, int tick)
    {
        if (!BBSPerfConfig.ENABLE_PAUSED_BONE_CACHE) return false;
        if (replay == null) return false;

        Integer t = cachedTick.get(replay);
        if (t == null) return false;
        if (t.intValue() != tick) return false;

        return cached.containsKey(replay);
    }

    /**
     * Look up the cached float[] for {@code replay} at {@code tick}.
     * Returns {@code null} if either the cache is disabled, the replay
     * is unknown, or the tick has advanced. Increments hit/miss counters.
     */
    public float[] tryGet(UUID replay, int tick)
    {
        if (!BBSPerfConfig.ENABLE_PAUSED_BONE_CACHE) return null;
        if (replay == null) return null;

        Integer t = cachedTick.get(replay);
        if (t == null || t.intValue() != tick)
        {
            cacheMisses++;
            BBSPerfConfig.pausedBoneCacheMisses++;
            return null;
        }
        float[] arr = cached.get(replay);
        if (arr == null)
        {
            cacheMisses++;
            BBSPerfConfig.pausedBoneCacheMisses++;
            return null;
        }
        cacheHits++;
        BBSPerfConfig.pausedBoneCacheHits++;
        return arr;
    }

    /** Store a freshly-computed flat matrix array for {@code replay}
     *  at {@code tick}. Caller passes a defensive copy if the source
     *  buffer is mutable. */
    public void store(UUID replay, int tick, float[] matrices)
    {
        if (replay == null || matrices == null) return;
        cached.put(replay, matrices);
        cachedTick.put(replay, tick);
    }

    /** Drop a single replay's cache (e.g. user moved/deleted it). */
    public void invalidate(UUID replay)
    {
        if (replay == null) return;
        cached.remove(replay);
        cachedTick.remove(replay);
    }

    /** Drop every entry (e.g. play resumed, world unloaded, film closed). */
    public void invalidateAll()
    {
        cached.clear();
        cachedTick.clear();
    }

    public int cachedEntryCount()
    {
        return cached.size();
    }

    /* ----- IFeatureDebugger -------------------------------------- */

    @Override public String getName() { return "paused-bone-cache"; }
    @Override public int priority() { return 38; }

    @Override
    public boolean isEnabled()
    {
        return BBSPerfConfig.ENABLE_PAUSED_BONE_CACHE;
    }

    @Override
    public void setEnabled(boolean v)
    {
        BBSPerfConfig.ENABLE_PAUSED_BONE_CACHE = v;
        /* Keep the sister flag (used by the version-counter cache) in
         * sync so /bbsdebug paused-bone-cache on flips both code paths. */
        BBSPerfConfig.ENABLE_STATIC_PAUSED_BATCH = v;
        if (!v) invalidateAll();
    }

    @Override
    public void writeStatus(StringBuilder sb)
    {
        sb.append("enabled=").append(BBSPerfConfig.ENABLE_PAUSED_BONE_CACHE).append('\n');
        sb.append("entries=").append(cachedEntryCount()).append('\n');
        sb.append("sister(static-paused)=").append(BBSPerfConfig.ENABLE_STATIC_PAUSED_BATCH).append('\n');
    }

    @Override
    public void writeStats(StringBuilder sb)
    {
        long hits = BBSPerfConfig.pausedBoneCacheHits;
        long misses = BBSPerfConfig.pausedBoneCacheMisses;
        sb.append("hits=").append(hits).append('\n');
        sb.append("misses=").append(misses).append('\n');
        long total = hits + misses;
        if (total > 0L)
        {
            double rate = 100.0D * hits / total;
            sb.append(String.format("hitRate=%.1f%%%n", rate));
        }
        else
        {
            sb.append("hitRate=n/a (no traffic yet)\n");
        }
    }

}
