// Coded by Afrobama/Claude — Blender<->BBS live-sync bridge (Phase 2 world export).
package com.blenderbridge.world;

import com.blenderbridge.BridgeProtocol;

import com.google.gson.JsonObject;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.File;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * WorldExportBridge — turns a WORLD_EXPORT message into a {@link WorldExporter} run and back into
 * a WORLD_EXPORT_RESULT payload.
 *
 * <p>WIRE CONTRACT (agreed with the Blender add-on):</p>
 * <pre>
 * request:  {"type":"WORLD_EXPORT","origin":"blender","uuid":"world","seq":&lt;int&gt;,
 *            "fields":{"minX":&lt;int&gt;,"minY":&lt;int&gt;,"minZ":&lt;int&gt;,
 *                      "maxX":&lt;int&gt;,"maxY":&lt;int&gt;,"maxZ":&lt;int&gt;,
 *                      "streaming":&lt;bool, optional&gt;,"cullCaves":&lt;bool, optional, Task 3.3,
 *                      streaming-only, default false&gt;,"mergeFaces":&lt;bool, optional, Task 3.4,
 *                      streaming-only, default false&gt;}}
 * reply:    {"type":"WORLD_EXPORT_RESULT","origin":"bbs","uuid":"world","seq":&lt;int&gt;,
 *            "fields":{"ok":&lt;bool&gt;,"objPath":"&lt;abs path&gt;","blocks":&lt;int&gt;,
 *                      "faces":&lt;int&gt;,"error":"&lt;msg&gt;",
 *                      "chunksTotal":&lt;int, Task 3.2&gt;,"chunksLoaded":&lt;int, Task 3.2&gt;,
 *                      "missingChunks":["&lt;chunkX&gt; &lt;chunkZ&gt;", ..., Task 3.2, capped ~20]}}
 * </pre>
 *
 * <p>Bounds are INCLUSIVE block coordinates. The caller does NOT choose the output path — BBS
 * writes into {@link #DEFAULT_SUBDIR} under the game's run directory and returns the absolute
 * {@code objPath} (forward slashes) for the add-on to open. The reply always carries every field,
 * including {@code error} (empty on success) and the Task 3.2 chunk-coverage trio (0/0/[] on a
 * failure before any chunk was scanned), so the python side can read it without branching on key
 * presence.</p>
 *
 * <p>TASK 3.2 ("laden ehrlich machen"): a region whose far side sits outside the player's view
 * distance used to export as a silent, unmarked hole — {@code world.getBlockState} on an unloaded
 * chunk degrades to air with no signal of its own, so the export finished and reported
 * {@code ok:true} with a plausible-looking but wrong block/face count. {@code chunksTotal}/
 * {@code chunksLoaded}/{@code missingChunks} (see {@link ChunkCoverage}) let the add-on tell "empty
 * because it truly is air" from "empty because the chunk never loaded" and warn the user. Wired for
 * BOTH export paths (the synchronous alt-path in {@link WorldExporter#runExport} and the streaming
 * path in {@link StreamingWorldExporter#processColumnOnMainThread}) since both route their reply
 * through this same {@link #payload} method.</p>
 *
 * <p>THREADING: {@link #export(JsonObject)} must be called on the client main thread — it is
 * invoked from {@code BlenderBridgeClient}'s client-tick dispatch, and {@link WorldExporter} walks
 * live world/render state. The export is synchronous and stalls the game while it runs, which is
 * why {@link WorldExporter#MAX_VOLUME} bounds it.</p>
 */
public final class WorldExportBridge
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Output directory, relative to the game's run directory. */
    public static final String DEFAULT_SUBDIR = "blenderbridge_export";

    /** Sentinel for "field absent" — a real bound can legitimately be any int, including 0. */
    private static final long MISSING = Long.MIN_VALUE;

    private WorldExportBridge()
    {
        /* static-only helper */
    }

    /**
     * Run a world export described by a WORLD_EXPORT envelope's {@code fields}.
     *
     * <p>MAIN THREAD ONLY. Never throws — every failure comes back as a payload with
     * {@code ok:false} and a human-readable {@code error}, so the Blender side always gets an
     * answer instead of a silent timeout.</p>
     *
     * @param fields the request's {@code fields} object (may be null)
     * @return the WORLD_EXPORT_RESULT payload; never null
     */
    public static JsonObject export(JsonObject fields)
    {
        /* W8-B4 reject-honesty: every reject below that never reaches an exporter wraps its
         * payload in RejectCoverage.stamp — payload() reads ChunkCoverage's statics, which a
         * reject never armed, so it used to echo the PREVIOUS export's chunk tally. Rejects
         * that DO reach WorldExporter.exportRegion need no stamp: it now arms first (0/0/[]
         * on its own internal rejects, e.g. MAX_VOLUME). See RejectCoverage's class doc. */
        if (fields == null)
        {
            return RejectCoverage.stamp(payload(WorldExporter.ExportResult.failure("missing fields")));
        }

        long minX = BridgeProtocol.getLong(fields, BridgeProtocol.WORLD_MIN_X, MISSING);
        long minY = BridgeProtocol.getLong(fields, BridgeProtocol.WORLD_MIN_Y, MISSING);
        long minZ = BridgeProtocol.getLong(fields, BridgeProtocol.WORLD_MIN_Z, MISSING);
        long maxX = BridgeProtocol.getLong(fields, BridgeProtocol.WORLD_MAX_X, MISSING);
        long maxY = BridgeProtocol.getLong(fields, BridgeProtocol.WORLD_MAX_Y, MISSING);
        long maxZ = BridgeProtocol.getLong(fields, BridgeProtocol.WORLD_MAX_Z, MISSING);

        if (minX == MISSING || minY == MISSING || minZ == MISSING || maxX == MISSING || maxY == MISSING || maxZ == MISSING)
        {
            return RejectCoverage.stamp(payload(WorldExporter.ExportResult.failure("missing region bounds (need minX,minY,minZ,maxX,maxY,maxZ)")));
        }

        if (outOfIntRange(minX) || outOfIntRange(minY) || outOfIntRange(minZ) || outOfIntRange(maxX) || outOfIntRange(maxY) || outOfIntRange(maxZ))
        {
            return RejectCoverage.stamp(payload(WorldExporter.ExportResult.failure("region bounds out of block-coordinate range")));
        }

        File outDir = resolveOutDir(fields);

        if (outDir == null)
        {
            return RejectCoverage.stamp(payload(WorldExporter.ExportResult.failure("no run directory (client not ready)")));
        }

        BlockPos min = new BlockPos((int) minX, (int) minY, (int) minZ);
        BlockPos max = new BlockPos((int) maxX, (int) maxY, (int) maxZ);

        /* Task 3.1 (streaming pipeline): an explicit fields.streaming=true opts into the
         * off-render-thread path (StreamingWorldExporter — snapshots columns on the main thread,
         * meshes them on a worker pool). It returns null on a successful start, meaning it owns
         * sending progress + the final WORLD_EXPORT_RESULT itself, asynchronously — see
         * BlenderBridgeClient.handleWorldExport's null-check. Anything else (missing/false/absent
         * "streaming") falls through to the original synchronous alt-pfad below, UNCHANGED. */
        if (isStreamingRequested(fields))
        {
            /* ORDERING BUG FOUND + FIXED LIVE, 2026-07-17 (see StreamingWorldExporter.isActive's own
             * doc for the full write-up): this check MUST run before any of the arm/reset calls below.
             * A request arriving while a job is already active used to still overwrite cullCaves/
             * mergeFaces and WIPE the running job's write-sequencer state via resetWriteSequencer(),
             * even though start() was always going to reject the new request anyway a few lines later —
             * corrupting the ALREADY-RUNNING job's determinism for no benefit to the rejected one. */
            if (StreamingWorldExporter.isActive())
            {
                /* Stamp, NEVER arm here: the running job owns ChunkCoverage's statics right now
                 * (the exact scenario RejectCoverage's class doc explains). */
                return RejectCoverage.stamp(payload(WorldExporter.ExportResult.failure("a streaming export is already running")));
            }

            boolean cullCaves = isCullCavesRequested(fields);

            /* Task 3.4: inlined (not a new private helper method like isCullCavesRequested) — this
             * class is already loaded in the live JVM this session, and this file's own body-only
             * hotswap discipline (see the armNextJob call below) extends to not adding new METHODS
             * either, only new lines inside this already-existing method's body. Same boolean-or-
             * string wire tolerance as isCullCavesRequested. */
            String mergeFacesRaw = BridgeProtocol.getFieldString(fields, "mergeFaces", "false");
            boolean mergeFaces = "true".equalsIgnoreCase(mergeFacesRaw) || "1".equals(mergeFacesRaw);

            LOGGER.info("[blenderbridge] WORLD_EXPORT (streaming) request: [{},{},{}]..[{},{},{}] cullCaves={} mergeFaces={}", minX, minY, minZ, maxX, maxY, maxZ, cullCaves, mergeFaces);

            /* Task 3.3: arm cave culling BEFORE starting the job — a static flag on the brand-new
             * SkyReachability class, not a new parameter here (see its class doc "DEPLOY-WINDOW-FREE
             * WIRING" section for why: StreamingWorldExporter.start is already loaded in this JVM and
             * cannot grow a parameter without a restart, this call site can still change its BODY).
             * resetWriteSequencer: fresh determinism-ordering state for THIS job (see SkyReachability's
             * "DETERMINISTIC WRITE ORDER" section — a measured-live bug fix, cullCaves specifically).
             * Safe now: the isActive() check above guarantees we only reach here when no OTHER job is
             * mid-flight to corrupt. */
            SkyReachability.armNextJob(cullCaves);
            SkyReachability.resetWriteSequencer();

            /* Task 3.4: arm face merging the same way — a static flag on the brand-new FaceMerger
             * class (see its class doc for why), not a new parameter on this already-loaded method.
             * FaceMerger.merge is a pure per-column function with no cross-job state (see its class
             * doc THREADING section), so unlike SkyReachability there is no reset call needed here. */
            FaceMerger.armNextJob(mergeFaces);

            /* End-review fix 2 (request correlation): remember THIS job's requestId so the
             * asynchronous result StreamingWorldExporter.sendResult builds later can echo it — the
             * add-on only accepts its own result (see ExportCorrelation's class doc). Deliberately
             * AFTER the isActive() guard above: a rejected request must never overwrite the running
             * job's id (its own synchronous reject reply is stamped in handleWorldExport instead). */
            ExportCorrelation.armNextJob(BridgeProtocol.getFieldString(fields, "requestId", ""));

            /* Task 3.3c: close the mesh at the region boundary — the mirror of the alt path's
             * RegionClamp (out-of-region neighbours count as air, so walls + floor get emitted).
             * Same deploy-window-free wiring as SkyReachability/FaceMerger: state lives on the
             * brand-new StreamRegionClamp class, this already-loaded body only gains lines.
             * fields.openBounds=true restores the old open-skin behaviour. */
            String openBoundsRaw = BridgeProtocol.getFieldString(fields, "openBounds", "false");
            boolean openBounds = "true".equalsIgnoreCase(openBoundsRaw) || "1".equals(openBoundsRaw);
            StreamRegionClamp.armNextJob(!openBounds, (int) minX, (int) minY, (int) minZ, (int) maxX, (int) maxY, (int) maxZ);

            try
            {
                JsonObject started = StreamingWorldExporter.start(min, max, outDir);

                /* Task 3.6 (live-block-delta): a null return means the job STARTED — from now on
                 * this region is what Blender will hold, so re-target the delta tracker to it.
                 * Deliberately on start (not job completion): completion lives in
                 * StreamingWorldExporter.sendResult, whose class is already loaded (body edits
                 * only there too, but arming here keeps ALL delta wiring in this one method).
                 * Blocks changed DURING the job land in the snapshot sweep either way. A failed
                 * start (non-null payload) falls through un-armed — the tracker keeps watching
                 * the PREVIOUS successful region, which is still what Blender has imported. */
                if (started == null)
                {
                    BlockDeltaTracker.armRegion(Minecraft.getInstance().level,
                        (int) minX, (int) minY, (int) minZ, (int) maxX, (int) maxY, (int) maxZ);

                    return null;
                }

                /* W8-B4 reject-honesty: a non-null return from start() is ALWAYS a failed start,
                 * and every one of start()'s reject payloads is built BEFORE its own
                 * ChunkCoverage.armForExport call (verified against all six failure returns) —
                 * so it carries the stale tally of whatever export ran last. Stamp it honest. */
                return RejectCoverage.stamp(started);
            }
            catch (Throwable t)
            {
                LOGGER.error("[blenderbridge] streaming WORLD_EXPORT failed to start", t);

                return RejectCoverage.stamp(payload(WorldExporter.ExportResult.failure(t.getClass().getSimpleName() + ": " + t.getMessage())));
            }
        }

        /* End-review fix 3 (finding 3, 2026-07-17): the 86d5628 guard above only covers a second
         * STREAMING request — a request WITHOUT streaming:true (the default!) used to run the
         * synchronous alt-path unchecked while a streaming job was mid-flight. Both paths resolve
         * the SAME outDir and file names (resolveOutDir -> blenderbridge_export/world.obj), so the
         * alt-path truncated the very world.obj the running job still held open and kept writing
         * (interleaved/corrupt OBJ, last-writer-wins world_meta.json), and its ChunkCoverage
         * .armForExport wiped the running job's coverage tally (the final result then reported the
         * WRONG region's chunksTotal/chunksLoaded). Reject exactly like the streaming branch does. */
        if (StreamingWorldExporter.isActive())
        {
            /* Stamp, NEVER arm — same running-job reasoning as the streaming branch's reject. */
            return RejectCoverage.stamp(payload(WorldExporter.ExportResult.failure("a streaming export is already running")));
        }

        LOGGER.info("[blenderbridge] WORLD_EXPORT request: [{},{},{}]..[{},{},{}]", minX, minY, minZ, maxX, maxY, maxZ);

        /* exportRegion already swallows its own exceptions into an ExportResult; this catch is the
         * belt-and-braces guard so a bug here can never kill the tick handler. */
        try
        {
            WorldExporter.ExportResult result = WorldExporter.exportRegion(min, max, outDir);

            /* Task 3.6 (live-block-delta): only a SUCCESSFUL export re-targets the delta tracker
             * — see the streaming branch's arm above for the reasoning. Body-only edit; all
             * tracker state lives on the brand-new lazy BlockDeltaTracker class. */
            if (result.ok)
            {
                BlockDeltaTracker.armRegion(Minecraft.getInstance().level,
                    (int) minX, (int) minY, (int) minZ, (int) maxX, (int) maxY, (int) maxZ);
            }

            return payload(result);
        }
        catch (Throwable t)
        {
            LOGGER.error("[blenderbridge] WORLD_EXPORT failed", t);

            return payload(WorldExporter.ExportResult.failure(t.getClass().getSimpleName() + ": " + t.getMessage()));
        }
    }

    /**
     * True when the request opted into the streaming pipeline via {@code fields.streaming}. Tolerates
     * either a JSON boolean or a string ("true"/"1") since Gson's {@code getAsString()} on a boolean
     * primitive already yields "true"/"false", so this single check covers both wire shapes without
     * needing a new {@code BridgeProtocol} constant.
     */
    private static boolean isStreamingRequested(JsonObject fields)
    {
        String raw = BridgeProtocol.getFieldString(fields, "streaming", "false");

        return "true".equalsIgnoreCase(raw) || "1".equals(raw);
    }

    /**
     * True when the request opted into Task 3.3's sky-reachability cave culling via {@code
     * fields.cullCaves} (streaming path only — the synchronous alt-path is untouched, see {@code
     * StreamingWorldExporter}'s class doc TASK 3.3 section). Same boolean-or-string tolerance as
     * {@link #isStreamingRequested}. Default false: an absent/false field is byte-identical to the
     * pre-3.3 export, which is exactly what the Task 3.1 face-count gate depends on.
     */
    private static boolean isCullCavesRequested(JsonObject fields)
    {
        String raw = BridgeProtocol.getFieldString(fields, "cullCaves", "false");

        return "true".equalsIgnoreCase(raw) || "1".equals(raw);
    }

    /**
     * Where to write. Defaults to {@code <runDirectory>/blenderbridge_export}. An {@code out} field
     * overrides it — not part of the Blender contract, but it lets an EXEC/manual caller redirect
     * the dump without a rebuild.
     */
    private static File resolveOutDir(JsonObject fields)
    {
        String out = BridgeProtocol.getFieldString(fields, BridgeProtocol.WORLD_OUT, null);

        if (out != null && !out.trim().isEmpty())
        {
            return new File(out.trim());
        }

        Minecraft client = Minecraft.getInstance();

        if (client == null || client.gameDirectory == null)
        {
            return null;
        }

        return new File(client.gameDirectory, DEFAULT_SUBDIR);
    }

    /**
     * Build the reply payload. Every key is always present — the python side reads them blind.
     *
     * <p>Package-private (not private) so {@link StreamingWorldExporter} can build its own
     * asynchronous WORLD_EXPORT_RESULT with the exact same field shape as this synchronous path's
     * reply — same package, no cross-package exposure, no new class member added elsewhere.</p>
     */
    static JsonObject payload(WorldExporter.ExportResult result)
    {
        JsonObject out = new JsonObject();

        out.addProperty(BridgeProtocol.WORLD_OK, result.ok);
        out.addProperty(BridgeProtocol.WORLD_OBJ_PATH, result.objPath == null ? "" : result.objPath);
        out.addProperty(BridgeProtocol.WORLD_MTL_PATH, result.mtlPath == null ? "" : result.mtlPath);
        out.addProperty(BridgeProtocol.WORLD_TEX_DIR, result.texDir == null ? "" : result.texDir);
        out.addProperty(BridgeProtocol.WORLD_BLOCKS, result.blockCount);
        out.addProperty(BridgeProtocol.WORLD_FACES, result.faceCount);
        out.addProperty(BridgeProtocol.WORLD_ERROR, result.error == null ? "" : result.error);

        /* Task 3.2: "laden ehrlich machen" — an export whose region touches unloaded chunks used to
         * come back ok:true with silent holes (see WorldExporter's own dossier entry). ChunkCoverage
         * is a separate lazy class (not a new ExportResult field — see its class doc for why), read
         * here rather than threaded through the constructor so this already-loaded class only gains
         * a body line. Present on EVERY reply (success or failure) so the addon never has to branch
         * on key presence, matching every other field in this payload; on a failed export before any
         * chunk was scanned this is legitimately 0/0/[]. */
        out.addProperty("chunksTotal", ChunkCoverage.chunksTotal());
        out.addProperty("chunksLoaded", ChunkCoverage.chunksLoaded());
        out.add("missingChunks", ChunkCoverage.missingChunksJson());

        return out;
    }

    private static boolean outOfIntRange(long value)
    {
        return value < Integer.MIN_VALUE || value > Integer.MAX_VALUE;
    }
}
