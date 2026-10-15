// Coded by Afrobama/Claude — Blender<->BBS live-sync bridge (W8-B4: honest reject coverage).
package com.blenderbridge.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * RejectCoverage — stamp the honest "nothing was scanned" chunk-coverage trio
 * ({@code chunksTotal:0}/{@code chunksLoaded:0}/{@code missingChunks:[]}) onto a
 * WORLD_EXPORT_RESULT reject payload.
 *
 * <h2>WHY (the stale-echo bug, W8 fixer queue)</h2>
 * <p>{@link WorldExportBridge#payload} always reads {@link ChunkCoverage}'s statics (Task 3.2),
 * but a bridge-level reject — malformed request, busy-reject while a streaming job runs, a
 * failed streaming start — never arms them. The reply then echoed the PREVIOUS export's
 * coverage numbers as if they described the rejected request: a request with missing bounds
 * could come back {@code ok:false} yet claim "2 chunks total, 2 loaded", which the add-on's
 * chunk-honesty warning (Task 3.2-addon) would happily trust.</p>
 *
 * <h2>WHY NOT {@code ChunkCoverage.armForExport()} in the reject branch</h2>
 * <p>The obvious fix — arm (i.e. clear) the tally before building the reject — mutates SHARED
 * statics, and a reject can arrive exactly WHILE a streaming job is mid-flight (that is what
 * the busy-reject is for). End-review fix 3 (b7c81ed) exists precisely because a rejected
 * request used to wipe the running job's coverage tally; re-introducing that wipe as an
 * "honesty" fix would trade one lie (stale echo on the reject) for another (zeroed coverage on
 * the running job's final result). Overwriting the three keys on the reject's OWN JsonObject
 * touches no shared state at all.</p>
 *
 * <h2>WHY A NEW CLASS (RegionClamp/ExportCorrelation pattern)</h2>
 * <p>{@code WorldExportBridge} is loaded in the live JVM — deploy-window discipline allows its
 * existing methods only BODY edits, not new members. A brand-new lazy class carries the helper;
 * the reject sites each gain a single wrapping call inside an already-existing method body.</p>
 *
 * <p>THREADING: main thread only (every caller lives in {@code WorldExportBridge.export}'s
 * client-tick dispatch). Stateless — no fields, nothing to arm or leak.</p>
 */
public final class RejectCoverage
{
    private RejectCoverage()
    {
        /* static-only helper */
    }

    /**
     * Overwrite the Task 3.2 coverage trio on {@code reject} with the honest empty values and
     * return the same object (wrap-a-return-expression friendly). All other fields untouched.
     *
     * @param reject a WORLD_EXPORT_RESULT payload built for a request that never scanned a chunk
     * @return {@code reject} itself, coverage trio now 0/0/[]
     */
    public static JsonObject stamp(JsonObject reject)
    {
        reject.addProperty("chunksTotal", 0);
        reject.addProperty("chunksLoaded", 0);
        reject.add("missingChunks", new JsonArray());

        return reject;
    }
}
