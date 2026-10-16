// Coded by Afrobama/Claude — Blender<->BBS live-sync bridge (Task 3.2: laden ehrlich machen).
package com.blenderbridge.world;

import com.google.gson.JsonArray;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * ChunkCoverage — Task 3.2: catches the "export a region with UNLOADED chunks silently returns
 * ok:true with holes" trap ({@code WorldExporter}'s own dossier entry: "nur GELADENE chunks (sonst
 * ok:true+blocks:0!)"). A region whose far side sits outside the player's view distance reads every
 * position there as {@code Blocks.AIR} (world.getBlockState degrades silently, no exception, no
 * flag) — the export finishes, reports success, and the Blender side has no way to tell "empty
 * because the region truly is air" from "empty because the chunk never loaded".
 *
 * <p>WHY A NEW, SEPARATE CLASS (deploy-window-free wiring, same pattern as {@link RegionClamp}/
 * {@link TextureStripExporter}/{@link StreamMetaWriter}): {@link WorldExporter} is an already-loaded
 * class in the running client this session, and so is its nested {@code ExportResult} — changing
 * that record's constructor/field list to add {@code chunksTotal}/{@code chunksLoaded}/
 * {@code missingChunks} would hit exactly the nested-class hotswap trap this project's dossier
 * documents fresh (2026-07-17, {@code TextureStripExporter.AnimatedTexture}: a nested class is its
 * own {@code .class} file, a changed constructor needs its OWN hotswap call, and on THIS JVM
 * new/changed fields on an already-loaded class risk the worse {@code <clinit>}-never-reruns trap
 * documented in the package dossier). So {@code ExportResult}'s shape stays byte-for-byte what it
 * was; this class holds the coverage tally itself (arm/record/read-and-clear, exactly {@link
 * RegionClamp}'s set/clear rhythm) and {@code WorldExportBridge.payload} (a body-only edit, already
 * package-private for exactly this kind of cross-class reach) appends the three new keys straight
 * onto the outgoing JSON, no new constructor arg anywhere.</p>
 *
 * <h2>CHUNK GRANULARITY, NOT BLOCK GRANULARITY</h2>
 * <p>Coverage is tracked per CHUNK COLUMN ({@code chunkX = worldX >> 4}, {@code chunkZ = worldZ >>
 * 16} — via {@link SectionPos#blockToSectionCoord(int)}, the same idiom {@link ColumnSnapshot} and
 * {@link StreamingWorldExporter} already use), not per block: "chunk loaded" is what the game and
 * the addon-side status message actually mean, and de-duping to one {@code isChunkLoaded} probe per
 * chunk (not per block) keeps this a few hundred extra calls on a 128^3 region, not millions.</p>
 *
 * <h2>WIRING (single call site, cheap)</h2>
 * <p>{@link WorldExporter#runExport}'s block-scan loop already walks Y-outer/Z/X-inner and already
 * has the current {@code (x, y, z)} in hand for every position, loaded or not — {@link #record}
 * is called once per position from that loop (not a second scan) and internally short-circuits to a
 * no-op after the first call for a given chunk this export (a {@code LinkedHashSet} membership
 * check), so the actual chunk-identity probe still only runs once per distinct chunk.</p>
 *
 * <h2>MEASURED TRAP — {@code World.isChunkLoaded(BlockPos)} LIES ON THE CLIENT</h2>
 * <p>The obvious API is {@code World.isChunkLoaded}, and the first version of this class used it —
 * WRONG, caught live against the running game before landing (2026-07-17, region x+2000 from the
 * player, view distance 12): it returned {@code true} for a chunk the player had never been near.
 * Measured directly: {@code world.getChunk(cx, cz)} for that position returned an instance of
 * {@link EmptyLevelChunk} (vanilla's placeholder for "no real chunk here"), while the SAME call at the
 * player's own position returned a real {@link LevelChunk} — and {@code isChunkLoaded} answered
 * {@code true} for BOTH. {@code ClientChunkManager} always keeps some chunk object at every
 * coordinate (so unrelated code never null-checks), and {@code isChunkLoaded}'s own contract is
 * about the chunk MANAGER'S bookkeeping, not "is this real generated terrain" — the empty
 * placeholder still counts as "loaded" by that measure. The correct ground truth, used here instead,
 * is {@code !(world.getChunk(cx, cz) instanceof EmptyChunk)} — exactly the same instanceof check
 * {@code ClientWorld}/{@code ClientChunkManager} use internally to decide whether to render/tick a
 * chunk at all. Getting this wrong would have made the Task 3.2 gate lie in exactly the way the
 * whole task exists to stop lying (a "fully unloaded far region" gate reporting chunksLoaded ==
 * chunksTotal, i.e. false confidence).</p>
 */
public final class ChunkCoverage
{
    private static final long CHUNK_KEY_SHIFT = 32L;

    /** Cap on how many missing-chunk coordinates the result reports — see {@link #missingChunksJson}. */
    private static final int MAX_MISSING_REPORTED = 20;

    /** Chunk keys ({@code (chunkX << 32) | (chunkZ & 0xFFFFFFFFL)}) already probed this export. */
    private static Set<Long> seen = new LinkedHashSet<Long>();

    /** Chunk keys probed and found loaded. */
    private static Set<Long> loaded = new LinkedHashSet<Long>();

    /** Chunk keys probed and found NOT loaded, in first-seen order — the source for the capped report. */
    private static Set<Long> missing = new LinkedHashSet<Long>();

    private ChunkCoverage()
    {
        /* static-only helper */
    }

    /** Arm a fresh tally for one export run — MUST be called before the block scan starts. */
    public static void armForExport()
    {
        seen = new LinkedHashSet<Long>();
        loaded = new LinkedHashSet<Long>();
        missing = new LinkedHashSet<Long>();
    }

    /**
     * Record the chunk containing {@code pos}, probing chunk identity exactly once per distinct
     * chunk (repeat calls for a chunk already seen this export are a single {@code Set} lookup and
     * return immediately). Safe to call for every block position in the scan loop.
     *
     * <p>Ground truth is {@code !(world.getChunk(cx, cz) instanceof EmptyChunk)}, NOT
     * {@code world.isChunkLoaded(pos)} — see the class doc's MEASURED TRAP section for why the
     * obvious API lies on the client.</p>
     */
    public static void record(ClientLevel world, BlockPos pos)
    {
        int chunkX = SectionPos.blockToSectionCoord(pos.getX());
        int chunkZ = SectionPos.blockToSectionCoord(pos.getZ());
        long key = (((long) chunkX) << CHUNK_KEY_SHIFT) | (chunkZ & 0xFFFFFFFFL);

        if (!seen.add(key))
        {
            /* Already probed this chunk this export. */
            return;
        }

        boolean realChunk = !(world.getChunk(chunkX, chunkZ) instanceof EmptyLevelChunk);

        if (realChunk)
        {
            loaded.add(key);
        }
        else
        {
            missing.add(key);
        }
    }

    /** How many distinct chunks the scanned region touched. */
    public static int chunksTotal()
    {
        return seen.size();
    }

    /** How many of those chunks were loaded at scan time. */
    public static int chunksLoaded()
    {
        return loaded.size();
    }

    /**
     * The missing-chunk coordinates as a {@link JsonArray}, one {@code "chunkX chunkZ"} string per
     * entry, capped at {@link #MAX_MISSING_REPORTED} so a region that is entirely unloaded (e.g. the
     * region-far-from-player gate case) does not dump hundreds of coordinates into the reply.
     */
    public static JsonArray missingChunksJson()
    {
        JsonArray out = new JsonArray();
        int i = 0;

        for (Long key : missing)
        {
            if (i >= MAX_MISSING_REPORTED)
            {
                break;
            }

            int chunkX = (int) (key >> CHUNK_KEY_SHIFT);
            int chunkZ = (int) (long) key;

            out.add(chunkX + " " + chunkZ);
            i++;
        }

        return out;
    }
}
