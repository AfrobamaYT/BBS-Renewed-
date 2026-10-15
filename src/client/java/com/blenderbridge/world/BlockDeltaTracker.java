// Coded by Afrobama/Claude — Blender<->BBS live-sync bridge (plan v2 task 3.6: live block delta).
package com.blenderbridge.world;

import com.blenderbridge.BlenderBridgeClient;
import com.blenderbridge.BridgeProtocol;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.EmptyLevelChunk;

/**
 * BlockDeltaTracker — plan v2 task 3.6 (the USP piece): a block changes in the running game and
 * shows up in Blender WITHOUT a re-export. This class is the BBS half: it watches the region of
 * the most recent successful WORLD_EXPORT and pushes an unsolicited {@code BLOCK_DELTA} message
 * (raw string type, {@code FILM_CAMERA_EXPORT} pattern — no new {@code BridgeProtocol.TYPE_*}
 * constant, that would be a field on an already-loaded class = boot) the tick a difference is
 * found. The Blender consumer is {@code blender_bbs_bridge/world_delta.py}.
 *
 * <h2>WHY A BUDGETED SNAPSHOT-DIFF POLL AND NOT AN EVENT (MEASURED, 2026-07-17)</h2>
 * <p>There is NO mixin-free block-change signal on the client — measured, not assumed:</p>
 * <ul>
 *   <li>fabric-api 0.116.7+1.21.1 {@code fabric-lifecycle-events-v1} ships ONLY
 *       {@code ClientBlockEntityEvents} (BE load/unload), {@code ClientChunkEvents} (chunk
 *       load/unload), {@code ClientEntityEvents}, {@code ClientWorldEvents}, {@code
 *       ClientTickEvents} — no per-block-change client event exists (jar listing checked).</li>
 *   <li>BBS' own {@code ClientWorldMixin} hooks only {@code addParticle} — nothing to ride, and
 *       extending it would be a BBS-source edit (forbidden) AND a mixin change (boot).</li>
 *   <li>The exact hook would be a new mixin into {@code ClientWorld.handleBlockUpdate} — a NEW
 *       mixin only weaves at boot (deploy-window doc, table row 3). Deliberately NOT built in
 *       v1: the poll below works TODAY, restart-free. <b>v2 UPDATE (W8-B3, 2026-07-17): the
 *       mixin IS now built and committed DORMANT</b> ({@code ClientWorldBlockDeltaMixin} + the
 *       {@link BlockDeltaEvents} bridge, two hooks — measured funnel + activation plan in that
 *       class' doc). It waits for the next boot window; until then this poll carries the
 *       feature, and afterwards the poll gets tuned DOWN (not off — it remains the safety net
 *       for the measured full-chunk-reload gap no block-level hook can see).</li>
 * </ul>
 *
 * <p><b>⚠ HOTSWAP-HINWEIS SEIT W8-B3:</b> the committed class differs from the loaded ffc7c5f
 * version structurally (several members widened private→package-private for the bridge, one
 * flush line in {@link #tick()}). Do NOT force-sync-hotswap this class without need before the
 * boot window: the body/access changes are the sanctioned safe shapes and the flush call is a
 * no-op while the mixin is unwoven (empty queue), but there is nothing to gain — the event path
 * cannot run pre-boot anyway.</p>
 * <p>The naive alternative — fingerprint the WHOLE region every tick — is the "teure
 * Voll-Serialisierung pro tick" shape the plan warns against (a 1.6M-block region is millions of
 * palette reads per tick). This class instead keeps a raw-state-id snapshot of the region and
 * diffs a BUDGETED SLICE per tick, round-robin: full-region sweep latency is
 * {@code volume / budget} ticks (~{@value #TARGET_SWEEP_TICKS} ticks = ~1s by construction),
 * per-tick cost is capped and constant. Detection latency is therefore up to one sweep, not one
 * tick — the honest price of restart-free; documented, not hidden.</p>
 *
 * <h2>DEPLOY-WINDOW-FREE WIRING (RegionClamp pattern)</h2>
 * <p>Brand-new lazy class; the two touched call sites only gain body lines: {@code
 * WorldExportBridge.export} arms it after a successful export (alt path) / job start (streaming),
 * {@code BlenderBridgeClient.onClientTick} calls {@link #tick()} — same shape as
 * {@code ActorPushTracker.tick()} right above it. No field, no method, no signature on any
 * already-loaded class. The armed region is this class' OWN copy — deliberately NOT
 * {@link RegionClamp}/{@link StreamRegionClamp} state (those are export-scoped and cleared in a
 * finally when the export run ends; this tracker must outlive the export by the whole session).</p>
 *
 * <h2>WIRE FORMAT (consumed by world_delta.py — change only after SendMessage-rücksprache)</h2>
 * <pre>
 * {"type":"BLOCK_DELTA","origin":"bbs","uuid":"world_delta","seq":N,"fields":{
 *   "region":[minX,minY,minZ,maxX,maxY,maxZ],   // the armed export region, inclusive
 *   "count":3,                                   // changed positions in this batch
 *   "reExport":false,                            // true => batch > BATCH_CAP, changes omitted,
 *                                                //         re-export recommended instead
 *   "changes":["x y z minecraft:stone minecraft_block_stone", ...]}}
 * </pre>
 * <p>Each change is space-separated: 3 ints, the block id, and an OPTIONAL 5th token — the
 * material key the exporter would use for this block's PARTICLE sprite ({@code safeName} of the
 * sprite id, e.g. {@code minecraft_block_stone}), so the addon can patch with a material already
 * in the imported pool. The coordinator's minimal contract is the first 4 tokens ("x y z
 * block-id"); the 5th is an additive hint, absent for air. The particle sprite is a
 * REPRESENTATIVE texture (v1 single-material cube patch), not the per-face truth — a
 * grass_block patch renders all-dirt-ish; honest v1 limit, the real fix is the v2 re-mesh.</p>
 *
 * <h2>UNLOADED CHUNKS — the Task 3.2 trap applies HERE too</h2>
 * <p>{@code world.getBlockState} on an unloaded chunk silently reads air ({@code ChunkCoverage}
 * MEASURED TRAP). Diffing that would fire a storm of "block -> air" deltas the moment the player
 * walks away and the far chunks unload. So every scanned position first checks
 * {@code !(world.getChunk(cx,cz) instanceof EmptyChunk)} (the measured ground truth — NOT
 * {@code isChunkLoaded}, which lies) and unloaded positions are SKIPPED: their snapshot keeps the
 * last loaded value and diffing resumes when the chunk comes back.</p>
 *
 * <h2>THREADING + HOTSWAP DISCIPLINE</h2>
 * <p>MAIN THREAD ONLY ({@link #armRegion} from the export dispatch, {@link #tick()} from the
 * client tick) — plain static fields suffice, like {@link RegionClamp}. All statics are
 * primitives/null-defaults on purpose: if this class ever needs a body-fix hotswap after it is
 * loaded, there is no {@code <clinit>}-initialised field to fall back to null (the measured
 * crash trap in the dossier). The world reference is a {@link WeakReference} so a tracked region
 * never pins a closed world's whole object graph ({@code StreamMetaWriter} armedWorld lesson);
 * a world switch (new {@code ClientWorld} identity) disarms on the next tick.</p>
 */
public final class BlockDeltaTracker
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Snapshot cell value for "never seen loaded" — raw state ids are >= 0, so -1 is free.
     * Package-private since W8-B3: {@link BlockDeltaEvents} mirrors the adopt rule. */
    static final int UNSEEN = -1;

    /** Full-region sweep target, in ticks (~1s at 20 tps). Budget derives from this. */
    private static final int TARGET_SWEEP_TICKS = 20;

    /** Per-tick position-scan bounds. MIN keeps tiny regions snappy (sub-tick sweeps are free);
     * MAX caps the per-tick cost for huge streaming regions. MEASURED on this JVM (2026-07-17,
     * main-thread EXEC probe, the exact chunk-cached read loop below, all chunks loaded,
     * 131072 reads): 21.8ms cold-JIT, then 7.04ms / 7.33ms warm (~54ns/read) — 131072/tick
     * would eat ~14% of a 50ms tick, so MAX is 32768 (~1.8ms worst case). Honest trade-off
     * that buys: a 1.6M-block streaming region sweeps in ~49 ticks (~2.5s detection latency,
     * slightly over the plan's <2s target for THAT size — normal regions stay ~1s via
     * TARGET_SWEEP_TICKS), instead of costing 7ms every tick forever. */
    private static final int BUDGET_MIN = 4096;
    private static final int BUDGET_MAX = 32768;

    /** Regions above this volume are NOT tracked (honest disarm + log, no silent OOM risk):
     * 2^23 positions = 32 MB of int[] snapshot. Every region the gates ever exported (max ~1.6M)
     * fits with 5x headroom. */
    private static final int MAX_TRACK_VOLUME = 8_388_608;

    /** More changes than this in one tick's sweep (WorldEdit-scale paste) => the per-cube patch
     * path is the wrong tool; send a re-export recommendation instead of a mega-message. Plan
     * 3.6 wants this threshold measured — 256 is the documented v1 guess (a hand-built change is
     * always far below it, a paste far above), refine when a real WorldEdit workflow exists. */
    static final int BATCH_CAP = 256;

    /* Since W8-B3 the region/snapshot statics and the two wire helpers below are
     * package-private (NOT private): {@link BlockDeltaEvents} reuses the v1 poll structures
     * for the event path instead of duplicating region state (split-brain risk) or adding
     * members to this LOADED class (hotswap risk). Sanctioned shape per dossier —
     * "private→package-private relaxen ist ok (kein neues member)". Main-thread-only
     * discipline unchanged. */
    static boolean armed;
    static int minX;
    static int minY;
    static int minZ;
    static int sizeX;
    static int sizeY;
    static int sizeZ;
    private static int volume;
    private static int budget;
    private static int cursor;
    static int[] snapshot;
    static WeakReference<ClientLevel> armedWorld;

    private BlockDeltaTracker()
    {
        /* static-only helper */
    }

    /**
     * Arm the tracker for the region of a just-successful export. Replaces any previous armed
     * region (the addon's imported world now IS this region — the old one is stale). Called from
     * {@code WorldExportBridge.export}'s body on the main thread, AFTER the alt-path export
     * succeeded / the streaming job started — a failed/rejected request must never re-target the
     * tracker away from the region Blender actually has.
     */
    public static void armRegion(ClientLevel world, int x0, int y0, int z0, int x1, int y1, int z1)
    {
        if (world == null)
        {
            disarm("no world at arm time");

            return;
        }

        long vol = (long) (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1);

        if (vol <= 0 || vol > MAX_TRACK_VOLUME)
        {
            /* Honest refusal, not a silent cap: tracking a partial region would report "no
             * changes" for the untracked rest — exactly the hidden-hole lie Task 3.2 exists
             * to prevent. */
            disarm("region volume " + vol + " exceeds track limit " + MAX_TRACK_VOLUME);

            return;
        }

        minX = x0;
        minY = y0;
        minZ = z0;
        sizeX = x1 - x0 + 1;
        sizeY = y1 - y0 + 1;
        sizeZ = z1 - z0 + 1;
        volume = (int) vol;
        budget = Math.max(BUDGET_MIN, Math.min(BUDGET_MAX, volume / TARGET_SWEEP_TICKS + 1));
        cursor = 0;
        snapshot = new int[volume];
        java.util.Arrays.fill(snapshot, UNSEEN);
        armedWorld = new WeakReference<>(world);
        armed = true;

        LOGGER.info("[blenderbridge] block-delta tracker armed: [{},{},{}]..[{},{},{}] volume={} budget={}/tick",
            x0, y0, z0, x1, y1, z1, volume, budget);
    }

    /** Drop everything (snapshot included — 6-32 MB, must not linger). */
    public static void disarm(String why)
    {
        if (armed)
        {
            LOGGER.info("[blenderbridge] block-delta tracker disarmed: {}", why);
        }

        armed = false;
        snapshot = null;
        armedWorld = null;
    }

    /**
     * Per-tick budgeted sweep (called from {@code BlenderBridgeClient.onClientTick}'s widened
     * body, same shape as {@code ActorPushTracker.tick()}). Cheap no-op when disarmed. The FIRST
     * full pass over a position only adopts its state as the baseline (snapshot phase, per cell —
     * no diff against {@code UNSEEN}), so arming never floods Blender with the whole region as
     * "changes"; the same rule makes a chunk that loads later join the watch silently.
     */
    public static void tick()
    {
        if (!armed)
        {
            return;
        }

        ClientLevel world = Minecraft.getInstance().level;

        if (world == null || armedWorld == null || armedWorld.get() != world)
        {
            /* World closed or switched (new ClientWorld identity, e.g. dimension change): the
             * snapshot describes a world that no longer exists — comparing against the new one
             * would report the entire difference between two worlds as "edits". */
            disarm("world closed/switched");

            return;
        }

        /* W8-B3 (boot-effective, no-op until the ClientWorldBlockDeltaMixin is woven): drain
         * event-notified positions FIRST — they adopt the snapshot, so the sweep below can
         * never re-report them. See BlockDeltaEvents' class doc for the activation plan. */
        BlockDeltaEvents.flush(world);

        List<String> changes = new ArrayList<>();
        int changeCount = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        /* Chunk cache: consecutive indices share a column (y is the innermost index axis), so
         * one loaded-check serves up to sizeY positions. EmptyChunk instanceof is the measured
         * ground truth (ChunkCoverage trap) — isChunkLoaded lies on the client. */
        int cachedChunkX = Integer.MIN_VALUE;
        int cachedChunkZ = Integer.MIN_VALUE;
        boolean cachedLoaded = false;

        int steps = Math.min(budget, volume);

        for (int i = 0; i < steps; i++)
        {
            int idx = cursor;
            cursor = cursor + 1 >= volume ? 0 : cursor + 1;

            int iy = idx % sizeY;
            int columns = idx / sizeY;
            int iz = columns % sizeZ;
            int ix = columns / sizeZ;

            int x = minX + ix;
            int y = minY + iy;
            int z = minZ + iz;

            int chunkX = x >> 4;
            int chunkZ = z >> 4;

            if (chunkX != cachedChunkX || chunkZ != cachedChunkZ)
            {
                cachedChunkX = chunkX;
                cachedChunkZ = chunkZ;
                cachedLoaded = !(world.getChunk(chunkX, chunkZ) instanceof EmptyLevelChunk);
            }

            if (!cachedLoaded)
            {
                /* Unloaded: getBlockState would silently read air (Task 3.2 trap). Keep the
                 * last-known snapshot value and resume diffing when the chunk is back. */
                continue;
            }

            BlockState state = world.getBlockState(pos.set(x, y, z));
            int rawId = Block.getId(state);

            if (snapshot[idx] == UNSEEN)
            {
                snapshot[idx] = rawId;

                continue;
            }

            if (snapshot[idx] == rawId)
            {
                continue;
            }

            snapshot[idx] = rawId;
            changeCount++;

            /* SHORT-CIRCUIT above the cap (WorldEdit-scale paste): keep counting (the count is
             * what the reExport recommendation reports) but stop building wire entries — each
             * entry costs a string build AND a particle-sprite model lookup, and a paste can
             * dirty the whole budget's worth of positions in one sweep tick. The snapshot still
             * adopts every new value either way, so nothing is re-reported next sweep. */
            if (changeCount <= BATCH_CAP)
            {
                changes.add(changeEntry(x, y, z, state));
            }
        }

        if (changeCount > 0)
        {
            push(changes, changeCount);
        }
    }

    /* ================================================================= *
     *  Push                                                             *
     * ================================================================= */

    /**
     * Send one BLOCK_DELTA batch (this tick's sweep findings), unsolicited — {@code
     * BridgeServer.send} is a no-op with no client connected, exactly like the camera/actor
     * pushes. Over {@link #BATCH_CAP} changes the per-block list is omitted and {@code
     * reExport:true} tells the addon to recommend a fresh export instead (see the constant's
     * doc); the addon-side patching of hundreds of loose cubes would be slower AND uglier than
     * one clean re-import.
     */
    static void push(List<String> changes, int changeCount)
    {
        long seq = BlenderBridgeClient.STATE.nextOutboundSeq();
        JsonObject envelope = BridgeProtocol.envelope("BLOCK_DELTA", "world_delta", seq, BridgeProtocol.ORIGIN_BBS);
        JsonObject fields = BridgeProtocol.getFields(envelope);

        JsonArray region = new JsonArray();

        region.add(minX);
        region.add(minY);
        region.add(minZ);
        region.add(minX + sizeX - 1);
        region.add(minY + sizeY - 1);
        region.add(minZ + sizeZ - 1);
        fields.add("region", region);

        boolean reExport = changeCount > BATCH_CAP;
        JsonArray list = new JsonArray();

        if (!reExport)
        {
            for (String change : changes)
            {
                list.add(change);
            }
        }

        fields.addProperty("count", changeCount);
        fields.addProperty("reExport", reExport);
        fields.add("changes", list);

        BlenderBridgeClient.SERVER.send(envelope);

        LOGGER.debug("[blenderbridge] block-delta push: {} change(s), reExport={}", changeCount, reExport);
    }

    /** One wire entry: "x y z block-id [material-key]" — see class doc WIRE FORMAT. */
    static String changeEntry(int x, int y, int z, BlockState state)
    {
        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        String materialKey = state.isAir() ? "" : particleMaterialKey(state);

        StringBuilder entry = new StringBuilder(48);

        entry.append(x).append(' ').append(y).append(' ').append(z).append(' ').append(blockId);

        if (!materialKey.isEmpty())
        {
            entry.append(' ').append(materialKey);
        }

        return entry.toString();
    }

    /**
     * The material name the world exporter would have given this block's PARTICLE sprite —
     * {@code minecraft:block/stone} -> {@code minecraft_block_stone}, the exact {@code safeName}
     * transform from {@code WorldExporter} (duplicated small private copy, the package's
     * convention — see {@code ActorPushTracker.interpKey}'s note). The particle sprite is the
     * one representative texture vanilla itself uses for break particles; for the untinted
     * common case the resulting name matches a material already sitting in Blender's imported
     * pool. Any resolution failure degrades to "" (no hint) — the addon then falls back, never
     * this class throwing inside the tick handler.
     */
    private static String particleMaterialKey(BlockState state)
    {
        try
        {
            /* 26.2: BlockRenderDispatcher + BlockModelShaper sind weg. Das Partikel-Material
             * eines Blockzustands liefert der Modellsatz direkt -- derselbe Wert, eine
             * Indirektion weniger. */
            mchorse.bbs_mod.compat.alt.MaterialBaked material =
                Minecraft.getInstance().getModelManager().getBlockStateModelSet().getParticleMaterial(state);

            TextureAtlasSprite sprite = material == null ? null : material.sprite();

            if (sprite == null)
            {
                return "";
            }

            return safeName(sprite.contents().name());
        }
        catch (RuntimeException e)
        {
            return "";
        }
    }

    /** Same transform as {@code WorldExporter.safeName} (private there — small per-class copy). */
    private static String safeName(ResourceLocation id)
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

    /* ================================================================= *
     *  EXEC-probe helpers (gates) — read-only introspection             *
     * ================================================================= */

    /** Gate probe: current tracker state in one line (EXEC-friendly, no JSON building). */
    public static String describe()
    {
        if (!armed)
        {
            return "disarmed";
        }

        return "armed [" + minX + "," + minY + "," + minZ + "].." + "[" + (minX + sizeX - 1) + ","
            + (minY + sizeY - 1) + "," + (minZ + sizeZ - 1) + "] volume=" + volume + " budget=" + budget
            + " cursor=" + cursor;
    }
}
