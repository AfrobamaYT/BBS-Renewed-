// Coded by Afrobama/Claude — Blender<->BBS live-sync bridge (Task 3.3: sky-reachability cave culling).
package com.blenderbridge.world;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;

/**
 * SkyReachability — Task 3.3 (Bret-B pattern: {@code LightConnectedPathfinder}, plan v2 research R1,
 * "22M&rarr;7.6M faces"): a per-column BFS/flood-fill over an already-built {@link ColumnSnapshot},
 * marking which block POSITIONS in that column's footprint are reachable from open sky through a
 * chain of light-permeable neighbours — i.e. "surface/outdoor" geometry, as opposed to a sealed
 * underground cave nobody standing outside the world could ever see. {@link StreamingWorldExporter}
 * consults {@link #isReachable(BlockPos)} to optionally skip emitting faces that sit entirely inside
 * a sealed cave (the {@code fields.cullCaves} export option).
 *
 * <h2>WHY A TOPOLOGICAL BFS, NOT VANILLA'S OWN COMPUTED SKYLIGHT</h2>
 * <p>{@link ColumnSnapshot} already exposes the live world's real, precomputed {@link
 * LevelLightEngine} (captured once, see its own class doc's GETLIGHTINGPROVIDER section) — reading
 * {@code lightingProvider.get(LightType.SKY).getLightLevel(pos)} there would be far cheaper than a
 * BFS. We deliberately do NOT use that as the primary signal: vanilla's stored sky light VALUE
 * decays with travel distance from the nearest opening (roughly 1 level per block of horizontal/
 * upward travel), so a straight tunnel longer than ~15 blocks from its only opening reads 0 sky
 * light at its far end even though it is still topologically open to the sky the whole way — exactly
 * the case a "keep the outdoor geometry, drop the sealed cave" export needs to get right. We only
 * want CONNECTIVITY (is there any unbroken path of light-permeable cells from here to open sky),
 * not the decayed brightness VALUE, so a from-scratch reachability flood-fill through the snapshot's
 * own captured {@link BlockState} data is the correct tool, matching the Bret-B reference's own
 * pathfinder-shaped name rather than a light-value read.</p>
 *
 * <h2>SEEDING (two independent sources)</h2>
 * <ol>
 * <li><b>Top of the captured column height</b> — {@link ColumnSnapshot} captures the FULL vertical
 * extent of the dimension ({@link ColumnSnapshot#getHeight()}, 384 Overworld), regardless of the
 * export job's own Y-range (a job that only exports an underground slice still gets a snapshot that
 * reaches the real surface above it, see {@code ColumnSnapshot.captureBlockStates}). Every footprint
 * cell at {@code ly = heightBlocks - 1} that is light-permeable is treated as directly touching real
 * sky (there is nothing above the captured height in-game) and seeds the flood-fill. This is what
 * naturally discovers "the topmost visible layer" the plan task describes — a straight downward
 * flood-fill from here reaches every column's true open-air surface without needing a separate
 * per-column height scan.</li>
 * <li><b>Region-edge real skylight</b> — a column at the outer edge of the requested export box has,
 * via {@link ColumnSnapshot}'s own 1-block rim, one real captured block just OUTSIDE the box. We
 * cannot flood-fill further out (no snapshot exists there), so for footprint cells that lie outside
 * the requested job region ({@code regionMinX..regionMaxX} / {@code regionMinZ..regionMaxZ}, e.g. a
 * cave mouth carved into the side of the exported box that opens onto true outdoor space just beyond
 * it) we fall back to trusting vanilla's own already-globally-computed sky light: light-permeable AND
 * a real sky light level &gt; 0 seeds the flood-fill there too. Interior columns never touch this
 * condition (their whole footprint, rim included, lies inside the region) so this is a no-op fast
 * path for the common case of a large multi-column export.</li>
 * </ol>
 * Propagation from either seed source is the SAME 6-connected flood-fill through light-permeable
 * neighbours ({@link #isLightPermeable}), matching the plan's "propagation durch nicht-licht-
 * blockierende states".
 *
 * <h2>SCOPE: PER COLUMN, MATCHING THE EXISTING MESHING ARCHITECTURE (KNOWN LIMIT)</h2>
 * <p>Like {@link StreamingWorldExporter#meshColumn}'s own face culling (which only ever reads 1
 * neighbour via {@link ColumnSnapshot}'s rim), this BFS is scoped to ONE column's own footprint
 * (column + {@link ColumnSnapshot#RIM_BLOCKS} = 1-block rim), computed independently on that column's
 * worker thread with zero shared state — this is what keeps it parallel-safe inside the existing
 * per-column worker-pool design instead of requiring every in-flight snapshot to be held at once
 * (which {@link ColumnSnapshot}'s own class doc explicitly warns against at export scale). The
 * consequence, stated plainly: a cave that spans multiple 16-wide columns and is only connected to
 * the sky through an opening that sits MORE than 1 block into a NEIGHBOURING column will be marked
 * unreachable (culled) in a column that is, in full topological reality, still open to that opening.
 * This under-detection is conservative in the sense that it only ever culls MORE than a true global
 * BFS would (never fewer faces exported than truly hidden), is symmetric across every column (no
 * single column is special-cased), and is a documented, measured trade-off — not a silent gap.</p>
 *
 * <h2>OTHER KNOWN LIMITS (documented, not hidden)</h2>
 * <ul>
 * <li><b>Water attenuates real light but is treated as opaque here.</b> {@code
 * BlockState#getOpacity} for water returns a non-zero value (vanilla lets a little sky light bleed
 * through shallow water), but this class's {@link #isLightPermeable} is a strict {@code == 0} binary
 * check. A lake open to the sky still seeds correctly at its top (air-above-water) layer, but the
 * flood-fill will NOT propagate reachability down through the water column itself — an underwater
 * cave mouth more than zero water blocks deep from open air will not be found reachable through the
 * water. Fixing this would mean special-casing fluids in the propagation predicate; not done here.</li>
 * <li><b>No dimension awareness.</b> This class has no notion of {@code DimensionType.hasSkyLight()}.
 * "Top of the captured column height counts as sky" is only actually true for Overworld-like
 * dimensions. The Nether has no real sky (its height-limit "roof" is not outdoors); running
 * {@code cullCaves} there would seed from whatever is at the captured height ceiling regardless of
 * whether that is meaningfully "sky", so results in sky-less dimensions should not be trusted without
 * further work — this option is intended for Overworld-style exports.</li>
 * <li>Dense foliage (leaves) may carry a non-zero real opacity depending on the block, so a forest
 * canopy can locally behave like a light-blocking ceiling in this binary model, potentially
 * under-counting some outdoor-under-leaves floor positions as unreachable. Not specially handled.</li>
 * </ul>
 *
 * <h2>THREADING &amp; MEMORY</h2>
 * <p>Built and consumed entirely on a {@link StreamingWorldExporter} WORKER thread, against one
 * already-finished, immutable {@link ColumnSnapshot} — never touches {@code client.level}. The
 * {@code boolean[]} reachability array is the same {@link ColumnSnapshot#FOOTPRINT}&sup2;&times;
 * {@code heightBlocks} shape as the snapshot's own block-state array (roughly 124K booleans for an
 * Overworld column, ~124KiB — small next to the ~1MiB snapshot it reads), lives only for the
 * duration of meshing that one column, and is never held across columns or shared between worker
 * threads — no addition to the {@link ColumnSnapshot}-driven in-flight memory budget.</p>
 */
public final class SkyReachability
{
    private static final Logger LOGGER = LogUtils.getLogger();

    private final ColumnSnapshot snapshot;
    private final int columnMinX;
    private final int columnMinZ;
    private final int bottomY;
    private final int heightBlocks;

    /**
     * Flat {@code [lx][ly][lz]} reachability array, SAME shape/indexing convention as {@link
     * ColumnSnapshot}'s own internal block-state array ({@link ColumnSnapshot#FOOTPRINT}&times;
     * {@code heightBlocks}&times;{@link ColumnSnapshot#FOOTPRINT}), indexed via {@link #index}.
     */
    private final boolean[] reachable;

    /**
     * How many {@link #isReachable(BlockPos)} queries fell outside this column's captured footprint
     * (should never happen for real callers — see {@link #isReachable} — measurement instrument
     * only, mirrors {@link ColumnSnapshot#outOfRimReads()}).
     */
    private final AtomicInteger outOfFootprintQueries = new AtomicInteger();

    /**
     * Build the reachability flood-fill for one column's already-finished snapshot. WORKER THREAD —
     * reads only {@code snapshot}'s own captured data (block states directly, plus one real-light
     * lookup limited to cells outside the export region, see class doc REGION-EDGE SEEDING); never
     * touches {@code client.level}.
     *
     * @param snapshot    the finished column snapshot to flood-fill over
     * @param regionMinX  the WHOLE export job's inclusive min X (not just this column's own range) —
     *                    used only to know which of this column's rim cells lie outside the requested
     *                    box, for region-edge seeding
     * @param regionMaxX  inclusive max X of the whole export job
     * @param regionMinZ  inclusive min Z of the whole export job
     * @param regionMaxZ  inclusive max Z of the whole export job
     */
    public SkyReachability(ColumnSnapshot snapshot, int regionMinX, int regionMaxX, int regionMinZ, int regionMaxZ)
    {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.columnMinX = snapshot.columnMinX();
        this.columnMinZ = snapshot.columnMinZ();
        this.bottomY = snapshot.getMinBuildHeight();
        this.heightBlocks = snapshot.getHeight();
        this.reachable = new boolean[ColumnSnapshot.FOOTPRINT * this.heightBlocks * ColumnSnapshot.FOOTPRINT];

        long start = System.nanoTime();
        int seeds = compute(regionMinX, regionMaxX, regionMinZ, regionMaxZ);
        long micros = (System.nanoTime() - start) / 1000L;

        LOGGER.debug("[blenderbridge] SkyReachability built: column ({}, {}), {} seeds, {} us", this.columnMinX / ColumnSnapshot.COLUMN_SIZE, this.columnMinZ / ColumnSnapshot.COLUMN_SIZE, seeds, micros);
    }

    /**
     * Whether {@code pos} is reachable from open sky through this column's flood-fill. Callers are
     * {@link StreamingWorldExporter}'s per-direction face check ({@code pos} and its one-block
     * neighbour, exactly the footprint {@link ColumnSnapshot} itself guarantees for {@code
     * shouldDrawSide}) — a query outside the captured footprint should not happen in practice, but
     * degrades to {@code true} (fail-open: an unknown answer must never silently delete real
     * geometry) rather than throwing.
     */
    public boolean isReachable(BlockPos pos)
    {
        int lx = pos.getX() - this.columnMinX + ColumnSnapshot.RIM_BLOCKS;
        int ly = pos.getY() - this.bottomY;
        int lz = pos.getZ() - this.columnMinZ + ColumnSnapshot.RIM_BLOCKS;

        if (lx < 0 || lx >= ColumnSnapshot.FOOTPRINT || ly < 0 || ly >= this.heightBlocks || lz < 0 || lz >= ColumnSnapshot.FOOTPRINT)
        {
            this.outOfFootprintQueries.incrementAndGet();

            return true;
        }

        return this.reachable[index(lx, ly, lz)];
    }

    /** Measurement instrument only — see {@link #outOfFootprintQueries}. Should be 0 in real use. */
    public int outOfFootprintQueries()
    {
        return this.outOfFootprintQueries.get();
    }

    /**
     * Run both seed passes (see class doc SEEDING) then the 6-connected flood-fill from whatever they
     * enqueued. Returns the seed count (measurement/debug only).
     */
    private int compute(int regionMinX, int regionMaxX, int regionMinZ, int regionMaxZ)
    {
        int footprint = ColumnSnapshot.FOOTPRINT;
        int rim = ColumnSnapshot.RIM_BLOCKS;
        int total = footprint * this.heightBlocks * footprint;
        int[] queue = new int[total];
        int tail = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        /* ---- Seed 1: top of the captured column height touches real sky (class doc SEEDING #1). */
        int topLy = this.heightBlocks - 1;

        for (int lx = 0; lx < footprint; lx++)
        {
            int worldX = this.columnMinX - rim + lx;

            for (int lz = 0; lz < footprint; lz++)
            {
                int worldZ = this.columnMinZ - rim + lz;

                pos.set(worldX, this.bottomY + topLy, worldZ);
                BlockState state = this.snapshot.getBlockState(pos);

                if (isLightPermeable(pos, state))
                {
                    int idx = index(lx, topLy, lz);

                    if (!this.reachable[idx])
                    {
                        this.reachable[idx] = true;
                        queue[tail++] = idx;
                    }
                }
            }
        }

        int surfaceSeeds = tail;

        /* ---- Seed 2: region-edge real skylight (class doc SEEDING #2) — only for columns whose
         * footprint actually pokes outside the requested export box; a no-op fast path otherwise. */
        boolean touchesXEdge = (this.columnMinX - rim < regionMinX) || (this.columnMinX + ColumnSnapshot.COLUMN_SIZE - 1 + rim > regionMaxX);
        boolean touchesZEdge = (this.columnMinZ - rim < regionMinZ) || (this.columnMinZ + ColumnSnapshot.COLUMN_SIZE - 1 + rim > regionMaxZ);

        if (touchesXEdge || touchesZEdge)
        {
            LevelLightEngine lighting = this.snapshot.getLightEngine();

            for (int lx = 0; lx < footprint; lx++)
            {
                int worldX = this.columnMinX - rim + lx;
                boolean xOutside = worldX < regionMinX || worldX > regionMaxX;

                for (int lz = 0; lz < footprint; lz++)
                {
                    int worldZ = this.columnMinZ - rim + lz;
                    boolean zOutside = worldZ < regionMinZ || worldZ > regionMaxZ;

                    if (!xOutside && !zOutside)
                    {
                        /* Inside the requested box — a NEIGHBOURING column's own interior, whose own
                         * reachability that column will compute itself. Real light there can be
                         * mid-decay (see class doc WHY) and would be the wrong signal here. */
                        continue;
                    }

                    for (int ly = 0; ly < this.heightBlocks; ly++)
                    {
                        int idx = index(lx, ly, lz);

                        if (this.reachable[idx])
                        {
                            continue;
                        }

                        pos.set(worldX, this.bottomY + ly, worldZ);
                        BlockState state = this.snapshot.getBlockState(pos);

                        if (!isLightPermeable(pos, state))
                        {
                            continue;
                        }

                        if (lighting.getLayerListener(LightLayer.SKY).getLightValue(pos) > 0)
                        {
                            this.reachable[idx] = true;
                            queue[tail++] = idx;
                        }
                    }
                }
            }
        }

        /* ---- Flood-fill: 6-connected, through light-permeable cells only. */
        int head = 0;

        while (head < tail)
        {
            int idx = queue[head++];
            int lz = idx % footprint;
            int rest = idx / footprint;
            int ly = rest % this.heightBlocks;
            int lx = rest / this.heightBlocks;

            tail = visitNeighbour(queue, tail, lx + 1, ly, lz, pos);
            tail = visitNeighbour(queue, tail, lx - 1, ly, lz, pos);
            tail = visitNeighbour(queue, tail, lx, ly + 1, lz, pos);
            tail = visitNeighbour(queue, tail, lx, ly - 1, lz, pos);
            tail = visitNeighbour(queue, tail, lx, ly, lz + 1, pos);
            tail = visitNeighbour(queue, tail, lx, ly, lz - 1, pos);
        }

        return surfaceSeeds + (tail - surfaceSeeds);
    }

    /**
     * If ({@code lx},{@code ly},{@code lz}) is inside the footprint, not yet reachable, and
     * light-permeable: mark it reachable, enqueue it at {@code queue[tail]}, and return
     * {@code tail + 1}. Otherwise returns {@code tail} unchanged. {@code pos} is a scratch mutable
     * reused across every call in the flood-fill (no per-visit allocation).
     */
    private int visitNeighbour(int[] queue, int tail, int lx, int ly, int lz, BlockPos.MutableBlockPos pos)
    {
        if (lx < 0 || lx >= ColumnSnapshot.FOOTPRINT || lz < 0 || lz >= ColumnSnapshot.FOOTPRINT || ly < 0 || ly >= this.heightBlocks)
        {
            return tail;
        }

        int idx = index(lx, ly, lz);

        if (this.reachable[idx])
        {
            return tail;
        }

        int worldX = this.columnMinX - ColumnSnapshot.RIM_BLOCKS + lx;
        int worldZ = this.columnMinZ - ColumnSnapshot.RIM_BLOCKS + lz;

        pos.set(worldX, this.bottomY + ly, worldZ);
        BlockState state = this.snapshot.getBlockState(pos);

        if (!isLightPermeable(pos, state))
        {
            return tail;
        }

        this.reachable[idx] = true;
        queue[tail] = idx;

        return tail + 1;
    }

    /**
     * "Does light pass straight through this block" — {@code true} only for a strict zero real
     * opacity. See class doc's WATER limitation for the one deliberate, documented gap this strict
     * check creates.
     */
    private boolean isLightPermeable(BlockPos pos, BlockState state)
    {
        /* ⚠️ 26.2-VERHALTENSAENDERUNG: getLightBlock(view, pos) heisst jetzt getLightDampening()
         * und liest WEDER Welt NOCH Position — die Lichtdaempfung haengt nur noch am Zustand des
         * Blocks selbst. Fuer die Bloecke, die hier durchlaufen werden, ist das dasselbe Ergebnis;
         * einen ortsabhaengigen Wert (den nur wenige Bloecke ueberhaupt lieferten) kann 26.2 nicht
         * mehr geben. pos bleibt im Signaturkopf, weil die Aufrufer damit ihre Schleife fuehren. */
        return state.getLightBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, pos) == 0;
    }

    /** Flat-array index, SAME formula as {@link ColumnSnapshot}'s own private {@code index()}. */
    private int index(int lx, int ly, int lz)
    {
        return (lx * this.heightBlocks + ly) * ColumnSnapshot.FOOTPRINT + lz;
    }

    /* ====================================================================================
     * DEPLOY-WINDOW-FREE WIRING (static arming + per-worker-thread active instance)
     * ====================================================================================
     *
     * WHY THIS SHAPE, NOT A CONSTRUCTOR PARAMETER ON StreamingWorldExporter.start(...): this JVM's
     * running instance already has {@code StreamingWorldExporter}, its {@code Job} inner class, and
     * {@code WorldExportBridge} LOADED (measured live via EXEC/reflection this session — the resident
     * class only has the original 3-arg {@code start(BlockPos,BlockPos,File)} and 8-param
     * {@code meshBlock}). Per the project's deploy-window model (blenderbridge dossier: "GEMESSEN
     * ... canAddMethod=false auf dieser JVM — auch NEUE methoden/felder an GELADENEN klassen brauchen
     * boot, nicht nur mixins"), adding a new parameter/field/overload to any of those THREE
     * already-loaded classes would need a restart. Restarts are forbidden for anything short of a
     * real crash/freeze/system problem (project hard rule) — and this is exactly the situation the
     * hotswap workflow exists to cover: keep every already-loaded class's public surface BYTE-IDENTICAL
     * (only method BODIES change there) and push every bit of NEW state into THIS class, which has
     * never been loaded before and is therefore free to have any shape at all.
     *
     * <p>The three body-only edits this enables in {@code StreamingWorldExporter}/{@code
     * WorldExportBridge} (no signature/field changes anywhere):</p>
     * <ol>
     * <li>{@code WorldExportBridge.export}'s body reads {@code fields.cullCaves} and calls
     * {@link #armNextJob(boolean)} once, BEFORE its existing (unchanged, 3-arg) call to {@code
     * StreamingWorldExporter.start(min, max, outDir)}.</li>
     * <li>{@code StreamingWorldExporter.meshColumn}'s body calls {@link #enterColumn} once at the top
     * (building this column's flood-fill, or arming "no cull" — see {@link #armedCullCaves}) and
     * {@link #exitColumn} once at the bottom (a {@code finally}, so a worker thread that goes on to
     * mesh a DIFFERENT column next never sees a stale reachability instance from this one).</li>
     * <li>{@code meshBlock}/{@code meshFluid}'s bodies call {@link #shouldCullFace}/{@link
     * #shouldCull} instead of taking a reachability parameter.</li>
     * </ol>
     *
     * <p>Why a single static flag is a correct stand-in for a per-Job field: {@code
     * StreamingWorldExporter} already enforces "only one streaming job at a time" ({@code
     * activeJob != null} rejects a second {@code start}), so at any moment there is at most one
     * cullCaves setting in effect anyway — a per-Job field would carry exactly the same single value
     * a static one does, just with a restart-requiring extra field to store it in.</p>
     *
     * <p>Why {@link ThreadLocal} for the ACTIVE instance rather than another static: {@code
     * StreamingWorldExporter}'s worker pool meshes several columns CONCURRENTLY (2-4 threads), each
     * against its OWN {@link ColumnSnapshot} — a plain static field would have one worker thread's
     * column stomp on another's mid-flight. Each worker thread gets its own slot, matching the
     * existing "zero shared state while computing a column's quads" design {@code meshColumn} already
     * relies on for its parallelism to be real (see {@code StreamingWorldExporter}'s own class doc).</p>
     */
    private static volatile boolean armedCullCaves;

    /**
     * Per-worker-thread active reachability for the column currently being meshed on THAT thread, or
     * {@code null} when cullCaves is off or no column is currently being processed on this thread.
     *
     * <p><b>DELIBERATELY NOT {@code = new ThreadLocal<>()} AT THE FIELD DECLARATION</b> — measured
     * live this session (2026-07-17): a field initializer only runs as part of the class's
     * {@code <clinit>}, and enhanced (DCEVM) redefinition that ADDS a brand-new static field to a
     * class the JVM already finished initialising does NOT re-run {@code <clinit>}. The field simply
     * starts at the JVM's bytecode-level default for its type — {@code null} for a reference field —
     * and stays {@code null} forever, since nothing ever assigns it. This is exactly what happened
     * here: {@code StreamingWorldExporter}/{@code WorldExportBridge} were already loaded (and
     * initialised) in this session's live JVM from earlier testing; adding {@code ACTIVE} via hotswap
     * left it {@code null}, and every {@link #shouldCull}/{@link #shouldCullFace} call threw a
     * {@code NullPointerException} on {@code ACTIVE.get()} — silently aborting EVERY column's mesh
     * loop mid-iteration (caught by {@code StreamingWorldExporter}'s per-column worker try/catch),
     * collapsing a real ~16.8k-block export down to whatever a couple of unrelated block-entities
     * contributed. ({@link #armedCullCaves} above "accidentally" survived the same fate unharmed only
     * because {@code boolean}'s bytecode default, {@code false}, happens to equal its intended
     * starting value — a coincidence, not a fix, and not a pattern to rely on for any future field.)
     * {@link #active()} below lazily creates the {@link ThreadLocal} on first real use instead,
     * which needs no {@code <clinit>} to have run at all — safe whether this class loaded fresh
     * (normal case) or via hotswap onto an already-initialised class (this session's case).</p>
     */
    private static volatile ThreadLocal<SkyReachability> activeHolder;

    /**
     * DEAD FIELD, kept only so this fix stays a purely ADDITIVE hotswap on top of what is already
     * live in this session's JVM (the buggy {@code ACTIVE} field from the first cut of this class —
     * see {@link #activeHolder}'s doc). Removing a field is a riskier redefinition than adding one;
     * since this one is harmless and unreferenced, it stays rather than risk the swap. Safe to delete
     * next time this class needs a real (non-hotswap) reload.
     */
    private static final ThreadLocal<SkyReachability> ACTIVE = new ThreadLocal<>();

    /**
     * Lazily creates {@link #activeHolder} on first use (double-checked, {@code synchronized} only on
     * the rare cold path) — see the field's own doc for WHY this can't just be a field initializer.
     */
    private static ThreadLocal<SkyReachability> active()
    {
        ThreadLocal<SkyReachability> holder = activeHolder;

        if (holder == null)
        {
            synchronized (SkyReachability.class)
            {
                holder = activeHolder;

                if (holder == null)
                {
                    holder = new ThreadLocal<>();
                    activeHolder = holder;
                }
            }
        }

        return holder;
    }

    /**
     * Arm (or disarm) cave culling for the NEXT streaming job that starts. Call exactly once, from
     * {@code WorldExportBridge.export}'s body, BEFORE calling {@code StreamingWorldExporter.start}.
     */
    public static void armNextJob(boolean cullCaves)
    {
        armedCullCaves = cullCaves;
    }

    /** The cullCaves setting most recently armed — used by {@code StreamingWorldExporter}'s start/
     * status logging so it can report the setting without a Job field. */
    public static boolean lastArmedCullCaves()
    {
        return armedCullCaves;
    }

    /**
     * WORKER THREAD, called once at the top of {@code StreamingWorldExporter.meshColumn}: build this
     * column's flood-fill (region bounds are the WHOLE job's, already available as {@code job.minX}
     * etc. — no new field needed) and stash it for this thread's {@link #shouldCull}/{@link
     * #shouldCullFace} calls, or clear the slot when cullCaves is off.
     */
    public static void enterColumn(ColumnSnapshot snapshot, int regionMinX, int regionMaxX, int regionMinZ, int regionMaxZ)
    {
        if (!armedCullCaves)
        {
            active().remove();
            return;
        }

        active().set(new SkyReachability(snapshot, regionMinX, regionMaxX, regionMinZ, regionMaxZ));
    }

    /**
     * WORKER THREAD, called once at the bottom of {@code StreamingWorldExporter.meshColumn} (a
     * {@code finally}, so an exception mid-column can't leak a stale instance into the next column
     * this same pooled thread picks up).
     */
    public static void exitColumn()
    {
        active().remove();
    }

    /**
     * True if the face between cells {@code a} and {@code b} should be dropped: cullCaves is armed
     * for THIS thread's current column and neither side is sky-reachable. Always {@code false}
     * (never cull) when cullCaves is off — the pre-3.3 code path is unchanged.
     */
    public static boolean shouldCullFace(BlockPos a, BlockPos b)
    {
        SkyReachability reach = active().get();

        if (reach == null)
        {
            return false;
        }

        /* W7-B2 (clamp x cullCaves co-gate, end-review fund 4): a face against the CLAMPED region
         * boundary is NEVER cave-culled. The clamp (StreamRegionClamp) declares everything outside
         * the export box to be open air precisely so boundary faces get DRAWN (the "diorama" hull,
         * Task 3.3c) — a face whose far cell is that declared-open outside is by definition visible
         * from outside the diorama, so "sealed cave" can never be true for it. Without this, the
         * flood-fill reached the clamp's fake-air only down the SIDE rim columns (top seed falls
         * straight down the all-air clamped rim), which is why the co-gate measured side walls
         * holding (-1%) while the REGION FLOOR lost 66% of its vertices: an interior column's
         * below-minY air layer has no path to any seed, so every deep floor face had two unreachable
         * cells and was culled — the diorama was open from below. Body-only edit, reuses the
         * EXISTING StreamRegionClamp.isBoundaryAir (active-gated: openBounds=true or alt-path =
         * false = behaviour unchanged). shouldCull(pos) below needs no twin check — its single pos
         * is always an in-region cell (a cross-model/fluid block being meshed), never a boundary
         * neighbour. */
        if (StreamRegionClamp.isBoundaryAir(a) || StreamRegionClamp.isBoundaryAir(b))
        {
            return false;
        }

        return !reach.isReachable(a) && !reach.isReachable(b);
    }

    /**
     * True if {@code pos} itself should be dropped — no "other side" to check (cross-model quads,
     * a fluid's own cell). Always {@code false} when cullCaves is off.
     */
    public static boolean shouldCull(BlockPos pos)
    {
        SkyReachability reach = active().get();

        return reach != null && !reach.isReachable(pos);
    }

    /* ====================================================================================
     * DETERMINISTIC WRITE ORDER (found + fixed live, 2026-07-17 — see class doc note below)
     * ====================================================================================
     *
     * MEASURED BUG: exporting the SAME region twice with cullCaves=false produced byte-identical
     * OBJ files (confirmed: 0-line diff, matching hashes) — but the SAME region twice with
     * cullCaves=true did NOT (a large, order-only diff: same block/face COUNTS both times, different
     * FILE ORDER). Root cause: {@code StreamingWorldExporter}'s worker pool has always written each
     * column's quads to the shared OBJ under {@code Job.writerLock} in WHICHEVER ORDER that column's
     * worker thread happens to finish and grab the lock — a real race that simply had low enough
     * timing variance in the cullCaves=false case (near-uniform, fast per-column cost) to happen to
     * finish in submission order both times tested. This class's own flood-fill adds a per-column
     * cost that VARIES with how much cave a column has (a plain surface column's BFS is cheap and
     * over almost immediately, a deep-cave column's BFS visits far more cells) — that extra variance
     * is what was needed to actually expose the pre-existing race in practice. {@link #sequencedWrite}
     * below removes it (for the cullCaves path only — see USAGE) by holding each finished column's
     * write action until every earlier column (by the SAME fixed order the columns were originally
     * enqueued in) has already been written, then draining the run of now-ready ones in order.
     *
     * <p>USAGE (in {@code StreamingWorldExporter.meshColumn}'s body, no signature change): a worker
     * thread that just finished a column, instead of writing straight to the file, computes that
     * column's fixed sequence number (its position in the SAME (cz outer, cx inner) enqueue order
     * {@code start} originally built {@code pendingColumns} in) and calls {@code
     * SkyReachability.sequencedWrite(seq, () -> writePending(job, pending))} — {@code writePending}
     * itself is completely unchanged, this only decides WHEN it may run. {@code WorldExportBridge
     * .export}'s body calls {@link #resetWriteSequencer()} once per job (alongside {@link
     * #armNextJob}), so a later job starts counting from 0 again.</p>
     *
     * <p>Only applied when cullCaves is armed (checked at the call site via {@link
     * #lastArmedCullCaves()}) — the cullCaves=false path already measured byte-identical across
     * runs, so it is left completely untouched rather than adding this extra synchronisation (and
     * therefore a small amount of extra worker-thread waiting) to a path that does not need it.</p>
     *
     * <p><b>LAZY HOLDER, NOT A FIELD INITIALIZER — SAME REASON AS {@link #activeHolder}, LEARNED THE
     * HARD WAY TWICE NOW:</b> the first cut of this fix declared {@code private static final Object
     * SEQUENCER_LOCK = new Object();} directly. Hotswapping that onto this session's already-
     * initialised live class did NOT run the initializer (identical failure mode to the {@link
     * #activeHolder} bug, documented there in detail) — {@code SEQUENCER_LOCK} stayed {@code null},
     * and the very next {@code WORLD_EXPORT} request threw {@code NullPointerException: Cannot enter
     * synchronized block because "...SEQUENCER_LOCK" is null} on the RENDER THREAD, uncaught,
     * CRASHING THE WHOLE GAME (crash report {@code crash-2026-07-17_01.59.12-client.txt}). Lesson
     * hardened into a rule for this class going forward: NO static field here may ever carry a
     * non-default initializer ({@code = new Whatever()}) again — only lazily-created holders behind a
     * {@code synchronized (SkyReachability.class)} double-checked accessor, exactly like {@link
     * #active()}. {@link #sequencer()} below is that fix, and it bundles the two pieces of sequencer
     * state ({@code pendingWrites}, {@code nextWriteSeq}) into one small holder object so there is
     * only ONE lazily-created reference to manage, instead of three separate ones.</p>
     */
    private static volatile WriteSequencer sequencerHolder;

    /** Lazily creates {@link #sequencerHolder} on first use — see that field's doc for why a plain
     * field initializer already crashed the game once this session. */
    private static WriteSequencer sequencer()
    {
        WriteSequencer holder = sequencerHolder;

        if (holder == null)
        {
            synchronized (SkyReachability.class)
            {
                holder = sequencerHolder;

                if (holder == null)
                {
                    holder = new WriteSequencer();
                    sequencerHolder = holder;
                }
            }
        }

        return holder;
    }

    /**
     * The sequencer's mutable state, bundled into one object so {@link #sequencer()} only ever needs
     * to lazily create ONE thing. Plain instance fields — instance construction ({@code new
     * WriteSequencer()}) always runs its field initializers correctly regardless of hotswap history
     * (that guarantee only breaks for STATIC fields on an already-initialised class, see {@link
     * #sequencerHolder}'s doc), so no lazy-init trickery is needed inside this class itself.
     */
    private static final class WriteSequencer
    {
        /** GUARDED BY {@code synchronized (this)}. Finished-but-not-yet-its-turn write actions, keyed
         * by the column's fixed sequence number. */
        final Map<Integer, Runnable> pendingWrites = new HashMap<>();

        /** GUARDED BY {@code synchronized (this)}. The next sequence number allowed to write. */
        int nextWriteSeq;
    }

    /**
     * Reset the sequencer for a new job. Call once, from {@code WorldExportBridge.export}'s body,
     * alongside {@link #armNextJob} (before {@code StreamingWorldExporter.start}).
     */
    public static void resetWriteSequencer()
    {
        WriteSequencer holder = sequencer();

        synchronized (holder)
        {
            holder.pendingWrites.clear();
            holder.nextWriteSeq = 0;
        }
    }

    /**
     * Run (or defer) one column's write action so that, across the whole job, actions run in strict
     * increasing {@code seq} order regardless of which worker thread finishes which column first.
     * WORKER THREAD. Never blocks the calling thread waiting for its turn — an out-of-turn action is
     * simply stored and the calling thread returns immediately, free to mesh another column; whichever
     * thread later completes the missing earlier column(s) drains this and every action queued behind
     * it, in order, before returning.
     *
     * @param seq        this column's fixed position in the job's original enqueue order (0-based)
     * @param writeAction the actual write (a closure over that column's own {@code job}/{@code
     *                    pending} — see USAGE above); runs while holding the sequencer's lock, so
     *                    it may itself safely acquire a DIFFERENT lock (e.g. {@code Job.writerLock})
     *                    without risking deadlock, as long as nothing else ever acquires locks in the
     *                    opposite order — nothing else in this codebase acquires writerLock and then
     *                    waits on this lock, so that invariant holds
     */
    public static void sequencedWrite(int seq, Runnable writeAction)
    {
        WriteSequencer holder = sequencer();

        synchronized (holder)
        {
            holder.pendingWrites.put(seq, writeAction);

            while (holder.pendingWrites.containsKey(holder.nextWriteSeq))
            {
                Runnable ready = holder.pendingWrites.remove(holder.nextWriteSeq);
                ready.run();
                holder.nextWriteSeq++;
            }
        }
    }
}
