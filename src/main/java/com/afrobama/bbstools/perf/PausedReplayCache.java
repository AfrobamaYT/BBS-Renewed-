// Coded by Afrobama/Claude — Tier 3g static-batch baking for paused
// replays. Skip bone-matrix re-upload when replay is paused at same tick.
//
// SCAFFOLD STATUS: API + frozen-set in place. Integration with
// BaseFilmControllerVeilMixin's setupMatrices skip is TODO.
package com.afrobama.bbstools.perf;

import com.afrobama.bbstools.debug.IFeatureDebugger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class PausedReplayCache implements IFeatureDebugger
{
    public static final PausedReplayCache INSTANCE = new PausedReplayCache();

    public static volatile boolean ENABLED = false;

    private final ConcurrentHashMap<Integer, Long> lastUploadFrame = new ConcurrentHashMap<>();
    private final java.util.Set<Integer> frozen = ConcurrentHashMap.newKeySet();

    public final AtomicLong skippedBoneUploads = new AtomicLong();
    public final AtomicLong invalidations = new AtomicLong();

    private PausedReplayCache() {}

    public boolean isFrozen(int replayId)
    {
        return ENABLED && frozen.contains(replayId);
    }

    public void markFrozen(int replayId, long currentFrame)
    {
        if (!ENABLED) return;
        frozen.add(replayId);
        lastUploadFrame.put(replayId, currentFrame);
        skippedBoneUploads.incrementAndGet();
    }

    public void invalidate(int replayId)
    {
        /* Code-review-fix F7: only count actual invalidations. */
        if (frozen.remove(replayId))
        {
            lastUploadFrame.remove(replayId);
            invalidations.incrementAndGet();
        }
    }

    public void invalidateAll()
    {
        frozen.clear();
        lastUploadFrame.clear();
        invalidations.incrementAndGet();
    }

    @Override public String getName() { return "paused-cache"; }
    @Override public boolean isEnabled() { return ENABLED; }
    @Override public void setEnabled(boolean v) { ENABLED = v; }

    @Override
    public void writeStatus(StringBuilder sb)
    {
        sb.append("enabled=").append(ENABLED).append('\n');
        sb.append("frozenCount=").append(frozen.size()).append('\n');
    }

    @Override
    public void writeStats(StringBuilder sb)
    {
        sb.append("skippedBoneUploads=").append(skippedBoneUploads.get()).append('\n');
        sb.append("invalidations=").append(invalidations.get()).append('\n');
    }

}
