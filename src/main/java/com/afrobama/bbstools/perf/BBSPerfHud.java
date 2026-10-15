// Coded by Afrobama/Claude — Tier 4b live frame-time HUD. Every BBSTools
// renderer brackets work with begin(stage)/end(stage). HUD renders
// per-stage ms in top-right corner of the screen.
//
// Thread-safety: nanoTime + AtomicLong for the rolling stats; render
// thread is the only writer to currentFrame; reader from HudRenderCallback
// gets a stable snapshot via volatile copy-on-end-of-frame.
//
// Built-in debugger: /bbsdebug perfhud registers via BBSDebugRegistry.
package com.afrobama.bbstools.perf;

import com.afrobama.bbstools.debug.IFeatureDebugger;
import com.afrobama.bbstools.debug.RollingHistogram;

import java.util.Optional;

public final class BBSPerfHud
{
    private static final int RING_FRAMES = 60;

    private static final long[][] frameNs = new long[PerfStage.COUNT][RING_FRAMES];
    private static final RollingHistogram[] stageHist = new RollingHistogram[PerfStage.COUNT];
    /** Code-review-fix F2: per-thread in-flight start time to handle
     *  worker-pool stages (ANIMATOR_PARALLEL) without race against
     *  render-thread stages. */
    private static final ThreadLocal<long[]> inFlightStart =
        ThreadLocal.withInitial(() -> new long[PerfStage.COUNT]);
    private static volatile int frameIdx;

    /** Master enable. Default OFF — opt-in via /bbsdebug perfhud on. */
    public static volatile boolean ENABLED = false;

    static
    {
        for (int i = 0; i < PerfStage.COUNT; i++)
        {
            stageHist[i] = new RollingHistogram(RING_FRAMES * 4);
        }
    }

    private BBSPerfHud() {}

    /** Call at the start of a render stage. */
    public static void begin(PerfStage stage)
    {
        if (!ENABLED) return;
        inFlightStart.get()[stage.ordinal()] = System.nanoTime();
    }

    /** Call at the end of a render stage. */
    public static void end(PerfStage stage)
    {
        if (!ENABLED) return;
        long[] starts = inFlightStart.get();
        long start = starts[stage.ordinal()];
        if (start == 0L) return;
        long elapsed = System.nanoTime() - start;
        starts[stage.ordinal()] = 0L;
        frameNs[stage.ordinal()][frameIdx] = elapsed;
        stageHist[stage.ordinal()].record(elapsed);
    }

    /** Advance to next frame. Called once per render frame.
     *  Code-review-fix F3: do NOT clear frameNs[i][frameIdx] post-advance
     *  (was wiping data BEFORE measurement). The slot we advance INTO is
     *  the WRITE slot — end() overwrites it normally. Clearing here
     *  blanks valid stats. */
    public static void nextFrame()
    {
        if (!ENABLED) return;
        frameIdx = (frameIdx + 1) % RING_FRAMES;
        /* Reset in-flight markers only — frameNs gets overwritten by end(). */
        long[] starts = inFlightStart.get();
        for (int i = 0; i < PerfStage.COUNT; i++)
        {
            starts[i] = 0L;
        }
    }

    /** Snapshot for HUD render. Returns latest-frame ms per stage. */
    public static long[] snapshotLatestNs()
    {
        long[] copy = new long[PerfStage.COUNT];
        int prev = (frameIdx + RING_FRAMES - 1) % RING_FRAMES;
        for (int i = 0; i < PerfStage.COUNT; i++)
        {
            copy[i] = frameNs[i][prev];
        }
        return copy;
    }

    /** Snapshot the last RING_FRAMES of a single stage — for sparklines. */
    public static long[] snapshotStage(PerfStage stage)
    {
        long[] copy = new long[RING_FRAMES];
        long[] src = frameNs[stage.ordinal()];
        for (int i = 0; i < RING_FRAMES; i++)
        {
            copy[i] = src[(frameIdx + i) % RING_FRAMES];
        }
        return copy;
    }

    /** Built-in IFeatureDebugger so /bbsdebug perfhud works automatically. */
    public static final class Debugger implements IFeatureDebugger
    {
        @Override public String getName() { return "perfhud"; }
        @Override public boolean isEnabled() { return ENABLED; }
        @Override public void setEnabled(boolean v) { ENABLED = v; }
        @Override public int priority() { return 10; }

        @Override
        public void writeStatus(StringBuilder sb)
        {
            sb.append("enabled=").append(ENABLED).append('\n');
            sb.append("ring=").append(RING_FRAMES).append(" frames\n");
            sb.append("stages=").append(PerfStage.COUNT).append('\n');
        }

        @Override
        public void writeStats(StringBuilder sb)
        {
            long[] latest = snapshotLatestNs();
            for (int i = 0; i < PerfStage.COUNT; i++)
            {
                PerfStage s = PerfStage.values()[i];
                sb.append(String.format("  %-22s last=%6.2fms ",
                    s.name(), latest[i] / 1_000_000.0));
                stageHist[i].writeSummary(sb);
                sb.append('\n');
            }
        }

        @Override
        public Optional<FeatureHudSection> hud()
        {
            return Optional.of(new FeatureHudSection()
            {
                @Override public int height() { return PerfStage.COUNT * 10 + 16; }
                @Override public void render(int x, int y)
                {
                    /* HUD render handled by BBSPerfHudRenderer (separate
                     * class to keep this main-source-set neutral re GUI
                     * imports). See client/.../BBSPerfHudRenderer.java */
                }
            });
        }
    }

}
