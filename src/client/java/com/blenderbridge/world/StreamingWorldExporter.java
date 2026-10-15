// Coded by Afrobama/Claude — Blender<->BBS live-sync bridge (Task 3.1: streaming world export).
package com.blenderbridge.world;

import com.blenderbridge.BlenderBridgeClient;
import com.blenderbridge.BridgeProtocol;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import org.slf4j.Logger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * StreamingWorldExporter — Task 3.1: takes the world export OFF the render thread by snapshotting
 * one chunk column at a time ({@link ColumnSnapshot}) on the main thread, then meshing each column's
 * blocks/fluids on a small worker pool, so a big region no longer freezes the game.
 *
 * <h2>WHY A NEW, SELF-CONTAINED CLASS (no changes to {@link WorldExporter}/{@link BlockEntityExporter})</h2>
 * <p>The deploy model for this JVM is: NEW classes load lazily (no boot), method BODIES hotswap live,
 * but FIELD/mixin changes to already-LOADED classes need a restart — and restarts are forbidden for
 * anything short of a real crash (see the project hard rule). {@code WorldExporter} and
 * {@code BlockEntityExporter} are both already loaded and working (they back the synchronous "alt-pfad"
 * this class is gated against). Rather than relaxing their private methods' visibility — which would
 * still be a redefinition of an already-loaded class carrying some risk, and would make the alt-pfad's
 * own file non-identical during the very comparison this task's proof gate depends on — this class
 * duplicates the small, mechanical pieces it needs (material/MTL/texture bookkeeping, the
 * bytecode-verified {@code BakedQuad} vertex decode) and reuses the two things that already are, or
 * happily can be, called unmodified: {@link ColumnSnapshot} (built for exactly this) and
 * {@link ObjVertexConsumer} (already package-private, no change needed) for fluid capture, and
 * {@link BlockEntityExporter#emit} (already public, called here exactly as the old synchronous scan
 * calls it — same live world, same main thread, same shared writer/index convention).</p>
 *
 * <h2>THREADING SPLIT (the actual point of this task)</h2>
 * <ul>
 * <li>MAIN THREAD, per tick, budgeted to {@link #TICK_BUDGET_NANOS} (5ms, panel M69): pop the next
 * pending column, build its {@link ColumnSnapshot} (constructor asserts main-thread), export any
 * block entities in it directly via the live world (BER stays main-thread — panel H22), then hand the
 * snapshot to the worker pool and move on. This is cheap per column (an array copy + a handful of BE
 * renders), which is what keeps frame time down.</li>
 * <li>WORKER THREADS: read {@link Block#shouldRenderFace}, {@link BakedModel#getQuads}, biome tint
 * (via the snapshot's own {@link ColumnSnapshot#getBlockTint}) and fluid rendering — the CPU-heavy part —
 * against the immutable, already-finished snapshot ONLY, never {@code client.level}. This mirrors
 * vanilla's own chunk-rebuild pipeline (worker threads calling these exact same APIs against a
 * region view), which is the established precedent this design leans on.</li>
 * <li>Each worker computes an entire column's quads into a private, lock-free list first (this is
 * what makes the parallelism real — no shared state touched while doing the expensive part), then
 * takes {@link #writerLock} ONCE to assign OBJ indices and write the (now cheap: pure text
 * formatting) result. Block-entity writes (main thread) go through the SAME lock/cursor, so indices
 * assigned by either side never collide.</li>
 * </ul>
 *
 * <h2>BACKPRESSURE (memory)</h2>
 * <p>{@link ColumnSnapshot}'s own class-doc warns roughly ~1MB per held snapshot and that the
 * pipeline "must keep only a small rolling window... never the whole export's worth at once". This
 * class enforces that with {@link #MAX_IN_FLIGHT}: the main thread stops submitting new columns once
 * that many are snapshotted-but-not-yet-fully-written, regardless of remaining tick budget.</p>
 *
 * <h2>WIRE / ASYNC REPLY</h2>
 * <p>{@link #start} returns {@code null} to its caller ({@code WorldExportBridge.export}) — that is
 * the signal (see {@code BlenderBridgeClient.handleWorldExport}) that this class owns sending the
 * reply itself, asynchronously: periodic {@code WORLD_EXPORT_PROGRESS} envelopes (~1/s, panel M68)
 * while the job runs, then exactly one {@code WORLD_EXPORT_RESULT} when it finishes (or fails/aborts).
 * The result payload is built via {@code WorldExportBridge.payload(...)} against a real
 * {@link WorldExporter.ExportResult}, so it is byte-shape-identical to the synchronous path's reply.</p>
 *
 * <h2>KNOWN SCOPE LIMIT</h2>
 * <p>{@link BlockEntityExporter} keeps its own export-in-progress state statically, keyed by "is this
 * the same {@code BufferedWriter} as last time". Running a synchronous ({@code WorldExporter}) export
 * and a streaming export INTERLEAVED (not sequentially) would confuse that tracking. Not fixed here —
 * out of this task's scope — just don't run both at once (this class also refuses to start a second
 * streaming job while one is active).</p>
 *
 * <h2>TASK 3.3: CAVE CULLING ({@code fields.cullCaves})</h2>
 * <p>An explicit {@code cullCaves=true} on the request (see {@code WorldExportBridge}) makes
 * {@link #meshColumn} arm a {@link SkyReachability} flood-fill for the column it is about to mesh
 * (WORKER thread) via {@link SkyReachability#enterColumn}, and {@link #meshBlock}/{@link #meshFluid}
 * skip emitting a face unless at least one of its two adjacent cells is sky-reachable (checked via
 * {@link SkyReachability#shouldCullFace}/{@link SkyReachability#shouldCull}) — the plan's "surface
 * only" export option. THIS CLASS'S PUBLIC SURFACE AND FIELDS ARE UNCHANGED by this option: see
 * {@link SkyReachability}'s own class doc "DEPLOY-WINDOW-FREE WIRING" section for why — this JVM
 * already has this class loaded without a restart-capable JDI redefine (canAddMethod=false,
 * measured), so every bit of Task 3.3 state lives in the brand-new {@link SkyReachability} class
 * (a static arm flag + a per-worker-thread {@code ThreadLocal}) instead of a new parameter/field
 * here. DEFAULT OFF: with {@code cullCaves} absent/false, every {@code shouldCull*} check returns
 * {@code false} unconditionally, so the untouched code path is byte-for-byte the same behaviour as
 * before this task, which is exactly what the Task 3.1 face-count gate depends on staying true.</p>
 *
 * <h2>TASK 3.4: FACE MERGE ({@code fields.mergeFaces})</h2>
 * <p>An explicit {@code mergeFaces=true} on the request makes {@link #meshColumn}'s body convert
 * its finished {@code pending} list into {@link FaceMerger.Quad}s, call {@link FaceMerger#merge},
 * and convert the result back — see the copy-in/copy-out block right after the block/fluid mesh
 * loop, and {@link FaceMerger}'s own class doc for the full algorithm and WHY that conversion
 * exists instead of {@link FaceMerger} touching {@link PendingQuad} directly (that type is
 * {@code private} to this class). Runs AFTER Task 3.3's cave-culling filter has already dropped
 * whatever faces it was going to drop, so the two options compose: merged face count with both
 * options on is always &le; the cullCaves-only face count. THIS CLASS'S PUBLIC SURFACE AND FIELDS
 * ARE UNCHANGED by this option, same deploy-window reasoning as Task 3.3. DEFAULT OFF: with
 * {@code mergeFaces} absent/false, {@link FaceMerger#isArmed} is false, the whole conversion block
 * never runs, and {@code merged} is simply {@code pending} itself — byte-identical to pre-3.4
 * behaviour, which is what proof gate (b) (the regression MD5 check) depends on. WRITE ORDER:
 * {@code mergeFaces=true} also joins the Task 3.3 write-order sequencer (see the {@code
 * lastArmedCullCaves() || lastArmedMergeFaces()} check further down in this method) — measured
 * live that merge's own per-column cost varies enough (a flat plain's greedy sweep vs. a single
 * isolated block) to surface the same pre-existing inter-column write race Task 3.3 found.</p>
 */
public final class StreamingWorldExporter
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Per-tick time budget for main-thread column snapshotting (panel M69: "start 5ms"). */
    private static final long TICK_BUDGET_NANOS = 5_000_000L;

    /** How often to emit a WORLD_EXPORT_PROGRESS envelope while a job is running (panel M68: "~1x/s"). */
    private static final long PROGRESS_INTERVAL_NANOS = 1_000_000_000L;

    /**
     * Max columns snapshotted-but-not-fully-written at once. Bounds live {@link ColumnSnapshot}
     * memory to roughly this many MB (see class-doc BACKPRESSURE) instead of holding the whole
     * export's worth of snapshots at once.
     */
    private static final int MAX_IN_FLIGHT = 24;

    /** Sanity cap so a wild request cannot queue an unbounded number of columns (64x64 chunks). */
    private static final int MAX_COLUMNS = 4096;

    /* ---- BakedQuad.getVertexData() layout — copied verbatim from WorldExporter's own bytecode-
     * verified constants (VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL, stride 8 ints/vertex).
     * Duplicated rather than referenced so this class never depends on WorldExporter's internals —
     * see class-doc for why. */
    private static final int VERTEX_STRIDE = 8;
    private static final int VERTEX_X = 0;
    private static final int VERTEX_Y = 1;
    private static final int VERTEX_Z = 2;
    private static final int VERTEX_U = 4;
    private static final int VERTEX_V = 5;

    /** Vertices per quad — MC always bakes quads. */
    private static final int QUAD_VERTICES = 4;

    /** "No tint" colour — same convention as WorldExporter's TINT_WHITE. */
    private static final int TINT_WHITE = 0xFFFFFF;

    /** Per-block tint-cache sentinel (a resolved colour is always 0..0xFFFFFF, never negative). */
    private static final int NOT_CACHED = -1;

    /** The one active streaming job, or null. Touched only from the main thread. */
    private static volatile Job activeJob;

    /** Last job's final tallies, kept around so {@link #status()} can report after completion. */
    private static volatile Job lastFinishedJob;

    /** Guards {@link #ensureTickRegistered()} so the Fabric event is registered at most once. */
    private static boolean tickRegistered;

    private StreamingWorldExporter()
    {
        /* static-only helper */
    }

    /**
     * Start a streaming export of the given region into {@code outDir}. MAIN THREAD ONLY (reads
     * {@code client.level}, and {@link ColumnSnapshot}'s constructor — called later, per column, also
     * from the main thread — asserts it).
     *
     * Task 3.3 note: cave culling is armed separately via {@code SkyReachability.armNextJob(boolean)},
     * called by {@code WorldExportBridge.export} BEFORE this method — see {@link SkyReachability}'s
     * own class doc "DEPLOY-WINDOW-FREE WIRING" section for why this stays a 3-arg method rather than
     * growing a {@code cullCaves} parameter (this class is already loaded in the live JVM this
     * session; a new parameter here would need a restart, a static flag on the brand-new
     * {@link SkyReachability} class does not).
     *
     * @return a failure payload (via {@code WorldExportBridge.payload}) if the job could not be
     *         started (already running, no world, region too large, I/O error) — the caller should
     *         send this as the immediate WORLD_EXPORT_RESULT. Returns {@code null} on successful
     *         start, meaning: no immediate reply — this class will send progress + the final result
     *         itself once the job completes.
     */
    public static synchronized JsonObject start(BlockPos min, BlockPos max, File outDir)
    {
        if (activeJob != null)
        {
            return WorldExportBridge.payload(WorldExporter.ExportResult.failure("a streaming export is already running"));
        }

        Minecraft client = Minecraft.getInstance();

        if (client == null || client.level == null)
        {
            return WorldExportBridge.payload(WorldExporter.ExportResult.failure("no world loaded (join a world before exporting)"));
        }

        int minX = Math.min(min.getX(), max.getX());
        int minY = Math.min(min.getY(), max.getY());
        int minZ = Math.min(min.getZ(), max.getZ());
        int maxX = Math.max(min.getX(), max.getX());
        int maxY = Math.max(min.getY(), max.getY());
        int maxZ = Math.max(min.getZ(), max.getZ());

        int minChunkX = SectionPos.blockToSectionCoord(minX);
        int maxChunkX = SectionPos.blockToSectionCoord(maxX);
        int minChunkZ = SectionPos.blockToSectionCoord(minZ);
        int maxChunkZ = SectionPos.blockToSectionCoord(maxZ);

        long totalColumnsLong = (long) (maxChunkX - minChunkX + 1) * (long) (maxChunkZ - minChunkZ + 1);

        if (totalColumnsLong > MAX_COLUMNS)
        {
            return WorldExportBridge.payload(WorldExporter.ExportResult.failure(
                "streaming region too large: " + totalColumnsLong + " columns (max " + MAX_COLUMNS + "); export a smaller box"));
        }

        if (!outDir.exists() && !outDir.mkdirs())
        {
            return WorldExportBridge.payload(WorldExporter.ExportResult.failure("could not create output directory: " + outDir.getAbsolutePath()));
        }

        File texDir = new File(outDir, "textures");

        if (!texDir.exists() && !texDir.mkdirs())
        {
            return WorldExportBridge.payload(WorldExporter.ExportResult.failure("could not create texture directory: " + texDir.getAbsolutePath()));
        }

        File objFile = new File(outDir, "world.obj");
        File mtlFile = new File(outDir, "world.mtl");
        BufferedWriter obj;

        try
        {
            obj = Files.newBufferedWriter(objFile.toPath(), StandardCharsets.UTF_8);
            obj.write("# Minecraft world export — BBS Blender bridge (STREAMING pipeline, Task 3.1)\n");
            obj.write("# region (mc): " + minX + " " + minY + " " + minZ + " .. " + maxX + " " + maxY + " " + maxZ + "\n");
            obj.write("# vertices are RAW, ABSOLUTE Minecraft world coordinates (Y-up), no axis conversion applied\n");
            obj.write("# import with bpy.ops.wm.obj_import defaults, same contract as the synchronous exporter\n");
            obj.write("mtllib " + mtlFile.getName() + "\n");
            obj.write("o mc_world\n");
        }
        catch (IOException e)
        {
            return WorldExportBridge.payload(WorldExporter.ExportResult.failure("io error: " + e.getMessage()));
        }

        int columns = (int) totalColumnsLong;
        ArrayDeque<int[]> pending = new ArrayDeque<>(columns);

        for (int cz = minChunkZ; cz <= maxChunkZ; cz++)
        {
            for (int cx = minChunkX; cx <= maxChunkX; cx++)
            {
                pending.add(new int[] { cx, cz });
            }
        }

        int workerCount = Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors() - 1));
        ExecutorService workers = Executors.newFixedThreadPool(workerCount, StreamingWorldExporter::newWorkerThread);

        Job job = new Job(client.level, minX, minY, minZ, maxX, maxY, maxZ, outDir, objFile, mtlFile, texDir, obj, pending, columns, workers);

        /* Task 3.5-streaming: fresh emissive-block accumulator for THIS job — see StreamMetaWriter's
         * class doc for why this lives on the brand-new class instead of a new field here (deploy-
         * window-free wiring, same convention as SkyReachability.resetWriteSequencer()). */
        StreamMetaWriter.armNextJob(client.level);

        /* Task 3.2: fresh chunk-coverage tally for THIS job — recorded per column in
         * processColumnOnMainThread (main thread, one isChunkLoaded probe per column, cheap) and
         * read back by WorldExportBridge.payload when maybeFinish sends the result. Same
         * deploy-window-free reasoning as ChunkCoverage's own class doc: ExportResult's shape does
         * not change, this class only gains a body line. */
        ChunkCoverage.armForExport();

        ensureTickRegistered();
        activeJob = job;

        LOGGER.info("[blenderbridge] streaming world export START: [{},{},{}]..[{},{},{}] ({} columns, {} workers, cullCaves={}, mergeFaces={}) -> {}",
            minX, minY, minZ, maxX, maxY, maxZ, columns, workerCount, SkyReachability.lastArmedCullCaves(), FaceMerger.lastArmedMergeFaces(), objFile.getAbsolutePath());

        return null;
    }

    /**
     * True while a streaming job is in flight. Added 2026-07-17 so {@code WorldExportBridge.export}
     * can check BEFORE touching any arm/reset state (see {@link SkyReachability#armNextJob}/{@link
     * SkyReachability#resetWriteSequencer}/{@code FaceMerger.armNextJob}) — those calls used to run
     * UNCONDITIONALLY for every request, including one this method's own {@code activeJob} check was
     * about to reject moments later inside {@link #start}. That ordering bug meant a request arriving
     * while a job was already mid-flight would still overwrite the running job's cullCaves/mergeFaces
     * flags and, worse, {@code resetWriteSequencer()} would WIPE the running job's still-buffered
     * write-sequencer state (see that method's doc) — even though the new request itself always got
     * correctly rejected. Symptom measured live: a determinism gate (2x identical export, expecting a
     * byte-diff of 0) flaked non-deterministically on an otherwise correct, previously-clean run,
     * traced to another request having reached {@code export} while the first job was still running.
     * Callers must check this FIRST and skip the arm/reset calls entirely when it is already true.
     */
    public static boolean isActive()
    {
        return activeJob != null;
    }

    /**
     * Current job progress (for dev/EXEC polling — the wire side gets the same numbers via
     * WORLD_EXPORT_PROGRESS). {@code {"active":bool, "done","total","blocks","faces","outOfRimReads"}}
     * — the last three fields are present for both an active job and (from {@link #lastFinishedJob})
     * the most recently finished one, absent only when neither exists yet.
     */
    public static JsonObject status()
    {
        JsonObject out = new JsonObject();
        Job job = activeJob;

        if (job != null)
        {
            out.addProperty("active", true);
            out.addProperty("done", job.columnsDone.get());
            out.addProperty("total", job.totalColumns);
            out.addProperty("blocks", job.blockCount.get());
            out.addProperty("faces", job.faceCount.get());
            out.addProperty("outOfRimReads", job.outOfRimReadsTotal.get());
            out.addProperty("cullCaves", SkyReachability.lastArmedCullCaves());
            out.addProperty("mergeFaces", FaceMerger.lastArmedMergeFaces());
            return out;
        }

        out.addProperty("active", false);
        Job last = lastFinishedJob;

        if (last != null)
        {
            out.addProperty("blocks", last.blockCount.get());
            out.addProperty("faces", last.faceCount.get());
            out.addProperty("outOfRimReads", last.outOfRimReadsTotal.get());
            out.addProperty("cullCaves", SkyReachability.lastArmedCullCaves());
            out.addProperty("mergeFaces", FaceMerger.lastArmedMergeFaces());
        }

        return out;
    }

    private static synchronized void ensureTickRegistered()
    {
        if (tickRegistered)
        {
            return;
        }

        tickRegistered = true;
        ClientTickEvents.END_CLIENT_TICK.register(StreamingWorldExporter::onClientTick);
    }

    private static Thread newWorkerThread(Runnable r)
    {
        Thread t = new Thread(r, "blenderbridge-world-export-worker");
        t.setDaemon(true);
        return t;
    }

    /** The registered per-tick hook. No-op when no job is active — cheap to leave registered forever. */
    private static void onClientTick(Minecraft client)
    {
        Job job = activeJob;

        if (job == null)
        {
            return;
        }

        if (client.level != job.world)
        {
            /* Player left the world / changed dimension mid-export — abort rather than snapshot a
             * world that is no longer current (panel N97-adjacent: a changed world mid-export is a
             * bigger discontinuity than normal ticking, must not be silently absorbed). */
            activeJob = null;
            abort(job, "world changed or unloaded during export");
            return;
        }

        long deadline = System.nanoTime() + TICK_BUDGET_NANOS;

        while (!job.pendingColumns.isEmpty() && job.inFlight.get() < MAX_IN_FLIGHT && System.nanoTime() < deadline)
        {
            int[] col = job.pendingColumns.poll();
            processColumnOnMainThread(job, client, col[0], col[1]);
        }

        maybeSendProgress(job);
        maybeFinish(job);
    }

    /** Main-thread half of one column: snapshot + block-entity export, then hand meshing to a worker. */
    private static void processColumnOnMainThread(Job job, Minecraft client, int cx, int cz)
    {
        /* Task 3.2: one isChunkLoaded probe per column, main thread — mirrors the alt-path's
         * per-block ChunkCoverage.record, but here the loop is already per-chunk-column, so no
         * internal dedup is even needed; any position inside the column resolves the same chunk. */
        ChunkCoverage.record(job.world, new BlockPos(cx * 16, job.world.getMinY(), cz * 16));

        ColumnSnapshot snapshot;

        try
        {
            snapshot = new ColumnSnapshot(job.world, cx, cz);
        }
        catch (RuntimeException e)
        {
            LOGGER.warn("[blenderbridge] streaming export: could not snapshot column ({},{}): {}", cx, cz, e.toString());
            job.columnsDone.incrementAndGet();
            return;
        }

        exportBlockEntitiesForColumn(job, client, cx, cz);

        job.inFlight.incrementAndGet();

        job.workers.submit(() ->
        {
            try
            {
                meshColumn(job, snapshot, cx, cz);
            }
            catch (Throwable t)
            {
                LOGGER.error("[blenderbridge] streaming export: mesh worker failed for column ({},{})", cx, cz, t);
            }
            finally
            {
                job.columnsDone.incrementAndGet();
                job.inFlight.decrementAndGet();
            }
        });
    }

    /**
     * Export block entities for one column directly off the live world (BER stays main-thread — panel
     * H22). Reuses {@link BlockEntityExporter#emit} completely unmodified: same signature, same
     * shared-writer/explicit-index-parameter convention the synchronous path already uses, just called
     * from here instead of from {@code WorldExporter.runExport}'s loop.
     *
     * <p><b>KNOWN LIMITATION, DELIBERATELY NOT FIXED HERE (found by Task 3.4/face-merge proving its
     * own determinism gate; investigated + reverted by Task 3.3, 2026-07-17):</b> this write is NOT
     * ordered relative to this SAME column's (worker-thread, {@link SkyReachability}-sequenced) mesh
     * write — it always runs immediately, synchronously, on the main thread, at column-pop time,
     * before that column's snapshot is even handed to a mesh worker. In a region WITH block entities,
     * that makes repeat cullCaves/mergeFaces exports content-identical (same block/face counts, same
     * geometry) but byte-DIFFERENT (different file order) — confirmed independently by three sessions
     * this day (Task 3.4, Task 3.1/streaming-export, Task 3.3).</p>
     *
     * <p><b>WHY THIS ISN'T A QUICK FIX:</b> a first attempt routed this whole method's write through
     * {@link SkyReachability#sequencedWrite} (same {@code columnSeq*2} scheme the mesh write uses at
     * {@code *2+1}) — compiles, and would fix the ordering, but is UNSAFE and was reverted before
     * landing: {@code sequencedWrite}'s drain runs a buffered action on WHICHEVER THREAD happens to
     * trigger that slot's cascade, which is not necessarily the thread that originally submitted it —
     * a WORKER thread finishing an unrelated earlier column can walk the cascade straight through this
     * column's deferred BE action and run it there. {@link BlockEntityExporter#emit} is documented
     * MAIN THREAD ONLY (its own class doc: "BERs demand it, and the atlas readback needs the GL
     * context" — {@code BlockEntityRenderer.render} and, on first sight of a new material, {@code
     * SpriteAtlasTexture.save()}'s GPU readback). Running it off-thread is a structural GL/BER-safety
     * violation, not a determinism nicety — and a perfectly still test world (this project's
     * correctness-testing convention) does NOT protect against it; only worker-vs-main-thread
     * scheduling luck would determine whether a given test run happens to trigger it.</p>
     *
     * <p><b>THE ACTUAL FIX (not done here — flagged, not attempted, given the stakes of an
     * off-thread-GL crash for a cosmetic byte-order gap):</b> split {@link BlockEntityExporter} into
     * an eager MAIN-THREAD capture step (BER render + material/atlas resolution, unchanged timing —
     * zero temporal shift, run exactly when this method is called today) that returns already-resolved
     * quad+material data with NO index assigned yet, and a separate, thread-agnostic, pure-text WRITE
     * step (only {@code v}/{@code vt}/{@code vn}/{@code f} line formatting, given explicit base
     * indices) that CAN safely go through {@code sequencedWrite}. That is a real, separate task
     * (touches GL-adjacent code neither this class nor {@link SkyReachability} owns) — out of scope
     * for a same-day fix layered on top of an already-crashed-once session. Not fixed; documented.</p>
     */
    private static void exportBlockEntitiesForColumn(Job job, Minecraft client, int cx, int cz)
    {
        LevelChunk chunk = job.world.getChunk(cx, cz);
        Map<BlockPos, BlockEntity> entities = chunk.getBlockEntities();

        if (entities.isEmpty())
        {
            return;
        }

        for (Map.Entry<BlockPos, BlockEntity> entry : entities.entrySet())
        {
            BlockPos pos = entry.getKey();

            if (pos.getX() < job.minX || pos.getX() > job.maxX
                || pos.getY() < job.minY || pos.getY() > job.maxY
                || pos.getZ() < job.minZ || pos.getZ() > job.maxZ)
            {
                /* Outside the requested box (a partial-column request clipped mid-chunk) — the
                 * synchronous scan would never have visited this position either. */
                continue;
            }

            synchronized (job.writerLock)
            {
                try
                {
                    int emitted = BlockEntityExporter.emit(client, job.world, pos, job.outDir, job.obj, job.vertexIndex, job.uvIndex, job.normalIndex);

                    job.vertexIndex += emitted * QUAD_VERTICES;
                    job.uvIndex += emitted * QUAD_VERTICES;
                    job.normalIndex += emitted;

                    if (emitted > 0)
                    {
                        job.blockCount.incrementAndGet();
                        job.faceCount.addAndGet(emitted);
                    }
                }
                catch (IOException e)
                {
                    job.ioFailure = e;
                }
            }
        }
    }

    /** This column's fixed position in the job's original enqueue order (0-based) — the SAME formula
     * {@code start} built {@code pendingColumns} in (cz outer, cx inner). Used as {@link #meshColumn}'s
     * write-sequencer key. NOT used for block-entity writes — see {@link
     * #exportBlockEntitiesForColumn}'s doc for why those stay unsequenced (a reverted attempt to
     * fold them in via {@code *2}/{@code *2+1} risked running {@link BlockEntityExporter#emit} off
     * the main thread). */
    private static int columnSeq(Job job, int cx, int cz)
    {
        int minChunkX = job.minX >> 4;
        int minChunkZ = job.minZ >> 4;
        int maxChunkX = job.maxX >> 4;
        int chunkWidth = maxChunkX - minChunkX + 1;

        return (cz - minChunkZ) * chunkWidth + (cx - minChunkX);
    }

    /**
     * WORKER THREAD: mesh one column's blocks + fluids against its (already-finished, immutable)
     * snapshot. Computes every quad into a private list with ZERO shared state touched — this is what
     * lets many columns mesh in true parallel — then writes the whole column out under
     * {@link Job#writerLock} in one shot ({@link #writePending}).
     */
    private static void meshColumn(Job job, ColumnSnapshot snapshot, int cx, int cz)
    {
        Minecraft client = Minecraft.getInstance();

        int colMinX = cx * ColumnSnapshot.COLUMN_SIZE;
        int colMinZ = cz * ColumnSnapshot.COLUMN_SIZE;
        int loX = Math.max(job.minX, colMinX);
        int hiX = Math.min(job.maxX, colMinX + ColumnSnapshot.COLUMN_SIZE - 1);
        int loZ = Math.max(job.minZ, colMinZ);
        int hiZ = Math.min(job.maxZ, colMinZ + ColumnSnapshot.COLUMN_SIZE - 1);
        int loY = Math.max(job.minY, snapshot.getMinY());
        int hiY = Math.min(job.maxY, snapshot.getMinY() + snapshot.getHeight() - 1);

        if (loX > hiX || loZ > hiZ || loY > hiY)
        {
            return;
        }

        /* Task 3.3: arm (or clear) this WORKER thread's active reachability flood-fill for this one
         * column — see SkyReachability's "DEPLOY-WINDOW-FREE WIRING" class-doc section for why this
         * is a static/ThreadLocal call rather than a new parameter on this (already-loaded) method.
         * When cullCaves is off, enterColumn clears the slot and every shouldCull/shouldCullFace
         * check below returns false unconditionally, so the untouched (default) behaviour is
         * byte-identical to before this task existed — the Task 3.1 face-count gate depends on that
         * staying true. The finally guarantees this worker thread never carries a stale reachability
         * instance into the NEXT column it happens to pick up from the pool. */
        SkyReachability.enterColumn(snapshot, job.minX, job.maxX, job.minZ, job.maxZ);

        try
        {
            List<PendingQuad> pending = new ArrayList<>();
            RandomSource random = RandomSource.create();

            /* PORT 26.2: die block-modelle kommen aus dem ModelManager statt aus dem
             * BlockRenderDispatcher (den es nicht mehr gibt). get(state) ist ein map-lookup,
             * also einmal je spalte geholt und nicht je block.
             *
             * Die teileliste wird WIEDERVERWENDET: eine variante kann mehrere teile liefern
             * (multipart: zaeune, redstone). collectParts leert sie selbst nicht -- meshBlock
             * tut es, direkt vor dem fuellen. */
            BlockStateModelSet blockModelle = client.getModelManager().getBlockStateModelSet();
            List<BlockStateModelPart> modelParts = new ArrayList<>();

            /* Ebenfalls einmal je spalte: der fluid-zeichner ist jetzt ein OBJEKT (fabric-api's
             * FluidRenderHandlerRegistry ist weg). Er haelt nur den FluidStateModelSet. */
            FluidRenderer fluids = new FluidRenderer(client.getModelManager().getFluidStateModelSet());
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            int blocksWithFaces = 0;

            for (int y = loY; y <= hiY; y++)
            {
                for (int z = loZ; z <= hiZ; z++)
                {
                    for (int x = loX; x <= hiX; x++)
                    {
                        pos.set(x, y, z);

                        BlockState state = snapshot.getBlockState(pos);

                        if (state.isAir())
                        {
                            continue;
                        }

                        int before = pending.size();

                        /* Fluid first, same as WorldExporter — a fluid's own render type is INVISIBLE
                         * (no baked model), so it must be handled before/independent of that guard. */
                        FluidState fluidState = state.getFluidState();

                        if (!fluidState.isEmpty())
                        {
                            meshFluid(client, fluids, snapshot, state, fluidState, pos, pending);
                        }

                        if (state.getRenderShape() != RenderShape.INVISIBLE)
                        {
                            meshBlock(client, snapshot, state, blockModelle.get(state), modelParts, pos.immutable(), random, state.getSeed(pos), pending);
                        }

                        boolean producedFaces = pending.size() > before;

                        if (producedFaces)
                        {
                            blocksWithFaces++;
                        }

                        /* Task 3.5-streaming emissive check (same one WorldExporter.exportRegion does
                         * in its scan loop, zero extra world read — see StreamMetaWriter's class doc),
                         * MOVED BEHIND the mesh/cull decision (W7-B2, end-review fund 4 co-gate:
                         * recordIfEmissive used to run BEFORE the cull verdict, so a cullCaves=true
                         * manifest listed every light of the UNculled region — 895 entries — while the
                         * culled OBJ no longer contained their geometry; Blender would place point
                         * lights inside sealed, geometry-less caves). With cullCaves ARMED, only a
                         * block that actually kept at least one face is recorded ("geometry-existent
                         * lights only"; note this also drops lava-lake INTERIOR cells, which emit no
                         * faces even unculled — they are enclosed by the recorded surface lava, so no
                         * visible light is lost). With cullCaves OFF the record is unconditional,
                         * byte-identical to the pre-W7 behaviour — the measured streaming==alt
                         * manifest gate depends on that staying true. */
                        if (producedFaces || !SkyReachability.lastArmedCullCaves())
                        {
                            StreamMetaWriter.recordIfEmissive(pos, state);
                        }
                    }
                }
            }

            job.blockCount.addAndGet(blocksWithFaces);
            job.outOfRimReadsTotal.addAndGet(snapshot.outOfRimReads());

            /* Task 3.4 (see FaceMerger's own class doc "WHY NOT REUSE StreamingWorldExporter
             * .PendingQuad": that type is private to THIS class, so FaceMerger declares its own
             * field-identical Quad and this body does the copy-in/copy-out around the merge call —
             * a body-only change, no signature/field anywhere is touched). Default off: when
             * FaceMerger.isArmed() is false, `merged` is just `pending` itself, unchanged — the
             * conversion loop never runs at all, so the untouched path stays byte-identical to
             * pre-3.4 behaviour (same gate discipline as Task 3.3's cullCaves default-off). */
            final List<PendingQuad> merged;

            if (FaceMerger.isArmed())
            {
                List<FaceMerger.Quad> quadsForMerge = new ArrayList<>(pending.size());

                for (int i = 0; i < pending.size(); i++)
                {
                    PendingQuad pq = pending.get(i);
                    FaceMerger.Quad q = new FaceMerger.Quad();

                    System.arraycopy(pq.x, 0, q.x, 0, QUAD_VERTICES);
                    System.arraycopy(pq.y, 0, q.y, 0, QUAD_VERTICES);
                    System.arraycopy(pq.z, 0, q.z, 0, QUAD_VERTICES);
                    System.arraycopy(pq.u, 0, q.u, 0, QUAD_VERTICES);
                    System.arraycopy(pq.v, 0, q.v, 0, QUAD_VERTICES);
                    q.nx = pq.nx;
                    q.ny = pq.ny;
                    q.nz = pq.nz;
                    q.spriteId = pq.spriteId;
                    q.frameWidth = pq.frameWidth;
                    q.frameHeight = pq.frameHeight;
                    q.tinted = pq.tinted;
                    q.tint = pq.tint;
                    q.emissive = pq.emissive;

                    quadsForMerge.add(q);
                }

                List<FaceMerger.Quad> mergedQuads = FaceMerger.merge(quadsForMerge);
                List<PendingQuad> rebuilt = new ArrayList<>(mergedQuads.size());

                for (int i = 0; i < mergedQuads.size(); i++)
                {
                    FaceMerger.Quad q = mergedQuads.get(i);
                    PendingQuad pq = new PendingQuad();

                    System.arraycopy(q.x, 0, pq.x, 0, QUAD_VERTICES);
                    System.arraycopy(q.y, 0, pq.y, 0, QUAD_VERTICES);
                    System.arraycopy(q.z, 0, pq.z, 0, QUAD_VERTICES);
                    System.arraycopy(q.u, 0, pq.u, 0, QUAD_VERTICES);
                    System.arraycopy(q.v, 0, pq.v, 0, QUAD_VERTICES);
                    pq.nx = q.nx;
                    pq.ny = q.ny;
                    pq.nz = q.nz;
                    pq.spriteId = q.spriteId;
                    pq.frameWidth = q.frameWidth;
                    pq.frameHeight = q.frameHeight;
                    pq.tinted = q.tinted;
                    pq.tint = q.tint;
                    pq.emissive = q.emissive;

                    rebuilt.add(pq);
                }

                merged = rebuilt;
            }
            else
            {
                merged = pending;
            }

            /* Task 3.3 determinism fix (measured live, see SkyReachability's "DETERMINISTIC WRITE
             * ORDER" class-doc section): cullCaves's per-column BFS cost varies enough with how much
             * cave a column has that the pre-existing "whichever worker finishes first writes first"
             * race (always there, in Task 3.1 too) actually surfaces as file-order nondeterminism.
             * Task 3.4 (measured live 2026-07-17, same symptom): mergeFaces's per-column greedy-sweep
             * cost ALSO varies enough with how much a column can merge (a flat plain's sweep is far
             * more work than a single isolated block) to surface the SAME pre-existing race — a
             * 16-chunk-region proof run showed content-identical (multiset-verified) but
             * byte-DIFFERENT OBJs across two mergeFaces=true runs before this fix. Reuses
             * SkyReachability's existing sequencer unchanged (it is a generic per-column ordering
             * primitive, not conceptually cullCaves-specific) rather than duplicating it in
             * FaceMerger — {@code WorldExportBridge.export} already calls {@code
             * SkyReachability.resetWriteSequencer()} unconditionally every job, so no additional
             * reset wiring is needed here. Only sequenced when EITHER option is armed — the
             * both-off path keeps its original, un-synchronised call (Task 3.1's own scope, not
             * touched here).
             *
             * <p>NOTE (investigated 2026-07-17): block-entity writes ({@code
             * exportBlockEntitiesForColumn}) deliberately do NOT share this sequencer — see that
             * method's doc for why (routing {@code BlockEntityExporter.emit} through it risked running
             * a main-thread/GL-bound call on a worker thread). A region with block entities can
             * therefore still show content-identical, byte-different repeat exports; documented as a
             * known limitation, not fixed here.</p> */
            if (SkyReachability.lastArmedCullCaves() || FaceMerger.lastArmedMergeFaces())
            {
                SkyReachability.sequencedWrite(columnSeq(job, cx, cz), () -> writePending(job, merged));
            }
            else
            {
                writePending(job, merged);
            }
        }
        finally
        {
            SkyReachability.exitColumn();
        }
    }

    /**
     * Baked-model quads for one block, direction-culled exactly like WorldExporter.emitBlock, PLUS
     * (Task 3.3) an optional sky-reachability gate via {@link SkyReachability#shouldCullFace}: a
     * direction's quads are skipped unless {@code pos} itself or its neighbour across that direction
     * is sky-reachable — "at least one of the two adjacent cells reachable" (plan wording).
     * {@code shouldCullFace} always returns {@code false} when cullCaves is off (see
     * {@link SkyReachability#enterColumn}), so this signature stays untouched from Task 3.1 and the
     * gate is a pure body addition.
     */
    private static void meshBlock(Minecraft client, ColumnSnapshot snapshot, BlockState state, net.minecraft.client.renderer.block.dispatch.BlockStateModel model, List<BlockStateModelPart> modelParts, BlockPos pos, RandomSource random, long seed, List<PendingQuad> out)
    {
        int[] tintCache = { NOT_CACHED, NOT_CACHED, NOT_CACHED, NOT_CACHED };

        /* PORT 26.2: die variantenwahl passiert EINMAL hier statt bei jedem getQuads-aufruf.
         * Der seed ist der POSITIONS-seed des blocks -- damit zieht ein mehrvarianten-block
         * (gras, steinziegel) dieselbe variante, die im spiel steht. Ein frischer zufall je
         * aufruf liesse den export von der ansicht abweichen, und nichts wuerde es melden.
         * (Vorher stand setSeed vor jedem der sieben getQuads-aufrufe; das war noetig, weil
         * jeder aufruf selbst wuerfelte.) */
        modelParts.clear();
        random.setSeed(seed);
        model.collectParts(random, modelParts);

        for (Direction direction : Direction.values())
        {
            /* PORT 26.2: shouldRenderFace nimmt den nachbar-ZUSTAND, nicht mehr welt+position.
             * Den lookup, den die methode bisher selbst machte, machen wir jetzt hier -- gegen
             * DEN SCHNAPPSCHUSS, nicht gegen die lebende welt, sonst laese dieser worker-thread
             * am hauptthread vorbei. */
            BlockPos nachbar = pos.relative(direction);

            if (!Block.shouldRenderFace(state, snapshot.getBlockState(nachbar), direction))
            {
                continue;
            }

            if (SkyReachability.shouldCullFace(pos, pos.relative(direction)))
            {
                /* Neither side of this face is open to the sky through this column's flood-fill —
                 * sealed-cave geometry, skip it (Task 3.3 "surface only" export option). */
                continue;
            }

            for (int p = 0; p < modelParts.size(); p++)
            {
                List<BakedQuad> quads = modelParts.get(p).getQuads(direction);

                for (int i = 0; i < quads.size(); i++)
                {
                    collectQuad(client, snapshot, state, pos, quads.get(i), tintCache, out);
                }
            }
        }

        /* Null-bucket quads (cross models: grass, flowers, torches) are never DIRECTION-culled, but
         * still get the Task 3.3 gate against the block's own cell — no "other side" concept for a
         * cross model, so pos itself is the only reachability signal available. */
        if (SkyReachability.shouldCull(pos))
        {
            return;
        }

        for (int p = 0; p < modelParts.size(); p++)
        {
            List<BakedQuad> general = modelParts.get(p).getQuads(null);

            for (int i = 0; i < general.size(); i++)
            {
                collectQuad(client, snapshot, state, pos, general.get(i), tintCache, out);
            }
        }
    }

    /**
     * Decode one BakedQuad (vertex layout copied verbatim from WorldExporter's bytecode-verified
     * constants) and resolve its tint THROUGH THE SNAPSHOT ({@code snapshot.getColor(...)}, called via
     * {@code BlockColors.getColor(state, snapshot, pos, tintIndex)}) — this is the whole reason
     * ColumnSnapshot implements BlockRenderView: the biome blend reads the snapshot's captured rim,
     * never the live world.
     */
    private static void collectQuad(Minecraft client, ColumnSnapshot snapshot, BlockState state, BlockPos pos, BakedQuad quad, int[] tintCache, List<PendingQuad> out)
    {
        /* PORT 26.2: BakedQuad ist ein RECORD -- kein int[]-vertexpuffer mehr, den man mit
         * festen offsets liest. Positionen und UVs kommen einzeln heraus, das material
         * (sprite + toenungsindex) sitzt in materialInfo. Die art, wie ein quad kaputt sein
         * kann, hat sich damit geaendert; die reaktion nicht -- melden statt geometrie zu
         * schreiben, die auf keine textur zeigt. */
        BakedQuad.MaterialInfo info = quad.materialInfo();

        if (info == null || info.sprite() == null)
        {
            LOGGER.warn("[blenderbridge] streaming export: skipping quad without material/sprite at {}", pos);
            return;
        }

        TextureAtlasSprite sprite = info.sprite();
        float[] xs = new float[QUAD_VERTICES];
        float[] ys = new float[QUAD_VERTICES];
        float[] zs = new float[QUAD_VERTICES];
        float[] us = new float[QUAD_VERTICES];
        float[] vs = new float[QUAD_VERTICES];

        for (int i = 0; i < QUAD_VERTICES; i++)
        {
            org.joml.Vector3fc position = quad.position(i);
            long packedUv = quad.packedUV(i);

            xs[i] = position.x() + pos.getX();
            ys[i] = position.y() + pos.getY();
            zs[i] = position.z() + pos.getZ();

            /* UVPair.unpackU/unpackV ist nicht geraten: VertexConsumer.putBlockBakedQuad ruft
             * im bytecode genau diese beiden methoden auf packedUV(i) auf. Ergebnis sind wie
             * bisher ATLAS-UVs, die buildPendingQuad ins sprite renormalisiert. */
            us[i] = UVPair.unpackU(packedUv);
            vs[i] = UVPair.unpackV(packedUv);
        }

        /* PORT 26.2: der toenungsindex sitzt in materialInfo. isTinted() ist das gate -- nur
         * wenn es true sagt, ist tintIndex() gemeint; sonst gilt weiterhin -1 = ungetoent. */
        int tintIndex = info.isTinted() ? info.tintIndex() : -1;
        int tint;

        if (tintIndex < 0)
        {
            tint = TINT_WHITE;
        }
        else if (tintIndex < tintCache.length)
        {
            if (tintCache[tintIndex] == NOT_CACHED)
            {
                tintCache[tintIndex] = toenung(client, state, snapshot, pos, tintIndex);
            }

            tint = tintCache[tintIndex];
        }
        else
        {
            tint = toenung(client, state, snapshot, pos, tintIndex);
        }

        out.add(buildPendingQuad(xs, ys, zs, us, vs, sprite, quad.direction(), tintIndex >= 0, tint, false));
    }

    /**
     * Fluid quads for one block via vanilla's own fluid renderer, captured through
     * {@link ObjVertexConsumer} (reused unmodified — it is already package-private and has no
     * ClientWorld coupling) — same approach as WorldExporter.emitFluid, against the snapshot instead
     * of the live world.
     *
     * <p>Task 3.3 gate: a fluid has no baked-model direction loop to hang the "two adjacent cells"
     * check on, so the two cells checked ({@link SkyReachability#shouldCullFace}) are the fluid's own
     * position and straight up (the natural "other side" of a horizontal fluid surface — the block a
     * viewer above the water would stand in). Always {@code false} (never cull) when cullCaves is
     * off, so this signature stays untouched from Task 3.1 and the gate is a pure body addition.</p>
     */
    private static void meshFluid(Minecraft client, FluidRenderer fluids, ColumnSnapshot snapshot, BlockState state, FluidState fluidState, BlockPos pos, List<PendingQuad> out)
    {
        if (SkyReachability.shouldCullFace(pos, pos.above()))
        {
            return;
        }

        /* PORT 26.2: statt fabric-api's FluidRenderHandlerRegistry fragen wir das spiel selbst.
         * FluidStateModelSet.get liefert nie null -- fuer ein unbekanntes fluid gibt es ein
         * standardmodell. Der alte "kein handler"-zweig hat damit keinen ersatz und ist
         * entfallen; er kann nichts mehr still verschlucken, weil es den fall nicht gibt. */
        FluidModel fluidModel = client.getModelManager().getFluidStateModelSet().get(fluidState);

        if (fluidModel == null)
        {
            LOGGER.warn("[blenderbridge] no fluid model for {} — skipping fluid at {}", fluidState.getType(), pos);
            return;
        }

        /* {still, flow} oder {still, flow, overlay} -- reihenfolge und laenge sind ABSICHTLICH
         * die des alten fabric-handlers, damit ObjVertexConsumer unveraendert bleibt. Welches
         * sprite ein quad benutzt hat, ist nur an seinen UVs erkennbar; genau das loest der
         * consumer auf. */
        TextureAtlasSprite still = fluidModel.stillMaterial() == null ? null : fluidModel.stillMaterial().sprite();
        TextureAtlasSprite flowing = fluidModel.flowingMaterial() == null ? null : fluidModel.flowingMaterial().sprite();
        TextureAtlasSprite overlay = fluidModel.overlayMaterial() == null ? null : fluidModel.overlayMaterial().sprite();

        if (still == null || flowing == null)
        {
            LOGGER.warn("[blenderbridge] fluid model for {} has no still/flow sprite — skipping fluid at {}", fluidState.getType(), pos);
            return;
        }

        TextureAtlasSprite[] sprites = overlay == null
            ? new TextureAtlasSprite[] { still, flowing }
            : new TextureAtlasSprite[] { still, flowing, overlay };

        ObjVertexConsumer consumer = new ObjVertexConsumer(sprites, pos.getX() & ~15, pos.getY() & ~15, pos.getZ() & ~15);

        /* PORT 26.2: der aufruf heisst jetzt tesselate und bekommt statt EINES consumers ein
         * Output-interface, das je chunk-layer einen consumer liefert. Wir geben fuer JEDEN
         * layer denselben zurueck -- wir wollen alle flaechen, nicht nach layer getrennt.
         * Der section-lokale koordinatenraum (& 15) gilt unveraendert, deshalb bleibt der
         * section-ursprung, den der consumer oben bekommt, richtig. */
        fluids.tesselate(snapshot, pos, layer -> consumer, state, fluidState);

        List<ObjVertexConsumer.FluidQuad> quads = consumer.finish();

        if (quads.isEmpty())
        {
            return;
        }

        /* PORT 26.2: der reine biom-tint kommt jetzt aus der tint-quelle des fluid-modells --
         * dieselbe, die FluidRenderer in seine ecken multipliziert. Ersetzt
         * FluidRenderHandler.getFluidColor. */
        int rawColor = fluidModel.tintSource() == null ? -1 : fluidModel.tintSource().colorInWorld(state, snapshot, pos);
        boolean tinted = rawColor != -1;
        boolean emissive = state.getLightEmission() > 0;

        for (int i = 0; i < quads.size(); i++)
        {
            ObjVertexConsumer.FluidQuad q = quads.get(i);

            out.add(buildPendingQuad(q.xs, q.ys, q.zs, q.us, q.vs, q.sprite, null, tinted, rawColor & 0xFFFFFF, emissive));
        }
    }

    /**
     * Der Biomton fuer eine Toenungsschicht dieses Blocks.
     *
     * <p>PORT 26.2: {@code BlockColors.getColor(state, welt, pos, index)} ist ersatzlos weg. Der
     * zweistufige Nachfolger ist {@code getTintSource(state, index)} ->
     * {@code BlockTintSource.colorInWorld(state, welt, pos)}. Gelesen wird gegen den
     * SCHNAPPSCHUSS, nicht gegen die lebende Welt -- dieser Aufruf laeuft auf einem
     * Arbeitsthread, und der Biom-Verlauf greift auf Nachbarn zu.
     *
     * <p>Ohne diese Farbe waeren Gras, Laub und Ranken grau: Minecraft liefert diese Texturen
     * in Graustufen und multipliziert den Biomton erst beim Zeichnen dazu.
     */
    private static int toenung(Minecraft client, BlockState state, ColumnSnapshot snapshot, BlockPos pos, int tintIndex)
    {
        net.minecraft.client.color.block.BlockTintSource quelle = client.getBlockColors().getTintSource(state, tintIndex);

        return quelle == null ? TINT_WHITE : quelle.colorInWorld(state, snapshot, pos) & 0xFFFFFF;
    }

    /** Renormalise atlas UV against the resolved sprite + compute the geometric normal, once per quad. */
    private static PendingQuad buildPendingQuad(float[] xs, float[] ys, float[] zs, float[] us, float[] vs, TextureAtlasSprite sprite, Direction fallbackFace, boolean tinted, int tint, boolean emissive)
    {
        PendingQuad pq = new PendingQuad();
        pq.spriteId = sprite.contents().name();
        pq.frameWidth = sprite.contents().width();
        pq.frameHeight = sprite.contents().height();
        pq.tinted = tinted;
        pq.tint = tint;
        pq.emissive = emissive;

        for (int i = 0; i < QUAD_VERTICES; i++)
        {
            pq.x[i] = xs[i];
            pq.y[i] = ys[i];
            pq.z[i] = zs[i];
            pq.u[i] = normalise(us[i], sprite.getU0(), sprite.getU1());
            pq.v[i] = 1F - normalise(vs[i], sprite.getV0(), sprite.getV1());
        }

        computeNormal(pq, fallbackFace);

        return pq;
    }

    private static void computeNormal(PendingQuad pq, Direction fallbackFace)
    {
        float ax = pq.x[1] - pq.x[0];
        float ay = pq.y[1] - pq.y[0];
        float az = pq.z[1] - pq.z[0];
        float bx = pq.x[2] - pq.x[0];
        float by = pq.y[2] - pq.y[0];
        float bz = pq.z[2] - pq.z[0];

        float nx = ay * bz - az * by;
        float ny = az * bx - ax * bz;
        float nz = ax * by - ay * bx;

        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);

        if (length < 1.0E-6F)
        {
            if (fallbackFace != null)
            {
                nx = fallbackFace.getStepX();
                ny = fallbackFace.getStepY();
                nz = fallbackFace.getStepZ();
            }
            else
            {
                nx = 0F;
                ny = 1F;
                nz = 0F;
            }
        }
        else
        {
            nx /= length;
            ny /= length;
            nz /= length;
        }

        pq.nx = nx;
        pq.ny = ny;
        pq.nz = nz;
    }

    /**
     * WRITE phase for one column: acquire {@link Job#writerLock} ONCE, assign OBJ indices, write the
     * (pre-computed) vertex/uv/normal lines and bucket the face lines per material — mirrors
     * WorldExporter's own "buffer per material, flush usemtl groups at the very end" layout.
     */
    private static void writePending(Job job, List<PendingQuad> pending)
    {
        if (pending.isEmpty())
        {
            return;
        }

        synchronized (job.writerLock)
        {
            try
            {
                for (int i = 0; i < pending.size(); i++)
                {
                    PendingQuad pq = pending.get(i);
                    Material material = materialFor(pq, job.materials);

                    for (int v = 0; v < QUAD_VERTICES; v++)
                    {
                        job.obj.write("v " + fmt(pq.x[v]) + " " + fmt(pq.y[v]) + " " + fmt(pq.z[v]) + "\n");
                    }

                    for (int v = 0; v < QUAD_VERTICES; v++)
                    {
                        job.obj.write("vt " + fmt(pq.u[v]) + " " + fmt(pq.v[v]) + "\n");
                    }

                    job.obj.write("vn " + fmt(pq.nx) + " " + fmt(pq.ny) + " " + fmt(pq.nz) + "\n");

                    int vBase = job.vertexIndex;
                    int uvBase = job.uvIndex;
                    int nBase = job.normalIndex;

                    job.vertexIndex += QUAD_VERTICES;
                    job.uvIndex += QUAD_VERTICES;
                    job.normalIndex += 1;

                    StringBuilder faces = material.faces;
                    faces.append("f");

                    for (int v = 0; v < QUAD_VERTICES; v++)
                    {
                        faces.append(' ').append(vBase + v).append('/').append(uvBase + v).append('/').append(nBase);
                    }

                    faces.append('\n');
                }

                job.faceCount.addAndGet(pending.size());
            }
            catch (IOException e)
            {
                job.ioFailure = e;
            }
        }
    }

    private static Material materialFor(PendingQuad pq, Map<String, Material> materials)
    {
        String textureName = safeName(pq.spriteId);
        String name = pq.tinted ? textureName + "_" + String.format(Locale.ROOT, "%06X", pq.tint) : textureName;
        Material material = materials.get(name);

        if (material == null)
        {
            material = new Material(pq.spriteId, textureName, pq.tinted ? pq.tint : TINT_WHITE, pq.emissive, pq.frameWidth, pq.frameHeight);
            materials.put(name, material);
        }

        return material;
    }

    private static void maybeSendProgress(Job job)
    {
        long now = System.nanoTime();

        if (now - job.lastProgressNanos < PROGRESS_INTERVAL_NANOS)
        {
            return;
        }

        job.lastProgressNanos = now;

        long seq = BlenderBridgeClient.STATE.nextOutboundSeq();
        JsonObject env = BridgeProtocol.envelope(BridgeProtocol.TYPE_WORLD_EXPORT_PROGRESS, BridgeProtocol.UUID_WORLD, seq, BridgeProtocol.ORIGIN_BBS);
        JsonObject f = BridgeProtocol.getFields(env);

        f.addProperty("done", job.columnsDone.get());
        f.addProperty("total", job.totalColumns);
        f.addProperty("blocks", job.blockCount.get());
        f.addProperty("verts", job.vertexIndex - 1);

        BlenderBridgeClient.SERVER.send(env);
    }

    private static void maybeFinish(Job job)
    {
        if (!job.pendingColumns.isEmpty() || job.inFlight.get() > 0 || job.finishing)
        {
            return;
        }

        job.finishing = true;
        activeJob = null;

        if (job.ioFailure != null)
        {
            LOGGER.error("[blenderbridge] streaming world export: write failure", job.ioFailure);

            try
            {
                job.obj.close();
            }
            catch (IOException ignored)
            {
                /* Already broken; nothing more to do. */
            }

            sendResult(WorldExportBridge.payload(WorldExporter.ExportResult.failure("io error: " + job.ioFailure.getMessage())));
            job.workers.shutdown();
            lastFinishedJob = job;
            return;
        }

        try
        {
            for (Map.Entry<String, Material> entry : job.materials.entrySet())
            {
                job.obj.write("usemtl " + entry.getKey() + "\n");
                job.obj.write(entry.getValue().faces.toString());
            }

            job.obj.close();

            writeMtl(job.mtlFile, job.materials);

            /* W7-B2: animated-textures section for the streaming manifest — reuses the alt-path's
             * TextureStripExporter hand-off UNCHANGED (armForExport fills the static holder here;
             * StreamMetaWriter.write's body calls appendAnimatedJson, which drains + clears it in
             * its own finally). THREAD: this runs on the MAIN thread (onClientTick -> maybeFinish;
             * measured live at the W7-B2 gate via the log lines' thread name) — the same thread the
             * alt-path arms on (render thread) and the same thread extractTextures below already
             * does its resource-manager reads on, so the resource access needs NO extra thread hop.
             * TreeMap, NOT the alt-path's LinkedHashMap: job.materials' insertion order here is
             * worker-ARRIVAL order (only deterministic when the write-sequencer is armed), and
             * world_meta.json carries the pipeline's byte/MD5 determinism guarantee (StreamMetaWriter
             * "ORDERING" doc) — sorting by textureName keeps the animated array reproducible
             * run-to-run; the cross-path gate compares order-insensitively, exactly like the
             * emissive-list gate already does. */
            Map<String, Identifier> spriteIdByMaterial = new java.util.TreeMap<String, Identifier>();

            for (Map.Entry<String, Material> entry : job.materials.entrySet())
            {
                spriteIdByMaterial.put(entry.getValue().textureName, entry.getValue().spriteId);
            }

            TextureStripExporter.armForExport(Minecraft.getInstance(), job.outDir, spriteIdByMaterial);

            /* Task 3.5-streaming: same lighting manifest the alt-path writes via WorldExporter
             * .writeWorldMeta, now for this pipeline too — see StreamMetaWriter's class doc. Written
             * after the OBJ/MTL are both finalized, same call-site convention as the alt-path (right
             * after writeMtl, before extractTextures). A failure here must not lose an otherwise
             * successful export, so it is caught and logged rather than propagated into ioFailure. */
            try
            {
                StreamMetaWriter.write(job.outDir, job.world, job.minX, job.minY, job.minZ, job.maxX, job.maxY, job.maxZ);
            }
            catch (IOException e)
            {
                LOGGER.error("[blenderbridge] streaming world export: world_meta.json write failed", e);
            }

            int textures = extractTextures(Minecraft.getInstance(), job.materials, job.texDir);

            WorldExporter.ExportResult result = WorldExporter.ExportResult.success(
                portablePath(job.objFile), portablePath(job.mtlFile), portablePath(job.texDir), job.blockCount.get(), job.faceCount.get());

            LOGGER.info("[blenderbridge] streaming world export DONE: {} blocks, {} faces, {} materials, {} textures, outOfRimReads={} -> {}",
                job.blockCount.get(), job.faceCount.get(), job.materials.size(), textures, job.outOfRimReadsTotal.get(), job.objFile.getAbsolutePath());

            sendResult(WorldExportBridge.payload(result));
        }
        catch (IOException e)
        {
            LOGGER.error("[blenderbridge] streaming world export finalize failed", e);
            sendResult(WorldExportBridge.payload(WorldExporter.ExportResult.failure("finalize io error: " + e.getMessage())));
        }
        finally
        {
            job.workers.shutdown();
            lastFinishedJob = job;
        }
    }

    private static void abort(Job job, String reason)
    {
        job.workers.shutdownNow();

        try
        {
            job.obj.close();
        }
        catch (IOException ignored)
        {
            /* Best-effort close on abort. */
        }

        sendResult(WorldExportBridge.payload(WorldExporter.ExportResult.failure(reason)));
        lastFinishedJob = job;
    }

    private static void sendResult(JsonObject payload)
    {
        /* End-review fix 2 (request correlation): the asynchronous job result must echo the
         * requestId its WORLD_EXPORT carried, or the add-on cannot tell it from a broadcast of
         * somebody else's export — WorldExportBridge armed ExportCorrelation for THIS job (after
         * the isActive() guards, so no rejected request can have clobbered it). Empty = the request
         * carried no id (old add-on/EXEC caller) -> stamp nothing, pre-correlation reply shape.
         * Body-only edit on this already-loaded class, same deploy-window discipline as ever. */
        String requestId = ExportCorrelation.currentRequestId();

        if (!requestId.isEmpty())
        {
            payload.addProperty("requestId", requestId);
        }

        /* End-review finding 5 teardown: sendResult is the single terminal call of EVERY job end
         * (success, io-failure, finalize-failure, abort). write() already clears its own refs in
         * its finally, but the failure/abort paths never reach write() — without this line an
         * aborted job pins its ClientWorld (plus the emissive queue) until the NEXT streaming
         * export arms. armNextJob(null) reuses the existing surface: fresh queue, no world ref. */
        StreamMetaWriter.armNextJob(null);

        long seq = BlenderBridgeClient.STATE.nextOutboundSeq();
        BlenderBridgeClient.SERVER.send(BridgeProtocol.worldExportResult(seq, payload));
    }

    /* ==================== material / MTL / texture bookkeeping (mirrors WorldExporter) ==================== */

    private static void writeMtl(File mtlFile, Map<String, Material> materials) throws IOException
    {
        BufferedWriter mtl = Files.newBufferedWriter(mtlFile.toPath(), StandardCharsets.UTF_8);

        try
        {
            mtl.write("# Minecraft world export — BBS Blender bridge (streaming pipeline)\n");
            mtl.write("# Kd = biome tint (multiplied with the greyscale texture, as MC does); white = untinted\n");
            mtl.write("# Ke = emission, written for glowing surfaces (lava) only\n");

            for (Map.Entry<String, Material> entry : materials.entrySet())
            {
                Material material = entry.getValue();
                String texture = material.textureName;

                mtl.write("\nnewmtl " + entry.getKey() + "\n");
                mtl.write("Ka 1.000 1.000 1.000\n");
                mtl.write("Kd " + fmtColor(material.tint) + "\n");
                mtl.write("Ks 0.000 0.000 0.000\n");

                if (material.emissive)
                {
                    mtl.write("Ke " + fmtColor(material.tint) + "\n");
                }

                mtl.write("d 1.0\n");
                mtl.write("illum 1\n");
                mtl.write("map_Kd textures/" + texture + ".png\n");
                mtl.write("map_d textures/" + texture + ".png\n");

                if (material.emissive)
                {
                    mtl.write("map_Ke textures/" + texture + ".png\n");
                }
            }
        }
        finally
        {
            mtl.close();
        }
    }

    private static int extractTextures(Minecraft client, Map<String, Material> materials, File texDir)
    {
        int written = 0;
        Set<String> done = new HashSet<>();

        for (Map.Entry<String, Material> entry : materials.entrySet())
        {
            Material material = entry.getValue();

            if (!done.add(material.textureName))
            {
                continue;
            }

            Identifier spriteId = material.spriteId;
            Identifier resourceId = Identifier.fromNamespaceAndPath(spriteId.getNamespace(), "textures/" + spriteId.getPath() + ".png");
            File target = new File(texDir, material.textureName + ".png");

            try
            {
                Optional<Resource> resource = client.getResourceManager().getResource(resourceId);

                if (resource.isEmpty())
                {
                    LOGGER.warn("[blenderbridge] streaming export: texture not found in resources: {}", resourceId);
                    continue;
                }

                extractTexture(resource.get(), material, target);
                written++;
            }
            catch (IOException e)
            {
                LOGGER.warn("[blenderbridge] streaming export: could not extract texture {}: {}", resourceId, e.toString());
            }
        }

        return written;
    }

    private static void extractTexture(Resource resource, Material material, File target) throws IOException
    {
        if (material.frameWidth > 0 && material.frameHeight > 0)
        {
            InputStream probe = resource.open();
            NativeImage image = null;

            try
            {
                image = NativeImage.read(probe);

                if (image.getWidth() != material.frameWidth || image.getHeight() != material.frameHeight)
                {
                    if (image.getWidth() >= material.frameWidth && image.getHeight() >= material.frameHeight)
                    {
                        writeFirstFrame(image, material.frameWidth, material.frameHeight, target);
                        return;
                    }

                    LOGGER.warn("[blenderbridge] streaming export: sprite {} declares a {}x{} frame but its texture is only {}x{} — copying uncropped",
                        material.spriteId, material.frameWidth, material.frameHeight, image.getWidth(), image.getHeight());
                }
            }
            finally
            {
                if (image != null)
                {
                    image.close();
                }

                probe.close();
            }
        }

        InputStream stream = resource.open();

        try
        {
            Files.copy(stream, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        finally
        {
            stream.close();
        }
    }

    private static void writeFirstFrame(NativeImage strip, int frameWidth, int frameHeight, File target) throws IOException
    {
        NativeImage frame = new NativeImage(NativeImage.Format.RGBA, frameWidth, frameHeight, false);

        try
        {
            for (int y = 0; y < frameHeight; y++)
            {
                for (int x = 0; x < frameWidth; x++)
                {
                    /* 26.2: get/setPixelRGBA heissen jetzt get/setPixel. Der Name trug schon
                     * vorher; die Zahlen lagen als ABGR im Speicher. Hier wird Pixel fuer Pixel
                     * zwischen zwei NativeImage kopiert, also gilt dieselbe Konvention auf
                     * beiden Seiten -- setPixelABGR waere hier falsch, das dreht um. */
                    frame.setPixel(x, y, strip.getPixel(x, y));
                }
            }

            frame.writeToFile(target);
        }
        finally
        {
            frame.close();
        }
    }

    private static String safeName(Identifier id)
    {
        String raw = id.getNamespace() + "_" + id.getPath();
        StringBuilder builder = new StringBuilder(raw.length());

        for (int i = 0; i < raw.length(); i++)
        {
            char c = raw.charAt(i);
            builder.append(Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.' ? c : '_');
        }

        return builder.toString();
    }

    private static String fmtColor(int color)
    {
        float r = ((color >> 16) & 0xFF) / 255F;
        float g = ((color >> 8) & 0xFF) / 255F;
        float b = (color & 0xFF) / 255F;

        return String.format(Locale.ROOT, "%.3f %.3f %.3f", r, g, b);
    }

    private static String fmt(float value)
    {
        return String.format(Locale.ROOT, "%.5f", value);
    }

    private static float normalise(float value, float min, float max)
    {
        float span = max - min;

        if (Math.abs(span) < 1.0E-9F)
        {
            return 0F;
        }

        return (value - min) / span;
    }

    private static String portablePath(File file)
    {
        return file.getAbsolutePath().replace('\\', '/');
    }

    /* ==================== data holders ==================== */

    /** One in-flight streaming export. All mutable counters not marked otherwise are guarded by {@link #writerLock}. */
    private static final class Job
    {
        final ClientLevel world;
        final int minX;
        final int minY;
        final int minZ;
        final int maxX;
        final int maxY;
        final int maxZ;
        final File outDir;
        final File objFile;
        final File mtlFile;
        final File texDir;
        final BufferedWriter obj;
        final Object writerLock = new Object();
        final Map<String, Material> materials = new LinkedHashMap<>();

        /** GUARDED BY writerLock. */
        int vertexIndex = 1;
        /** GUARDED BY writerLock. */
        int uvIndex = 1;
        /** GUARDED BY writerLock. */
        int normalIndex = 1;

        volatile Throwable ioFailure;

        /** Main-thread-only queue: populated once at start(), polled only from onClientTick. */
        final ArrayDeque<int[]> pendingColumns;
        final int totalColumns;
        final ExecutorService workers;

        final AtomicInteger columnsDone = new AtomicInteger();
        final AtomicInteger inFlight = new AtomicInteger();
        final AtomicInteger blockCount = new AtomicInteger();
        final AtomicInteger faceCount = new AtomicInteger();
        final AtomicInteger outOfRimReadsTotal = new AtomicInteger();

        /** Main-thread-only. */
        long lastProgressNanos;
        /** Main-thread-only. */
        boolean finishing;

        Job(ClientLevel world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, File outDir, File objFile, File mtlFile, File texDir,
            BufferedWriter obj, ArrayDeque<int[]> pendingColumns, int totalColumns, ExecutorService workers)
        {
            this.world = world;
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
            this.outDir = outDir;
            this.objFile = objFile;
            this.mtlFile = mtlFile;
            this.texDir = texDir;
            this.obj = obj;
            this.pendingColumns = pendingColumns;
            this.totalColumns = totalColumns;
            this.workers = workers;
        }
    }

    /** One OBJ material: sprite + tint, mirrors WorldExporter.Material's key convention. */
    private static final class Material
    {
        final Identifier spriteId;
        final String textureName;
        final int tint;
        final boolean emissive;
        final int frameWidth;
        final int frameHeight;
        final StringBuilder faces = new StringBuilder();

        Material(Identifier spriteId, String textureName, int tint, boolean emissive, int frameWidth, int frameHeight)
        {
            this.spriteId = spriteId;
            this.textureName = textureName;
            this.tint = tint;
            this.emissive = emissive;
            this.frameWidth = frameWidth;
            this.frameHeight = frameHeight;
        }
    }

    /** One computed-but-not-yet-written quad: everything writePending needs, with zero shared state. */
    private static final class PendingQuad
    {
        final float[] x = new float[QUAD_VERTICES];
        final float[] y = new float[QUAD_VERTICES];
        final float[] z = new float[QUAD_VERTICES];
        final float[] u = new float[QUAD_VERTICES];
        final float[] v = new float[QUAD_VERTICES];
        float nx;
        float ny;
        float nz;
        Identifier spriteId;
        int frameWidth;
        int frameHeight;
        boolean tinted;
        int tint;
        boolean emissive;
    }
}
