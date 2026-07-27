// Coded by Afrobama/Claude — Tier 1d UIReplayList crowdMode shared state.
//
// Lives OUTSIDE the mixin/ package because Mixin classes cannot hold
// public static fields (enforced by our mixin-no-public-static rule —
// see feedback_mixin_no_public_static memo).
//
// The mixin {@code UIReplayListCrowdModeMixin} writes typeCounts each
// frame at HEAD of renderElementPart and reads them at TAIL of render
// to draw the aggregated strip. The static map is single-threaded
// (render thread only) so a plain LinkedHashMap is enough.
package com.afrobama.bbstools.client.perf;

import java.util.LinkedHashMap;
import java.util.Map;

import com.afrobama.bbstools.perf.BBSPerfConfig;

public final class UIReplayCrowdModeState
{
    private UIReplayCrowdModeState() {}

    /** Insertion-ordered so the strip lists actor types in the order
     *  they first appear in the replay list — keeps the visual stable
     *  across frames even as the user scrolls/expands groups. */
    private static final Map<String, Integer> COUNTS = new LinkedHashMap<>();

    /** How many rows were skipped this frame (for /bbsdebug stats). */
    public static volatile long rowsSkippedThisFrame = 0L;
    /** Cumulative since session start. */
    public static volatile long totalRowsSkipped = 0L;
    /** Frames the strip was rendered (active). */
    public static volatile long activeFrames = 0L;

    public static boolean isEnabled()
    {
        return BBSPerfConfig.UI_REPLAY_LIST_CROWD_MODE;
    }

    public static void setEnabled(boolean v)
    {
        BBSPerfConfig.UI_REPLAY_LIST_CROWD_MODE = v;
    }

    /** Called at HEAD of UIReplayList.render — wipe the per-frame
     *  aggregation map so we start fresh. */
    public static void beginFrame()
    {
        COUNTS.clear();
        rowsSkippedThisFrame = 0L;
    }

    /** Called from per-row HEAD when crowd mode is active — bucket the
     *  row's actor-type name. Returns true if caller should cancel the
     *  vanilla per-row render. */
    public static boolean recordAndSkip(String typeName)
    {
        if (typeName == null || typeName.isEmpty()) typeName = "(unknown)";
        COUNTS.merge(typeName, 1, Integer::sum);
        rowsSkippedThisFrame++;
        totalRowsSkipped++;
        return true;
    }

    /** Snapshot for the strip renderer — returns a stable view of the
     *  aggregated counts so we don't iterate the map concurrently with
     *  a future modification (e.g. multiple lists in same screen). */
    public static Map<String, Integer> snapshot()
    {
        return new LinkedHashMap<>(COUNTS);
    }

    public static int distinctTypes()
    {
        return COUNTS.size();
    }

    public static int totalRowsAggregated()
    {
        int sum = 0;
        for (int v : COUNTS.values()) sum += v;
        return sum;
    }

    /** Called when the strip was successfully rendered this frame. */
    public static void noteRendered()
    {
        activeFrames++;
    }

}
