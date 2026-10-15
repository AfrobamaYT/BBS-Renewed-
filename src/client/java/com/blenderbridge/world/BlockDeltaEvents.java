// Coded by Afrobama/Claude — Blender<->BBS live-sync bridge (plan v2 task 3.6 v2: event-driven delta).
package com.blenderbridge.world;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.EmptyLevelChunk;

/**
 * BlockDeltaEvents — the notify bridge between {@code ClientWorldBlockDeltaMixin} (task 3.6 v2,
 * event-driven block delta) and {@link BlockDeltaTracker}'s v1 poll apparatus. The mixin calls
 * {@link #notifyExternal} the instant the client applies a block change; {@link #flush} (one
 * body line in {@code BlockDeltaTracker.tick()}) diffs the notified positions against the SAME
 * snapshot the sweep uses and pushes them through the SAME {@code BLOCK_DELTA} wire path — so
 * event-found and sweep-found changes are indistinguishable to the addon, and a change reported
 * by the event is never re-reported by the sweep (the snapshot is adopted here).
 *
 * <h2>⛔ BOOT-PFLICHTIG — NOTHING HERE RUNS TODAY (W8-B3, 2026-07-17)</h2>
 * <p>This class is committed but DORMANT: it is only ever called by (a) the new mixin — mixins
 * weave at class-load, i.e. next boot — and (b) the one new line in {@code
 * BlockDeltaTracker.tick()}, whose new body is likewise only live after the next boot (nothing
 * was hotswapped in this task; the running JVM still executes the ffc7c5f tick body). Until that
 * boot, W7-B4's budgeted poll is the delta path and keeps working unchanged. Honest status:
 * compile-gate + json-gate only, NO runtime gate — the runtime proof happens in the activation
 * window below.</p>
 *
 * <h2>WHY A NEW CLASS AND NOT A METHOD ON BlockDeltaTracker</h2>
 * <p>{@code BlockDeltaTracker} is LOADED in the running JVM (describe()-probes, W7-B4 re-swap).
 * Adding methods to a loaded class via DCEVM happened to work twice today, but it is exactly the
 * risk band of the {@code <clinit>}-trap (dossier) — and the fleet force-syncs classes via
 * hotswap before gates, so a committed add-method WOULD eventually travel through a redefine.
 * A brand-new class carries zero of that risk; the tracker itself only gets sanctioned edits
 * (private→package-private widening + one body line), both proven hotswap-safe shapes.</p>
 *
 * <h2>MEASURED FUNNEL (CFR 0.152 against this project's mapped jar, yarn 1.21.1+build.3)</h2>
 * <p>Which methods see a client-side block change (the reason the mixin needs TWO hooks):</p>
 * <ul>
 *   <li>{@code BlockUpdateS2CPacket} → {@code ClientPlayNetworkHandler.onBlockUpdate} →
 *       {@code ClientWorld.handleBlockUpdate} (single server update).</li>
 *   <li>{@code ChunkDeltaUpdateS2CPacket} → {@code onChunkDeltaUpdate} → {@code
 *       handleBlockUpdate} once PER position (multi-block server update, e.g. piston/explosion —
 *       and the burst reason flush batches per tick instead of pushing per notify).</li>
 *   <li>{@code handleBlockUpdate} calls {@code super.setBlockState(...)} — an INVOKESPECIAL that
 *       BYPASSES the {@code ClientWorld.setBlockState} override. A setBlockState-only mixin
 *       would MISS every server-driven change; hence the handleBlockUpdate hook.</li>
 *   <li>Local prediction (player place/break via {@code ClientPlayerInteractionManager}/{@code
 *       BlockItem}) and {@code processPendingUpdate} (server correction) go through the virtual
 *       {@code ClientWorld.setBlockState(BlockPos,BlockState,II)Z}; hence the second hook.</li>
 *   <li>Both packet paths run {@code NetworkThreadUtils.forceMainThread} first, prediction and
 *       tick are main-thread → everything here is MAIN THREAD ONLY, plain statics suffice.</li>
 *   <li><b>NOT covered by any hook (measured gap):</b> full-chunk (re)loads ({@code onChunkData})
 *       deserialise whole sections without per-block calls. The budgeted sweep is the safety net
 *       for exactly this — see the activation plan: turn the poll DOWN, never fully OFF.</li>
 * </ul>
 *
 * <h2>AKTIVIERUNGS-PLAN (wartet auf boot-fenster — the documented gate of this task)</h2>
 * <ol>
 *   <li>Nächster deploy-fenster-boot lädt mixin + neue tick()-body zusammen (ein commit = eine
 *       deploy-einheit). Zero verhalten bis ein export den tracker armt — early shipping is free.</li>
 *   <li>Weave-beweis: EXEC-bytecode-probe auf {@code ClientWorld} (declared methods enthalten
 *       {@code blenderbridge$onHandleBlockUpdate}/{@code blenderbridge$onSetBlockState} — muster
 *       der bbstools-mixin-proben; mixin-methoden sind für reflection auf INSTANZEN unsichtbar,
 *       im class-bytecode aber sichtbar).</li>
 *   <li>Runtime-gate: region exportieren (armt tracker) → setblock in der region → BLOCK_DELTA
 *       im wire-capture NOCH IM SELBEN TICK (event-latenz ≪ poll's ~1s sweep; wire-format
 *       byte-gleich zum v1-gate von W7-B4).</li>
 *   <li>DANN poll RUNTER, NICHT AUS: {@code BUDGET_MAX} 32768 → 4096 und/oder
 *       {@code TARGET_SWEEP_TICKS} 20 → 200 (sweep ~10s statt ~1s; body/konstanten-swap auf
 *       BlockDeltaTracker, post-boot hotswappbar). Der sweep bleibt als netz für die gemessene
 *       chunk-reload-lücke (oben) — poll ganz aus würde die stille-loch-klasse von bugs
 *       wiedereröffnen, die task 3.2 zugenagelt hat.</li>
 *   <li>Gate nach dem tuning: event same-tick UND sweep findet einen chunk-reload-diff noch.</li>
 * </ol>
 *
 * <h2>QUEUE SEMANTICS</h2>
 * <p>{@link #notifyExternal} only guards + enqueues (packed {@code BlockPos.asLong}, cap
 * {@value #QUEUE_CAP}); ALL diffing happens in {@link #flush} on the tick boundary. Overflow
 * (>{@value #QUEUE_CAP} notifies in one tick = paste-scale, far above BATCH_CAP) counts drops
 * and flush degrades to the reExport recommendation — dropped positions keep their old snapshot
 * value, so the sweep re-finds them later; a harmless trickle after a reExport (addon apply is
 * measured-idempotent), honest and documented rather than an unbounded queue. Stale entries
 * (disarm/re-arm between notify and flush) self-heal: bounds are re-checked at flush time and a
 * fresh snapshot diffs them as UNSEEN → silent adopt, never a false report.</p>
 */
public final class BlockDeltaEvents
{
    /** Max notified positions buffered per tick. 8 KB static; a hand edit is 1-few entries,
     * a piston a dozen — anything that overflows this is paste-scale and lands in the
     * reExport recommendation anyway (BATCH_CAP is 256). */
    static final int QUEUE_CAP = 1024;

    private static final long[] QUEUE = new long[QUEUE_CAP];

    private static int queued;
    private static int dropped;

    private BlockDeltaEvents()
    {
        /* static-only bridge */
    }

    /**
     * Mixin entry point (also EXEC-callable for the weave gate): a block change was applied at
     * {@code pos} in {@code world}. Cheap guards, then enqueue — NO world reads here (this runs
     * inside vanilla's block-update path; keep it to a few field reads).
     */
    public static void notifyExternal(ClientLevel world, BlockPos pos)
    {
        if (!BlockDeltaTracker.armed)
        {
            return;
        }

        WeakReference<ClientLevel> ref = BlockDeltaTracker.armedWorld;

        if (ref == null || ref.get() != world)
        {
            return;
        }

        int lx = pos.getX() - BlockDeltaTracker.minX;
        int ly = pos.getY() - BlockDeltaTracker.minY;
        int lz = pos.getZ() - BlockDeltaTracker.minZ;

        if (lx < 0 || lx >= BlockDeltaTracker.sizeX
            || ly < 0 || ly >= BlockDeltaTracker.sizeY
            || lz < 0 || lz >= BlockDeltaTracker.sizeZ)
        {
            return;
        }

        if (queued >= QUEUE_CAP)
        {
            dropped++;

            return;
        }

        QUEUE[queued++] = pos.asLong();
    }

    /**
     * Tick-boundary drain, called from {@code BlockDeltaTracker.tick()} AFTER its armed/world
     * checks (so {@code world} is the validated armed world). Mirrors the sweep's per-position
     * core (chunk-loaded EmptyChunk guard, UNSEEN-adopt, diff-adopt-report — MUST stay in sync
     * with the loop in {@code BlockDeltaTracker.tick()}, the package's resolveOpenFilm-style
     * deliberate duplication) and pushes ONE batch through {@code BlockDeltaTracker.push}.
     */
    static void flush(ClientLevel world)
    {
        if (queued == 0 && dropped == 0)
        {
            return;
        }

        List<String> changes = new ArrayList<>();
        int changeCount = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int i = 0; i < queued; i++)
        {
            long packed = QUEUE[i];

            int x = BlockPos.getX(packed);
            int y = BlockPos.getY(packed);
            int z = BlockPos.getZ(packed);

            int lx = x - BlockDeltaTracker.minX;
            int ly = y - BlockDeltaTracker.minY;
            int lz = z - BlockDeltaTracker.minZ;

            if (lx < 0 || lx >= BlockDeltaTracker.sizeX
                || ly < 0 || ly >= BlockDeltaTracker.sizeY
                || lz < 0 || lz >= BlockDeltaTracker.sizeZ)
            {
                /* Stale entry from before a re-arm — the new region does not cover it. */
                continue;
            }

            if (world.getChunk(x >> 4, z >> 4) instanceof EmptyLevelChunk)
            {
                /* Unloaded between notify and flush: getBlockState would silently read air
                 * (Task 3.2 trap). Keep the snapshot, the sweep resumes when it is back. */
                continue;
            }

            BlockState state = world.getBlockState(pos.set(x, y, z));
            int rawId = Block.getId(state);
            int idx = (lx * BlockDeltaTracker.sizeZ + lz) * BlockDeltaTracker.sizeY + ly;

            if (BlockDeltaTracker.snapshot[idx] == BlockDeltaTracker.UNSEEN)
            {
                /* Baseline not yet established for this cell (sweep has not passed it since
                 * arming) — adopt silently, exactly like the sweep's snapshot phase. */
                BlockDeltaTracker.snapshot[idx] = rawId;

                continue;
            }

            if (BlockDeltaTracker.snapshot[idx] == rawId)
            {
                /* No-op notify (e.g. prediction already applied it, server confirm re-notified). */
                continue;
            }

            BlockDeltaTracker.snapshot[idx] = rawId;
            changeCount++;

            if (changeCount <= BlockDeltaTracker.BATCH_CAP)
            {
                changes.add(BlockDeltaTracker.changeEntry(x, y, z, state));
            }
        }

        int droppedNow = dropped;

        queued = 0;
        dropped = 0;

        if (droppedNow > 0)
        {
            /* Overflow = paste-scale; count includes undiffed drops (honest approximation,
             * see class doc) and is guaranteed > BATCH_CAP → push emits reExport:true and
             * ignores the entry list. */
            BlockDeltaTracker.push(Collections.emptyList(), changeCount + droppedNow);
        }
        else if (changeCount > 0)
        {
            BlockDeltaTracker.push(changes, changeCount);
        }
    }

    /** Gate probe (EXEC-friendly), mirrors {@code BlockDeltaTracker.describe()}. */
    public static String describe()
    {
        return "queued=" + queued + " dropped=" + dropped;
    }
}
