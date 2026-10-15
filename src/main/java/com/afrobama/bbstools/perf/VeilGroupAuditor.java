// Coded by Afrobama/Claude — Tier 3h mesh deduplication audit.
//
// Tracks per-frame ratio of submitted-replays to unique-(model,texture)
// groups in the Veil batch system. Low ratio (<5 with >50 replays) means
// dedup is broken — different replays use unique mesh/texture combos
// instead of sharing, so each gets its own batch + draw call.
//
// Wires into BaseFilmControllerVeilMixin's flush block to snapshot stats.
// Registered with BBSDebugRegistry under "veil" subcommand.
package com.afrobama.bbstools.perf;

import com.afrobama.bbstools.debug.IFeatureDebugger;

import java.util.concurrent.atomic.AtomicLong;

public final class VeilGroupAuditor implements IFeatureDebugger
{
    public static final VeilGroupAuditor INSTANCE = new VeilGroupAuditor();

    public final AtomicLong replaysSubmitted = new AtomicLong();
    public final AtomicLong groupCount = new AtomicLong();
    public final AtomicLong framesCounted = new AtomicLong();
    public final AtomicLong alarmCount = new AtomicLong();
    public volatile long lastFrameReplays;
    public volatile long lastFrameGroups;
    public volatile double lastRatio;
    public volatile long lastAlarmTickNs;

    private VeilGroupAuditor() {}

    /**
     * Called from BaseFilmControllerVeilMixin AFTER flush. Snapshots
     * (replays, groups). Computes ratio. Logs alarm if dedup looks broken.
     */
    public void snapshot(int replaysThisFrame, int groupsThisFrame)
    {
        lastFrameReplays = replaysThisFrame;
        lastFrameGroups = groupsThisFrame;
        replaysSubmitted.addAndGet(replaysThisFrame);
        groupCount.addAndGet(groupsThisFrame);
        long frames = framesCounted.incrementAndGet();
        double ratio = groupsThisFrame == 0 ? 0.0 : (double) replaysThisFrame / groupsThisFrame;
        lastRatio = ratio;

        if (ratio < 5.0 && replaysThisFrame > 50)
        {
            alarmCount.incrementAndGet();
            long now = System.nanoTime();
            if (now - lastAlarmTickNs > 5_000_000_000L)
            {
                lastAlarmTickNs = now;
                org.slf4j.LoggerFactory.getLogger("bbstools/veil-audit").warn(
                    "VeilGroupAuditor: dedup looks broken — replays={} groups={} ratio={} (threshold 5.0). " +
                    "Different replays may be using unique (model, texture) combos instead of sharing.",
                    replaysThisFrame, groupsThisFrame, String.format("%.2f", ratio));
            }
        }
    }

    @Override public String getName() { return "veil-audit"; }
    @Override public int priority() { return 50; }

    @Override
    public void writeStatus(StringBuilder sb)
    {
        sb.append("lastFrame: replays=").append(lastFrameReplays)
          .append(" groups=").append(lastFrameGroups)
          .append(" ratio=").append(String.format("%.2f", lastRatio)).append('\n');
        sb.append("framesCounted=").append(framesCounted.get()).append('\n');
        sb.append("alarmCount=").append(alarmCount.get()).append('\n');
    }

    @Override
    public void writeStats(StringBuilder sb)
    {
        long frames = framesCounted.get();
        sb.append("totalReplays=").append(replaysSubmitted.get()).append('\n');
        sb.append("totalGroups=").append(groupCount.get()).append('\n');
        if (frames > 0)
        {
            sb.append("avgReplaysPerFrame=").append(replaysSubmitted.get() / frames).append('\n');
            sb.append("avgGroupsPerFrame=").append(groupCount.get() / frames).append('\n');
        }
    }

}
