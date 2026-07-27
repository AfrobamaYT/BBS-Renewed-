// Coded by Afrobama/Claude — Tier 3g static-batch paused replays.
//
// Per-replay bone-matrix cache that returns the cached flat float[]
// when the film is paused at the same play-state version, skipping
// the per-frame setupMatrices / matrix-flatten work entirely.
//
// VERSION COUNTER — single source of truth for "anything that could
// change a paused replay's pose":
//
//   1. FilmPlayStateMixin → bump on togglePause() RETURN.
//      Covers: play↔pause toggle.
//   2. FilmPlayStateMixin → bump on WorldFilmController.update() RETURN
//      whenever tick advanced (i.e. !paused). Covers: every tick that
//      moves the play-head.
//   3. observeTick(controller, tick) — called from the render path
//      every frame. If the controller's current tick differs from the
//      last-observed tick OR the controller identity changed, bump.
//      Belt-and-suspenders that catches UI scrub (which mutates
//      WorldFilmController.tick directly, bypassing any setter).
//
// This three-pronged invalidation gives HIGH confidence that any
// pose-affecting event invalidates the cache before the next render
// reads it. The render-side observeTick() is the failsafe — even if
// I missed a mutation path, it's caught within one frame.
package com.afrobama.bbstools.perf;

import com.afrobama.bbstools.debug.IFeatureDebugger;

import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public final class PausedReplayMatrixCache implements IFeatureDebugger
{
    public static final PausedReplayMatrixCache INSTANCE = new PausedReplayMatrixCache();

    /** Monotonic version. Anything that could change a paused replay's
     *  pose increments this. Cache entries store the version at which
     *  they were computed; mismatch ⇒ recompute. */
    private final AtomicLong pauseVersion = new AtomicLong(1L);

    /** WeakHashMap so a replay that's been GC'd doesn't leak a buffer.
     *  Synchronized externally — render thread is the only writer in
     *  practice but observeTick() can run from anywhere. */
    private final WeakHashMap<Object, CachedEntry> cache = new WeakHashMap<>();

    /** Last observed (tick, paused) per controller identity, for the
     *  observeTick() failsafe. Identity-based — we don't trust .equals
     *  on a film controller. */
    private final WeakHashMap<Object, ControllerObservation> observed = new WeakHashMap<>();

    private PausedReplayMatrixCache() {}

    public long currentVersion()
    {
        return pauseVersion.get();
    }

    /** Bump the version, invalidating every cached entry. Returns the
     *  new version for callers that want to log it. */
    public long bump()
    {
        return pauseVersion.incrementAndGet();
    }

    /** Returns the cached float[] if {@code replay}'s entry matches
     *  {@code currentVersion}, otherwise computes via {@code compute},
     *  stores, and returns. Caller should only invoke this on the
     *  paused-and-stable code path — invoking it while playing would
     *  not be incorrect (cache would just miss every frame) but wastes
     *  the wrap cost. */
    public float[] getOrCompute(Object replay, long currentVersion, Supplier<float[]> compute)
    {
        if (replay == null) return compute.get();
        synchronized (cache)
        {
            CachedEntry hit = cache.get(replay);
            if (hit != null && hit.version == currentVersion)
            {
                BBSPerfConfig.staticPausedSkips++;
                return hit.bonesFlat;
            }
        }
        float[] fresh = compute.get();
        synchronized (cache)
        {
            cache.put(replay, new CachedEntry(fresh, currentVersion));
        }
        BBSPerfConfig.staticPausedRecomputes++;
        return fresh;
    }

    /** Render-side failsafe: every frame, the renderer reports the
     *  current (controller, tick, paused) tuple. If anything changed
     *  vs the last observation, the version is bumped. This catches
     *  UI scrubs that mutate WorldFilmController.tick directly. */
    public void observeTick(Object controller, int currentTick, boolean paused)
    {
        if (controller == null) return;
        synchronized (observed)
        {
            ControllerObservation prev = observed.get(controller);
            if (prev == null || prev.tick != currentTick || prev.paused != paused)
            {
                observed.put(controller, new ControllerObservation(currentTick, paused));
                if (prev != null)
                {
                    /* Don't bump on first-sight — first observation
                     * just records the baseline. Subsequent diffs
                     * are real state changes. */
                    bump();
                }
            }
        }
    }

    /** Hard reset on world change / film unload. Drops all entries
     *  AND the observation map so a fresh film starts clean. */
    public void reset()
    {
        synchronized (cache)   { cache.clear();    }
        synchronized (observed){ observed.clear(); }
        bump();
    }

    /** Diagnostic for the debugger UI. */
    public int cachedEntryCount()
    {
        synchronized (cache) { return cache.size(); }
    }

    /* ----- IFeatureDebugger -------------------------------------- */

    @Override public String getName() { return "static-paused"; }

    @Override public boolean isEnabled() { return BBSPerfConfig.ENABLE_STATIC_PAUSED_BATCH; }

    @Override public void setEnabled(boolean v) { BBSPerfConfig.ENABLE_STATIC_PAUSED_BATCH = v; }

    @Override
    public void writeStatus(StringBuilder sb)
    {
        sb.append("enabled=").append(BBSPerfConfig.ENABLE_STATIC_PAUSED_BATCH).append('\n');
        sb.append("pauseVersion=").append(pauseVersion.get()).append('\n');
        sb.append("cachedEntries=").append(cachedEntryCount()).append('\n');
        synchronized (observed) { sb.append("observedControllers=").append(observed.size()).append('\n'); }
    }

    @Override
    public void writeStats(StringBuilder sb)
    {
        sb.append("staticPausedSkips=").append(BBSPerfConfig.staticPausedSkips).append('\n');
        sb.append("staticPausedRecomputes=").append(BBSPerfConfig.staticPausedRecomputes).append('\n');
        long total = BBSPerfConfig.staticPausedSkips + BBSPerfConfig.staticPausedRecomputes;
        if (total > 0L)
        {
            double hitRate = 100.0D * BBSPerfConfig.staticPausedSkips / total;
            sb.append(String.format("hitRate=%.1f%%%n", hitRate));
        }
    }

    /* ----- types -------------------------------------------------- */

    private record CachedEntry(float[] bonesFlat, long version) {}

    private record ControllerObservation(int tick, boolean paused) {}

}
