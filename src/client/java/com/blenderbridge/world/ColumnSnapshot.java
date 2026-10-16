// Coded by Afrobama/Claude — Blender<->BBS live-sync bridge (Phase 3 world export: off-thread column snapshot).
package com.blenderbridge.world;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;

/**
 * ColumnSnapshot — a deep-copied, thread-safe read view of one 16-wide chunk column (full world
 * height) plus a neighbour rim, so the world export (Task 3.1 streaming pipeline) can mesh a
 * column on a worker thread without ever touching {@code client.level} off the main thread.
 *
 * <p>WHY THIS EXISTS (research fundament R1, plan v2 Task 3.1): every live MC-side meshing tool
 * builds a snapshot-with-rim before handing block data to a worker — vanilla's own chunk (re)build
 * uses exactly this shape. A column snapshot without a rim gets face culling, biome tint and fluid
 * corner-heights silently wrong at every column edge (panel B2): {@link Block#shouldDrawSide}
 * reads the neighbour position directly, {@link BlockColors#getColor}/{@link #getBlockTint} blend
 * biomes in a radius around the queried block, and {@code FluidRenderer} derives water surface
 * height from neighbouring fluid levels. All three reach outside a bare 16&times;16 column.</p>
 *
 * <p>VANILLA COMPARISON (bytecode-checked, 2026-07-16, yarn 1.21.1+build.3): the plan's research
 * cites vanilla's {@code ChunkCache(World, BlockPos min, BlockPos max)} (yarn 23w03a) as the
 * pattern to follow. In CURRENT 1.21.1 that class was renamed/rewritten to
 * {@link net.minecraft.client.renderer.chunk.RenderChunkRegion}, built by
 * {@code ChunkRendererRegionBuilder} from a live {@code World} plus a 3&times;3 grid of
 * {@code RenderedChunk}s. Disassembling it shows it does <b>NOT</b> deep-copy: every method
 * ({@code getBlockState}, {@code getBrightness}, {@code getLightingProvider}, {@code getColor})
 * either indexes straight into the live {@code RenderedChunk}/{@code WorldChunk} objects or
 * forwards directly to the live {@code World} field. That is safe for vanilla because a chunk
 * (re)build is triggered once and consumed within milliseconds. OUR export worker can hold a
 * column for a much longer, streamed export while the world keeps ticking underneath it (plan
 * panel N97), so a live-reference wrapper would risk tearing mid-read. We therefore deliberately
 * deep-copy into our own arrays on the main thread and never store a {@code World}/
 * {@code ClientWorld} reference — the one documented exception is {@link #getLightEngine()},
 * see its own doc below.</p>
 *
 * <p>RIM WIDTH (two independent rims, not one): the copied {@link BlockState} footprint uses
 * {@link #RIM_BLOCKS} = 1 (18&times;height&times;18 for a 16-wide column) — exactly enough for
 * {@code shouldDrawSide}'s single-neighbour read and {@code FluidRenderer}'s immediate-neighbour
 * corner-height sampling. The biome footprint is sized independently, wide enough for a full
 * {@link #DEFAULT_BIOME_BLEND_RADIUS} = 2 (vanilla's "Biome Blend: 5&times;5" default, bytecode-
 * verified below) around EVERY block in the real 16&times;16 interior, including the ones sitting
 * exactly on the column edge. Biome data is quart-cell resolution (1 sample per 4&times;4&times;4
 * blocks, matching the game's own on-disk resolution), so widening its footprint from 1 to 2
 * blocks worth of rim costs a few KiB, not the ~500KiB doubling a wider blockstate rim would cost —
 * see the MEMORY section.</p>
 *
 * <p>FLUID STATE IS NOT STORED SEPARATELY: verified in the 1.21.1 bytecode that
 * {@code ChunkSection.getFluidState(x,y,z)} and {@code WorldChunk.getFluidState(BlockPos)} both do
 * nothing but {@code blockStateContainer.get(x,y,z)} cast to {@link BlockState} followed by
 * {@link BlockState#getFluidState()} — a pure derivation with no extra storage. We do the same in
 * {@link #getFluidState}, which halves the array count this class would otherwise need and removes
 * an entire class of "the two arrays drifted apart" bugs.</p>
 *
 * <p>BIOME BLEND ALGORITHM (bytecode-decompiled from {@code ClientWorld.calculateColor}, yarn
 * 1.21.1+build.3, 2026-07-16 — NOT guessed): for a non-zero radius it iterates a
 * {@code (2r+1)} &times; {@code (2r+1)} SQUARE of positions at the SAME Y as the query (Y is never
 * varied — biome blend is horizontal only), calls {@code ColorResolver.getColor(biome, sampleX,
 * sampleZ)} at every sample, sums the R/G/B byte channels separately, then integer-divides each sum
 * by the sample count. Radius 0 skips the loop and resolves the single position directly. That is
 * exactly what {@link #getBlockTint} below reproduces, with two intentional differences, both
 * dictated by the memory budget, both documented at the call site: (a) the radius is a
 * constructor parameter, not read from live {@code GameOptions} (keeps a worker-thread read
 * deterministic and independent of the player's video settings); (b) the per-sample biome lookup
 * uses the nearest quart-cell directly ({@link LevelChunk#getNoiseBiome}) rather than
 * {@code BiomeAccess.getBiome(BlockPos)}'s additional sub-quart trilinear smoothing — see
 * {@link #biomeAt} for why.</p>
 *
 * <p>GETBRIGHTNESS — ⚠️ 26.2 hat diesen Punkt VEREINFACHT. Bis 1.21.1 mussten wir vanillas
 * Würfel-Schattierungstabelle ({@code getShade(direction, shaded)}) selbst nachbauen und dafür
 * ein Dimensions-Flag mitschleppen. In 26.2 ist die Tabelle ein eigener Wert: die Schnittstelle
 * verlangt {@code cardinalLighting()}, einen {@link net.minecraft.world.level.CardinalLighting}-Record
 * mit den sechs Helligkeiten (unten dunkel, oben hell, Nord/Süd mittig, Ost/West dunkler; die
 * Nether-Variante flacht alles ab). Wir kopieren ihn beim Bauen aus der Welt — dieselbe
 * Eigenschaft wie vorher (pro Dimension konstant, keine Blockdaten), nur nicht mehr von Hand
 * nachgerechnet. Low-stakes either way: {@link WorldExporter} reads raw {@link
 * net.minecraft.client.resources.model.geometry.BakedQuad} position/UV data directly (see its class doc) and
 * never consumes the shaded vertex-colour channel that brightness feeds, so this value never
 * reaches the exported OBJ.</p>
 *
 * <p>GETLIGHTINGPROVIDER — the one deliberate exception to "no live references": we hand back the
 * live world's {@link LevelLightEngine}, captured once at construction, exactly like vanilla's own
 * {@code ChunkRendererRegion} does. Re-implementing a stand-in light source would be a large,
 * separate undertaking for no payoff — {@link WorldExporter} does not bake lighting into the
 * exported mesh (Blender does its own lighting), so nothing this class exports depends on it. It
 * exists purely so {@code FluidRenderer}'s internal AO/shading maths has something to call without
 * throwing; a benign race against world ticking here cannot corrupt export data.</p>
 *
 * <p>GETBLOCKENTITY intentionally always returns {@code null}. Unlike {@link BlockState}/
 * {@link Biome} (immutable value data, safe to copy by value once), a {@link BlockEntity} is a
 * mutable, ticking object with no thread-safety contract — capturing the REFERENCE would just move
 * the "live object read from a worker thread" problem down one level instead of solving it. Per
 * the plan (Task 3.1: "BER bleibt main-thread"), block-entity geometry is rendered separately, on
 * the main thread, straight from the live world, and merged with this snapshot's column by the
 * writer — it never goes through this class. A caller that needs block-entity quads must not route
 * them through a {@link ColumnSnapshot}.</p>
 *
 * <p>MEMORY (task-specified calculation, not a live measurement — see the runtime proof for a real
 * construction-time number): the blockstate footprint is {@link #FOOTPRINT}&times;height&times;
 * {@link #FOOTPRINT} = 18&times;384&times;18 = 124,416 references for an Overworld column
 * (bottom -64, height 384). At 4 bytes/reference (default compressed oops) that is ~486 KiB; at 8
 * bytes/reference (compressed oops off) ~972 KiB — matching the task's "~1MB" figure. The
 * underlying {@link BlockState} objects are shared vanilla singletons and are NOT duplicated, only
 * the pointer slots cost memory. The biome array (quart resolution, independently sized for the
 * blend radius) is roughly 6&times;96&times;6 &asymp; 3,456 references &asymp; 13-27 KiB —
 * negligible next to the blockstate array, which is exactly why giving biome its own wider rim was
 * free. <b>BUDGET WARNING for whoever builds {@code ExportPipeline} next:</b> 32&times;32 = 1024
 * columns held SIMULTANEOUSLY at ~1MB each is ~1GB — the streaming pipeline must keep only a small
 * rolling window of live snapshots in flight, never the whole export's worth at once.</p>
 *
 * <p>THREADING CONTRACT: the constructor MUST run on the client main thread (it reads
 * {@code client.level} directly) and asserts this via {@link Minecraft#isSameThread()} —
 * constructing off-thread throws {@link IllegalStateException} rather than silently racing. Once
 * built, every field is {@code final} and every array is fully populated before the constructor
 * returns, so the finished object is safe to publish to a worker thread through any of the normal
 * safe-publication paths (a {@code BlockingQueue}, an {@code ExecutorService} submission, starting
 * a {@code new Thread(...)}) — the JMM's final-field-freeze guarantees the worker sees fully
 * initialised arrays. Do NOT publish it through a plain non-final/non-volatile field written by one
 * thread and read by another without such a handoff.</p>
 *
 * <p>{@link #outOfRimReads()} is the measurement instrument the plan asks for (panel B2): any read
 * — block state, fluid state (derived from block state), or a biome sample during the tint blend —
 * that falls outside the captured footprint increments it and degrades gracefully (block/fluid:
 * air; biome: nearest captured quart-cell via {@link Mth#clamp(int, int, int)}) instead of
 * throwing. In a correctly-sized real export this MUST stay 0 — a non-zero count means the rim is
 * too small for whatever is being queried and the exported geometry at that column's edge cannot
 * be trusted.</p>
 */
public final class ColumnSnapshot implements BlockAndTintGetter
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Blocks per side of the column this snapshot covers (one chunk column). */
    public static final int COLUMN_SIZE = 16;

    /**
     * Neighbour rim width, in blocks, around the column for the copied {@link BlockState} array —
     * exactly enough for {@code Block.shouldDrawSide}'s single-neighbour read and
     * {@code FluidRenderer}'s immediate-neighbour corner sampling. See the class doc's RIM WIDTH
     * section for why the (wider) biome-blend rim is sized independently instead of widening this.
     */
    public static final int RIM_BLOCKS = 1;

    /**
     * Vanilla's default biome-blend radius — bytecode-verified against {@code ClientWorld
     * .calculateColor} (yarn 1.21.1+build.3): this is the "Biome Blend: 5x5" video setting's
     * backing radius. See the class doc's BIOME BLEND ALGORITHM section.
     */
    public static final int DEFAULT_BIOME_BLEND_RADIUS = 2;

    /** {@link #COLUMN_SIZE} plus one {@link #RIM_BLOCKS} on each side of X and Z. */
    public static final int FOOTPRINT = COLUMN_SIZE + 2 * RIM_BLOCKS;

    /** World-space X of the column's own (non-rim) first block — i.e. {@code chunkX * 16}. */
    private final int columnMinX;

    /** World-space Z of the column's own (non-rim) first block — i.e. {@code chunkZ * 16}. */
    private final int columnMinZ;

    /** Lowest valid world Y for this dimension, captured from the live world at construction. */
    private final int bottomY;

    /** {@code HeightLimitView.getHeight()} for this dimension (384 Overworld, 256 Nether/End). */
    private final int heightBlocks;

    /** The biome-blend radius this instance was built with; see {@link #DEFAULT_BIOME_BLEND_RADIUS}. */
    private final int biomeBlendRadius;

    /**
     * Flat {@code [lx][ly][lz]} block-state array, {@link #FOOTPRINT}&times;{@code heightBlocks}
     * &times;{@link #FOOTPRINT} entries, indexed via {@link #index(int, int, int)}. Local
     * coordinates are shifted so {@code lx = worldX - columnMinX + RIM_BLOCKS} etc. — index 0 is
     * one block INTO the rim, not the column's own first block.
     */
    private final BlockState[] blockStates;

    /** Quart-cell (biome resolution, 4 blocks/cell) minimum X/Y/Z of the captured biome footprint. */
    private final int biomeMinX;
    private final int biomeMinY;
    private final int biomeMinZ;

    /** Quart-cell extent of the captured biome footprint on each axis. */
    private final int biomeSizeX;
    private final int biomeSizeY;
    private final int biomeSizeZ;

    /**
     * Flat {@code [qx][qy][qz]} biome array at QUART-CELL resolution (one entry per 4x4x4 blocks,
     * matching the game's own storage resolution — NOT one entry per block, see the class doc's
     * MEMORY section for why). Indexed via {@link #biomeIndex(int, int, int)}.
     */
    private final Holder<Biome>[] biomes;

    /**
     * Die Würfel-Schattierung dieser Dimension, einmal beim Bauen kopiert — das einzige Stück
     * Weltzustand, das {@link #cardinalLighting()} braucht. Siehe GETBRIGHTNESS in der Klassendoku.
     */
    private final CardinalLighting cardinalLighting;

    /**
     * The live world's lighting provider, captured once. Deliberate exception to "no live
     * references" — see the class doc's GETLIGHTINGPROVIDER section for why this one is safe.
     */
    private final LevelLightEngine lightingProvider;

    /**
     * The measurement instrument (plan panel B2): counts reads that fell outside the captured
     * footprint and had to degrade to a fallback instead of real data. MUST be 0 in a correct real
     * export. Thread-safe because worker threads read it concurrently with each other (though never
     * concurrently with construction — see THREADING CONTRACT).
     */
    private final AtomicInteger outOfRimReads = new AtomicInteger();

    /**
     * Build a snapshot of the chunk column at ({@code chunkX}, {@code chunkZ}) using
     * {@link #DEFAULT_BIOME_BLEND_RADIUS}. MAIN THREAD ONLY — see THREADING CONTRACT in the class
     * doc.
     *
     * @param world   the live client world to read from; never touched again after this constructor returns
     * @param chunkX  chunk-space X (i.e. {@code worldX >> 4}, NOT a block coordinate)
     * @param chunkZ  chunk-space Z (i.e. {@code worldZ >> 4}, NOT a block coordinate)
     */
    public ColumnSnapshot(ClientLevel world, int chunkX, int chunkZ)
    {
        this(world, chunkX, chunkZ, DEFAULT_BIOME_BLEND_RADIUS);
    }

    /**
     * Build a snapshot of the chunk column at ({@code chunkX}, {@code chunkZ}) with an explicit
     * biome-blend radius. MAIN THREAD ONLY — see THREADING CONTRACT in the class doc.
     *
     * @param world             the live client world to read from; never touched again after this constructor returns
     * @param chunkX            chunk-space X (i.e. {@code worldX >> 4}, NOT a block coordinate)
     * @param chunkZ            chunk-space Z (i.e. {@code worldZ >> 4}, NOT a block coordinate)
     * @param biomeBlendRadius  &gt;= 0; see {@link #DEFAULT_BIOME_BLEND_RADIUS}
     */
    public ColumnSnapshot(ClientLevel world, int chunkX, int chunkZ, int biomeBlendRadius)
    {
        assertMainThread();
        Objects.requireNonNull(world, "world");

        if (biomeBlendRadius < 0)
        {
            throw new IllegalArgumentException("biomeBlendRadius must be >= 0, got " + biomeBlendRadius);
        }

        long start = System.nanoTime();

        this.columnMinX = chunkX * COLUMN_SIZE;
        this.columnMinZ = chunkZ * COLUMN_SIZE;
        this.bottomY = world.getMinY();
        this.heightBlocks = world.getHeight();
        this.biomeBlendRadius = biomeBlendRadius;

        this.blockStates = captureBlockStates(world);

        this.biomeMinX = Math.floorDiv(this.columnMinX - biomeBlendRadius, 4);
        this.biomeMinZ = Math.floorDiv(this.columnMinZ - biomeBlendRadius, 4);
        this.biomeMinY = Math.floorDiv(this.bottomY, 4);
        this.biomeSizeX = Math.floorDiv(this.columnMinX + COLUMN_SIZE - 1 + biomeBlendRadius, 4) - this.biomeMinX + 1;
        this.biomeSizeZ = Math.floorDiv(this.columnMinZ + COLUMN_SIZE - 1 + biomeBlendRadius, 4) - this.biomeMinZ + 1;
        this.biomeSizeY = Math.floorDiv(this.bottomY + this.heightBlocks - 1, 4) - this.biomeMinY + 1;

        this.biomes = captureBiomes(world);

        /* 26.2: ClientLevel#effects() gibt es nicht mehr — die Dimensions-Sondereffekte sind
         * aufgeteilt worden, und die Würfel-Schattierung hängt jetzt direkt an der Welt. */
        this.cardinalLighting = world.cardinalLighting();
        this.lightingProvider = world.getLightEngine();

        long micros = (System.nanoTime() - start) / 1000L;

        LOGGER.info("[blenderbridge] ColumnSnapshot built: chunk ({}, {}), {} blockstate refs, {} biome refs, {} us", chunkX, chunkZ, this.blockStates.length, this.biomes.length, micros);
    }

    /**
     * Convenience factory: snapshot the column that contains {@code anyPosInColumn}, e.g. a
     * player's feet position. MAIN THREAD ONLY.
     */
    public static ColumnSnapshot forPosition(ClientLevel world, BlockPos anyPosInColumn)
    {
        int chunkX = SectionPos.blockToSectionCoord(anyPosInColumn.getX());
        int chunkZ = SectionPos.blockToSectionCoord(anyPosInColumn.getZ());

        return new ColumnSnapshot(world, chunkX, chunkZ);
    }

    /**
     * Throws unless called on the client main thread. Construction reads {@code client.level}
     * directly, which is unsafe from any other thread — see THREADING CONTRACT in the class doc.
     */
    private static void assertMainThread()
    {
        Minecraft client = Minecraft.getInstance();

        if (client == null || !client.isSameThread())
        {
            throw new IllegalStateException("ColumnSnapshot must be constructed on the client main thread (construction reads the live world; workers must only receive the finished snapshot, never build one)");
        }
    }

    /**
     * Copy every block state in the column+rim footprint out of the live world. Loops X/Z outer,
     * Y inner, and caches the resolved {@link LevelChunk} across the Y loop — for a fixed (X, Z)
     * all {@code heightBlocks} positions share one chunk, so this turns 124k potential chunk
     * look-ups into at most {@link #FOOTPRINT}&sup2; = 324.
     */
    private BlockState[] captureBlockStates(ClientLevel world)
    {
        BlockState[] result = new BlockState[FOOTPRINT * this.heightBlocks * FOOTPRINT];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        LevelChunk cachedChunk = null;
        int cachedChunkX = Integer.MIN_VALUE;
        int cachedChunkZ = Integer.MIN_VALUE;

        for (int lx = 0; lx < FOOTPRINT; lx++)
        {
            int worldX = this.columnMinX - RIM_BLOCKS + lx;
            int chunkX = SectionPos.blockToSectionCoord(worldX);

            for (int lz = 0; lz < FOOTPRINT; lz++)
            {
                int worldZ = this.columnMinZ - RIM_BLOCKS + lz;
                int chunkZ = SectionPos.blockToSectionCoord(worldZ);

                if (cachedChunk == null || chunkX != cachedChunkX || chunkZ != cachedChunkZ)
                {
                    cachedChunk = world.getChunk(chunkX, chunkZ);
                    cachedChunkX = chunkX;
                    cachedChunkZ = chunkZ;
                }

                for (int ly = 0; ly < this.heightBlocks; ly++)
                {
                    pos.set(worldX, this.bottomY + ly, worldZ);
                    result[index(lx, ly, lz)] = cachedChunk.getBlockState(pos);
                }
            }
        }

        return result;
    }

    /**
     * Copy every biome quart-cell in the (wider, blend-radius-sized) biome footprint out of the
     * live world, using {@link LevelChunk#getNoiseBiome} — the raw, nearest-cell public API
     * (no private/mixin access), NOT {@code BiomeAccess.getBiome(BlockPos)}'s additional sub-quart
     * interpolation. See the class doc's BIOME BLEND ALGORITHM and MEMORY sections for why.
     */
    @SuppressWarnings("unchecked")
    private Holder<Biome>[] captureBiomes(ClientLevel world)
    {
        Holder<Biome>[] result = (Holder<Biome>[]) new Holder<?>[this.biomeSizeX * this.biomeSizeY * this.biomeSizeZ];

        LevelChunk cachedChunk = null;
        int cachedChunkX = Integer.MIN_VALUE;
        int cachedChunkZ = Integer.MIN_VALUE;

        for (int qx = 0; qx < this.biomeSizeX; qx++)
        {
            int quartX = this.biomeMinX + qx;
            int chunkX = SectionPos.blockToSectionCoord(quartX * 4);

            for (int qz = 0; qz < this.biomeSizeZ; qz++)
            {
                int quartZ = this.biomeMinZ + qz;
                int chunkZ = SectionPos.blockToSectionCoord(quartZ * 4);

                if (cachedChunk == null || chunkX != cachedChunkX || chunkZ != cachedChunkZ)
                {
                    cachedChunk = world.getChunk(chunkX, chunkZ);
                    cachedChunkX = chunkX;
                    cachedChunkZ = chunkZ;
                }

                for (int qy = 0; qy < this.biomeSizeY; qy++)
                {
                    int quartY = this.biomeMinY + qy;
                    result[biomeIndex(qx, qy, qz)] = cachedChunk.getNoiseBiome(quartX, quartY, quartZ);
                }
            }
        }

        return result;
    }

    /** Flat-array index into {@link #blockStates} for LOCAL (already rim-shifted) coordinates. */
    private int index(int lx, int ly, int lz)
    {
        return (lx * this.heightBlocks + ly) * FOOTPRINT + lz;
    }

    /** Flat-array index into {@link #biomes} for LOCAL (already footprint-shifted) quart coordinates. */
    private int biomeIndex(int qx, int qy, int qz)
    {
        return (qx * this.biomeSizeY + qy) * this.biomeSizeZ + qz;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Inside the column+rim: the copied value (identical block + properties to the live world
     * at capture time). Outside: {@link Blocks#AIR}'s default state, and {@link #outOfRimReads()}
     * increments — see the class doc.</p>
     */
    @Override
    public BlockState getBlockState(BlockPos pos)
    {
        /* Task 3.3c: outside the export region there is no neighbour in the mesh — report air so
         * the boundary face gets DRAWN (walls + floor), mirroring the alt path's RegionClamp.
         * BEFORE the rim bookkeeping: a clamped read is intentional, not an outOfRim anomaly. */
        if (StreamRegionClamp.isBoundaryAir(pos))
        {
            return Blocks.AIR.defaultBlockState();
        }

        int lx = pos.getX() - this.columnMinX + RIM_BLOCKS;
        int ly = pos.getY() - this.bottomY;
        int lz = pos.getZ() - this.columnMinZ + RIM_BLOCKS;

        if (lx < 0 || lx >= FOOTPRINT || ly < 0 || ly >= this.heightBlocks || lz < 0 || lz >= FOOTPRINT)
        {
            this.outOfRimReads.incrementAndGet();

            return Blocks.AIR.defaultBlockState();
        }

        return this.blockStates[index(lx, ly, lz)];
    }

    /**
     * {@inheritDoc}
     *
     * <p>Derived from {@link #getBlockState}, exactly like vanilla ({@code ChunkSection
     * .getFluidState}/{@code WorldChunk.getFluidState} — verified in the 1.21.1 bytecode to be pure
     * derivation, no separate fluid storage). Out-of-rim reads therefore also route through
     * {@link #getBlockState}'s own {@link #outOfRimReads()} accounting.</p>
     */
    @Override
    public FluidState getFluidState(BlockPos pos)
    {
        return getBlockState(pos).getFluidState();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Always {@code null} — block entities are intentionally not captured. See the class doc's
     * GETBLOCKENTITY section: they are mutable, ticking objects with no thread-safety contract, and
     * the plan keeps block-entity rendering on the main thread via the live world directly.</p>
     */
    @Override
    public BlockEntity getBlockEntity(BlockPos pos)
    {
        return null;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Reproduces {@code ClientWorld.calculateColor}'s square-sample averaging exactly (bytecode-
     * decompiled, see the class doc's BIOME BLEND ALGORITHM section), reading biome samples from
     * this snapshot's own captured quart-cell array via {@link #biomeAt} instead of the live world.</p>
     */
    @Override
    public int getBlockTint(BlockPos pos, ColorResolver colorResolver)
    {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        if (this.biomeBlendRadius == 0)
        {
            return colorResolver.getColor(biomeAt(x, y, z), x, z);
        }

        int sideLength = this.biomeBlendRadius * 2 + 1;
        int count = sideLength * sideLength;
        int sumR = 0;
        int sumG = 0;
        int sumB = 0;

        /* Biome blend is HORIZONTAL only — Y is held constant across every sample, matching the
         * decompiled vanilla loop exactly (it never varies the iterator's Y bound). */
        for (int dz = -this.biomeBlendRadius; dz <= this.biomeBlendRadius; dz++)
        {
            int sampleZ = z + dz;

            for (int dx = -this.biomeBlendRadius; dx <= this.biomeBlendRadius; dx++)
            {
                int sampleX = x + dx;
                Biome biome = biomeAt(sampleX, y, sampleZ);
                int color = colorResolver.getColor(biome, sampleX, sampleZ);

                sumR += (color >> 16) & 0xFF;
                sumG += (color >> 8) & 0xFF;
                sumB += color & 0xFF;
            }
        }

        return ((sumR / count) & 0xFF) << 16 | ((sumG / count) & 0xFF) << 8 | (sumB / count) & 0xFF;
    }

    /**
     * Resolve the biome at a block position from the captured quart-cell array. Positions outside
     * the captured biome footprint clamp to the nearest captured cell (texture-edge-clamp style —
     * still a real captured biome, not an arbitrary default) and increment
     * {@link #outOfRimReads()}; in a correctly-sized real export (radius &lt;= the footprint's own
     * margin) this branch is never taken for the interior column positions the exporter actually
     * queries.
     */
    private Biome biomeAt(int blockX, int blockY, int blockZ)
    {
        int qx = Math.floorDiv(blockX, 4) - this.biomeMinX;
        int qy = Math.floorDiv(blockY, 4) - this.biomeMinY;
        int qz = Math.floorDiv(blockZ, 4) - this.biomeMinZ;

        boolean outOfRange = qx < 0 || qx >= this.biomeSizeX || qy < 0 || qy >= this.biomeSizeY || qz < 0 || qz >= this.biomeSizeZ;

        if (outOfRange)
        {
            this.outOfRimReads.incrementAndGet();
            qx = Mth.clamp(qx, 0, this.biomeSizeX - 1);
            qy = Mth.clamp(qy, 0, this.biomeSizeY - 1);
            qz = Mth.clamp(qz, 0, this.biomeSizeZ - 1);
        }

        return this.biomes[biomeIndex(qx, qy, qz)].value();
    }

    /**
     * {@inheritDoc}
     *
     * <p>⚠️ 26.2: Das ist der Ersatz für {@code getShade(Direction, boolean)}. Statt einer Methode,
     * die die Schattierung je Richtung ausrechnet, verlangt die Schnittstelle jetzt den fertigen
     * Wert; wer eine einzelne Richtung braucht, ruft {@code byFace(direction)} darauf. Der zweite
     * alte Parameter ({@code shaded}) ist ersatzlos weg — ob überhaupt schattiert wird, entscheidet
     * nicht mehr die Weltansicht, sondern der Aufrufer. Der Wert ist wie vorher rein: einmal beim
     * Bauen aus der Welt kopiert, keine Positionsdaten. Siehe GETBRIGHTNESS in der Klassendoku.</p>
     */
    @Override
    public CardinalLighting cardinalLighting()
    {
        return this.cardinalLighting;
    }

    /**
     * {@inheritDoc}
     *
     * <p>The live world's lighting provider, captured once at construction — the one deliberate
     * exception to "no live references", see the class doc's GETLIGHTINGPROVIDER section.</p>
     */
    @Override
    public LevelLightEngine getLightEngine()
    {
        return this.lightingProvider;
    }

    /** {@inheritDoc} Captured from the live world at construction (384 Overworld, 256 Nether/End). */
    @Override
    public int getHeight()
    {
        return this.heightBlocks;
    }

    /** {@inheritDoc} Captured from the live world at construction (-64 Overworld, 0 Nether/End).
     *  26.2: heißt jetzt {@code getMinY()} — gleicher Wert, kürzerer Name. */
    @Override
    public int getMinY()
    {
        return this.bottomY;
    }

    /**
     * The measurement instrument (plan panel B2): how many reads (block state, derived fluid
     * state, or a biome tint sample) fell outside the captured footprint and had to degrade to a
     * fallback. MUST be 0 for a correctly-sized real export — see the class doc.
     */
    public int outOfRimReads()
    {
        return this.outOfRimReads.get();
    }

    /** World-space X of the column's own (non-rim) first block. */
    public int columnMinX()
    {
        return this.columnMinX;
    }

    /** World-space Z of the column's own (non-rim) first block. */
    public int columnMinZ()
    {
        return this.columnMinZ;
    }

    /** The biome-blend radius this instance was built with. */
    public int biomeBlendRadius()
    {
        return this.biomeBlendRadius;
    }

    @Override
    public String toString()
    {
        return "ColumnSnapshot{column=[" + this.columnMinX + ".." + (this.columnMinX + COLUMN_SIZE - 1) + ", " + this.columnMinZ + ".." + (this.columnMinZ + COLUMN_SIZE - 1) + "], rimBlocks=" + RIM_BLOCKS + ", biomeBlendRadius=" + this.biomeBlendRadius + ", outOfRimReads=" + this.outOfRimReads.get() + "}";
    }
}
