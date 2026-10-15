// Coded by Afrobama/Claude — Blender<->BBS live-sync bridge (end-review fix 2: request correlation echo).
package com.blenderbridge.world;

/**
 * ExportCorrelation — carries the streaming job's {@code requestId} from request time to its
 * asynchronous WORLD_EXPORT_RESULT, so the Blender add-on can tell ITS OWN result apart from a
 * broadcast of somebody else's export (end-review finding 2, 2026-07-17).
 *
 * <h2>WHY THIS EXISTS</h2>
 * <p>{@code BridgeServer} broadcasts every WORLD_EXPORT_RESULT to ALL connected clients, and the
 * reply envelope carries a FRESH outbound seq plus the constant uuid "world" — nothing in it
 * identified which request it answers. The add-on's solicited gate therefore only checked "do I
 * have a request in flight", which protected the idle instance but INVERTED during an own request:
 * whichever export finished first (an agent's, say) was imported and consumed the pending flag,
 * and the instance's own result was then dropped as "foreign". The add-on now sends a
 * {@code fields.requestId} (uuid4 hex) with each WORLD_EXPORT; the reply echoes it back.</p>
 *
 * <h2>WIRING (deploy-window-free, same pattern as {@link StreamRegionClamp}/{@link ChunkCoverage})</h2>
 * <ul>
 *   <li>SYNCHRONOUS replies (alt-path success/failure, streaming start-failures, busy-rejects):
 *       {@code BlenderBridgeClient.handleWorldExport} stamps the payload directly from the
 *       request's own fields — this class is NOT involved, so a rejected request is always stamped
 *       with ITS OWN id, never the running job's.</li>
 *   <li>ASYNCHRONOUS streaming result: {@code WorldExportBridge.export} calls {@link #armNextJob}
 *       right next to the other per-job arms (after the isActive() guards, so an active job's id
 *       can never be overwritten by a rejected request), and {@code StreamingWorldExporter
 *       .sendResult} reads it back via {@link #currentRequestId} when the job finishes/aborts.</li>
 * </ul>
 *
 * <p>A NEW class (not a field on {@code WorldExportBridge}/{@code StreamingWorldExporter}) because
 * both are already loaded in the live JVM this session — their methods may only gain body lines
 * (see the package dossier's deploy-window model and the {@code <clinit>} trap note: never
 * introduce initialized static fields on a LOADED class via hotswap; this class is brand-new and
 * lazy-loads with a normally-run {@code <clinit>}).</p>
 *
 * <p>THREADING: written on the main thread (request dispatch), read on the main thread
 * (sendResult runs in maybeFinish/abort, both tick-driven). Volatile anyway, matching
 * {@link StreamRegionClamp}'s belt-and-braces convention for this package's static holders.</p>
 *
 * <p>An empty id (old add-on, EXEC/manual caller without requestId) arms as "" and stamps nothing
 * — the add-on side treats a result without the key as the pre-correlation contract.</p>
 */
public final class ExportCorrelation
{
    /** requestId of the streaming job currently in flight; "" = none/no correlation requested. */
    private static volatile String armedRequestId = "";

    private ExportCorrelation()
    {
        /* static-only holder */
    }

    /**
     * Remember the requestId for the streaming job about to start. Call ONCE per accepted job,
     * main thread, AFTER the isActive() rejection guards (so a rejected request never clobbers
     * the running job's id) and BEFORE {@code StreamingWorldExporter.start}.
     */
    public static void armNextJob(String requestId)
    {
        armedRequestId = requestId == null ? "" : requestId;
    }

    /** The armed requestId, or "" when the job's request carried none. */
    public static String currentRequestId()
    {
        return armedRequestId;
    }
}
