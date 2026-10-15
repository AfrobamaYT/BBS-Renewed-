// Coded by Afrobama/Claude — Block-Spawn Animation FX for the 1m
// pillager-manhunt Grox-style cinematic. Drops every non-air block in
// a region from +10 b above its final resting spot with a per-block
// stagger that radiates outward from the region center, eased with a
// cubic ease-out so the impact feels weighty. Pure client-side render
// FX — does NOT touch real world blocks; assumes the region has
// already been built (the camera sees the real blocks AND our ghost
// blocks animating into them, which is the desired "blocks spawn into
// place" gag). For a cleaner reveal, the user can pair this with
// /bbstools blockanim to fly the real blocks away first.
//
// Render path mirrors SchematicPreview.java: WorldRenderEvents.
// AFTER_TRANSLUCENT → push matrix → translate to interpolated world
// pos → vanilla BlockRenderManager.getModelRenderer().render() with
// the entity vertex consumer + immediate flush. This keeps Iris/
// Sodium happy (we use the standard SubmitNodeCollector pipeline) and
// inherits resource-pack textures, biome tints, and block models for
// free. Block-rendering throws on some virtual positions
// (e.g. cross-shaped plants probing neighbor states) so each block
// is wrapped in try/catch like SchematicPreview does.
//
// Lifecycle: trigger() collects the blocks ONCE (so the region can
// be modified mid-animation without changing what's rendering),
// onRender() advances a tick counter implicitly via System.nanoTime
// at render time → t in [0,1] for each block, eased, applied as
// vertical offset. When elapsed >= duration + max stagger, the
// animation list clears itself on the next render. No background
// thread, no per-tick listener — just render hook + lazy expiry.
package com.afrobama.bbstools.client.blockspawn;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.RenderType;
//? if >=26.1 {
/*import net.minecraft.client.renderer.block.BlockQuadOutput;
*///?}
import mchorse.bbs_mod.compat.alt.BlockStateModelSet;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class BlockSpawnAnimEffect
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/blockspawn");

    /** How far above its final position each block starts. 10 b matches
     *  the "blocks pop in from the sky" feel the user described — high
     *  enough to be visibly dramatic, low enough that the drop completes
     *  inside even the shortest 0.5 s durations. */
    private static final double DROP_HEIGHT = 10.0D;

    /** Per-block tick stagger granularity. 1 tick per block of distance
     *  from the region center keeps things tight on small regions but
     *  scales gracefully on huge ones — a 50³ region's corners delay
     *  ~50 t (~2.5 s) which lines up nicely with the typical 30-60 t
     *  spawn duration used for the cinematic. Clamped 1-2 to honour
     *  the spec; we step in 0.5 t increments via distance / 2. */
    private static final double STAGGER_TICKS_PER_BLOCK = 1.5D;

    /** Render culling distance — same 96 b as SchematicPreview. Blocks
     *  beyond this from the camera are skipped per-frame; cheap distance
     *  check, avoids wasting fillrate on stuff the user can't see. */
    private static final double MAX_RENDER_DISTANCE_SQ = 96.0D * 96.0D;

    /** Hard cap on captured blocks. 20 000 matches SchematicPreview's
     *  internal limit and prevents accidental /bbstools blockspawn 64
     *  (1 048 576 positions) from OOMing the client. Anything beyond
     *  this is truncated with a log warning. */
    private static final int MAX_BLOCKS = 20_000;

    /** Per-block animation entry. Final field set, immutable after
     *  trigger() — render thread only reads, no synchronization needed
     *  beyond the volatile list ref + volatile expiry tick. */
    private static final class Entry
    {
        final BlockPos pos;
        final BlockState state;
        final int delayTicks;

        Entry(BlockPos pos, BlockState state, int delayTicks)
        {
            this.pos = pos;
            this.state = state;
            this.delayTicks = delayTicks;
        }
    }

    private static volatile List<Entry> activeAnim;
    /** System nanoTime at trigger; used to derive elapsed ticks at render
     *  time. We don't piggy-back on a client-tick listener because we
     *  want sub-tick smoothness in the drop animation — System.nanoTime
     *  / 50ms gives us a fractional tick value that eases cleanly. */
    private static volatile long animStartNanos;
    private static volatile int animDurationTicks;
    private static volatile int animMaxDelayTicks;

    private BlockSpawnAnimEffect() {}

    public static void register()
    {
        /* 26.2: EINREICHEN muss in der Sammelphase geschehen. AFTER_TRANSLUCENT_TERRAIN
         * gibt es zwar noch (javap auf WorldRenderEvents), aber dort sind die
         * Feature-Renderer schon durch -- eingereichte Geometrie wird dann still nicht
         * mehr gezeichnet. Die Zeichenreihenfolge bestimmt ohnehin der RenderType,
         * nicht der Haken. */
        WorldRenderEvents.AFTER_ENTITIES.register(BlockSpawnAnimEffect::onRender);
        LOG.info("BlockSpawnAnimEffect registered — render hook armed");
    }

    /**
     * Capture every non-air block in the inclusive AABB [min, max] and
     * begin a per-block staggered drop-in animation that lasts
     * {@code durationTicks} ticks once each block's stagger delay has
     * elapsed. Center-out radial stagger: blocks closest to the region
     * center start first; corners arrive last. Safe to call from the
     * main thread (e.g. command dispatch); render hook picks the new
     * animation up on the next frame.
     *
     * @param min              inclusive min corner of the region
     * @param max              inclusive max corner of the region
     * @param durationTicks    per-block fall duration (after its stagger
     *                         delay). Clamped to ≥1 t to avoid div-by-0.
     */
    public static void trigger(BlockPos min, BlockPos max, int durationTicks)
    {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel world = mc.level;

        if (world == null)
        {
            LOG.warn("trigger() called with no client world — ignoring");
            return;
        }

        /* Normalize the corners so callers can pass them in either
         *  order (the command may give us min/max in the wrong order
         *  if the player is north-west of where they think they are). */
        int x0 = Math.min(min.getX(), max.getX());
        int y0 = Math.min(min.getY(), max.getY());
        int z0 = Math.min(min.getZ(), max.getZ());
        int x1 = Math.max(min.getX(), max.getX());
        int y1 = Math.max(min.getY(), max.getY());
        int z1 = Math.max(min.getZ(), max.getZ());

        double cx = (x0 + x1) * 0.5D;
        double cy = (y0 + y1) * 0.5D;
        double cz = (z0 + z1) * 0.5D;

        List<Entry> collected = new ArrayList<>();
        int scanned = 0;
        int truncated = 0;
        int maxDelay = 0;

        outer:
        for (int y = y0; y <= y1; y++)
        {
            for (int z = z0; z <= z1; z++)
            {
                for (int x = x0; x <= x1; x++)
                {
                    scanned++;
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = world.getBlockState(pos);

                    if (state.isAir())
                    {
                        continue;
                    }

                    double dx = (x + 0.5D) - cx;
                    double dy = (y + 0.5D) - cy;
                    double dz = (z + 0.5D) - cz;
                    double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    int delay = (int) Math.floor(dist * (STAGGER_TICKS_PER_BLOCK / 2.0D));
                    if (delay > maxDelay) maxDelay = delay;

                    collected.add(new Entry(pos, state, delay));

                    if (collected.size() >= MAX_BLOCKS)
                    {
                        truncated = 1;
                        break outer;
                    }
                }
            }
        }

        int clampedDuration = Math.max(1, durationTicks);

        /* Publish under one volatile write so the render thread sees a
         *  consistent (list, start, duration, maxDelay) tuple. The
         *  individual fields are independently volatile but the list
         *  is the "owning" reference — renderers null-check it first. */
        animStartNanos = System.nanoTime();
        animDurationTicks = clampedDuration;
        animMaxDelayTicks = maxDelay;
        activeAnim = collected;

        LOG.info("trigger min={} max={} duration={}t blocks={} (scanned={}, truncated={}) maxDelay={}t",
            min, max, clampedDuration, collected.size(), scanned, truncated, maxDelay);
    }

    /** Force-clear any running animation. Called by /bbstools blockspawn
     *  clear and on world unload (BBSToolsModClient lifecycle hook can
     *  invoke this; until wired explicitly, expiry handles it
     *  automatically). */
    public static void clear()
    {
        activeAnim = null;
    }

    /**
     * Append a single block to the currently-running animation (or start a
     * fresh one if nothing is active). Used by per-place feeders like
     * {@code CrowdBuildManager.SPAWN_ANIM_ENABLED} so each freshly-placed
     * block gets its own drop-in flair without the bulk-{@link #trigger}
     * AABB rescan. Thread-safe enough for main-thread callers — the render
     * thread iterates the list with a stable snapshot reference, so the
     * worst-case race is one frame where the new block isn't rendered yet.
     *
     * <p>Behaviour notes vs {@link #trigger}:
     * <ul>
     *   <li>Each call REUSES {@code animStartNanos} if an animation is
     *       already running, so the new block's drop is timed relative to
     *       the existing animation clock. delay is 0 → block starts
     *       falling immediately.</li>
     *   <li>If no animation is active, a fresh one is started with the
     *       given duration.</li>
     *   <li>The total-span / lazy-expiry check in {@link #onRender} reads
     *       {@code animMaxDelayTicks + animDurationTicks}. We bump
     *       {@code animDurationTicks} to {@code max(current, durationTicks)}
     *       and reset {@code animStartNanos} when the existing animation
     *       has already expired, so the new block always gets its full
     *       fall window.</li>
     * </ul>
     *
     * @param pos            target block position (world coords)
     * @param state          block state to render falling
     * @param durationTicks  fall duration in ticks (clamped ≥1)
     */
    public static void triggerSingle(BlockPos pos, BlockState state, int durationTicks)
    {
        if (state == null || state.isAir())
        {
            return;
        }
        int clampedDuration = Math.max(1, durationTicks);

        List<Entry> existing = activeAnim;
        long now = System.nanoTime();

        /* Check if the existing animation has already expired (the render
         *  thread hasn't observed it yet, or this call is racing the
         *  lazy-expiry write). Treat an expired-but-not-yet-cleared
         *  animation as "no animation" so the new block gets a fresh
         *  timing window and doesn't get instantly culled. */
        if (existing != null)
        {
            double existingElapsed = (now - animStartNanos) / 50_000_000.0D;
            double existingSpan = (double) animMaxDelayTicks + (double) animDurationTicks;
            if (existingElapsed >= existingSpan)
            {
                existing = null;
            }
        }

        if (existing == null)
        {
            /* Fresh animation — single block, zero stagger. */
            List<Entry> fresh = new ArrayList<>(1);
            fresh.add(new Entry(pos, state, 0));
            animStartNanos = now;
            animDurationTicks = clampedDuration;
            animMaxDelayTicks = 0;
            activeAnim = fresh;
            return;
        }

        /* Existing animation — figure out the elapsed-ticks at NOW so the
         *  new block's delay can be set such that its local-tick clock
         *  starts at 0 right now (i.e. delay = elapsed). This way the
         *  block falls relative to the moment it was registered, even
         *  though the global clock is older. */
        double elapsedTicks = (now - animStartNanos) / 50_000_000.0D;
        int delay = (int) Math.floor(elapsedTicks);

        /* Bump duration to whichever is longer (so the new entry's full
         *  fall fits inside the lazy-expiry window). Bump maxDelay if
         *  needed for the same reason. */
        if (clampedDuration > animDurationTicks) animDurationTicks = clampedDuration;
        if (delay > animMaxDelayTicks) animMaxDelayTicks = delay;

        /* Copy-on-write append: build a new list so any in-flight render
         *  pass continues to iterate the old immutable snapshot, then
         *  publish via the volatile ref. Avoids ConcurrentModification. */
        List<Entry> appended = new ArrayList<>(existing.size() + 1);
        appended.addAll(existing);
        appended.add(new Entry(pos, state, delay));
        activeAnim = appended;
    }

    public static boolean isActive()
    {
        return activeAnim != null;
    }

    /** True iff a trigger() has run but the lazy expiry hasn't fired yet.
     *  Exposed mostly for /bbsdebug visibility. */
    public static int activeCount()
    {
        List<Entry> a = activeAnim;
        return a == null ? 0 : a.size();
    }

    private static void onRender(net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext context)
    {
        List<Entry> anim = activeAnim;
        if (anim == null || anim.isEmpty())
        {
            return;
        }

        long startNanos = animStartNanos;
        int duration = animDurationTicks;
        int maxDelay = animMaxDelayTicks;

        /* Elapsed in fractional ticks. 50 ms per tick → divide nanos by
         *  50_000_000. Fractional value drives sub-tick smoothness;
         *  capping at the total animation span (maxDelay + duration)
         *  triggers lazy expiry below. */
        double elapsedTicks = (System.nanoTime() - startNanos) / 50_000_000.0D;
        double totalSpan = maxDelay + duration;

        if (elapsedTicks >= totalSpan)
        {
            /* Lazy expiry — done. Free the list so the GC reclaims the
             *  Entry array and we early-out next frame. */
            activeAnim = null;
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        ClientLevel world = mc.level;
        if (world == null) return;

        PoseStack matrices = context.matrixStack();
        if (matrices == null) return;

        Vec3 cam = context.gameRenderer().getLevelRenderState().cameraRenderState.pos;
        double camX = cam.x;
        double camY = cam.y;
        double camZ = cam.z;

        /* 26.2: das translate(-cam) ist ersatzlos entfallen -- der Zeichenweg unten rechnet
         * keine Matrix ein, die Kamera-Verschiebung steckt jetzt in den Blockecken selbst
         * (siehe Kommentar am submitCustomGeometry). Der Stapel bleibt gepusht, weil der
         * Sammler die Pose beim Einreichen kopiert. */
        matrices.pushPose();

        /* 26.2: der BlockRenderDispatcher ist weg; den ModelBlockRenderer baut man selbst.
         * (ambientOcclusion, cull, blockColors) -- dieselben Werte, mit denen der
         * Chunk-Uebersetzer ihn baut. Er ist ZUSTANDSBEHAFTET (eigene Teileliste, eigener
         * Wuerfel, Toenungs-Zwischenspeicher), also einer je Aufruf und nicht geteilt.
         * Den Wuerfel bringt er selbst mit; der frueher hier gehaltene RandomSource ist
         * ersatzlos entfallen, es zaehlt nur noch der Positions-Seed unten. */
        //? if >=26.1 {
        /*ModelBlockRenderer modelRenderer = new ModelBlockRenderer(true, true, mc.getBlockColors());
        *///?} else {
        ModelBlockRenderer modelRenderer = mc.getBlockRenderer().getModelRenderer();
        //?}
        BlockStateModelSet modelle = mc.getModelManager().getBlockStateModelSet();

        /* 26.2: renderBuffers().bufferSource() gibt es nicht mehr, und es gibt keinen Puffer
         * mehr zum Hineinschreiben. Die Geometrie wird als AUFTRAG eingereicht und spaeter
         * gezeichnet — darum steht der ganze Zeichenblock jetzt in einem Lambda; der Aufbau
         * selbst ist unveraendert.
         *
         * Der Sammler KOPIERT die Pose beim Einreichen (die Kamera-Verschiebung von oben
         * steckt also schon drin), das Lambda laeuft aber spaeter — der Kontext-Stapel darf
         * zu dem Zeitpunkt nicht mehr benutzt werden. tesselateBlock will jedoch einen
         * PoseStack und nicht die Pose, also faehrt der Block auf einem EIGENEN Stapel, der
         * aus der kopierten Pose (Position UND Normale) gesetzt wird.
         *
         * Rueckseiten-Culling ist kein globaler Schalter mehr (kein disableCull), sondern
         * gehoert zur Pipeline des RenderType — hier zu der von RenderType.translucentMovingBlock()
         * (RenderPipelines.TRANSLUCENT_BLOCK), genau der, die Vanilla fuer die
         * Translucent-Lage der Chunks faehrt. Unsere Geisterbloecke sind dieselben
         * Blockmodelle aus ModelBlockRenderer#tesselateBlock, nur um ein reines Translate
         * versetzt — Wicklung unveraendert. */
        SubmitNodeCollector sammler = new mchorse.bbs_mod.compat.gpu.SofortSammler(context.consumers());

        sammler.submitCustomGeometry(matrices, RenderType.translucentMovingBlock(), (entry, consumer) ->
        {
        //? if >=26.1 {
        /*BlockQuadOutput ausgabe = consumer::putBlockBakedQuad;
        *///?} else {
        /** Vor 26.1 schreibt tesselateBlock in den Verbraucher, unter einem Stapel aus der Pose. **/
        com.mojang.blaze3d.vertex.PoseStack stapel = new com.mojang.blaze3d.vertex.PoseStack();
        stapel.last().set(entry);
        //?}

        int rendered = 0;

        for (Entry e : anim)
        {
            /* Per-block local time: starts at 0 when delay expires,
             *  reaches 1 when (delay + duration) elapsed. Clamped to
             *  [0, 1] — t<0 means "not yet spawned, render at +DROP",
             *  t≥1 means "settled, render at final pos". */
            double localTicks = elapsedTicks - e.delayTicks;
            double t;
            if (localTicks <= 0.0D)
            {
                /* Hasn't started yet — keep block hovering at full
                 *  drop height. Renders the "queue" of upcoming blocks
                 *  visibly in the sky, which sells the "raining in"
                 *  effect better than them just popping into being. */
                t = 0.0D;
            }
            else if (localTicks >= duration)
            {
                /* Settled. We still render so the ghost overlays the
                 *  real block until the animation list clears — this
                 *  is invisible to the user (same blockstate, same pos)
                 *  but avoids a one-frame gap between "ghost gone" and
                 *  "real block fully lit". Cheap. */
                t = 1.0D;
            }
            else
            {
                t = localTicks / duration;
            }

            /* Cubic ease-out: 1 - (1-t)^3. Starts fast, settles soft —
             *  matches the "thunk" feel of a block landing under
             *  gravity better than linear or ease-in. */
            double inv = 1.0D - t;
            double eased = 1.0D - inv * inv * inv;

            /* Vertical offset: DROP_HEIGHT at t=0, 0 at t=1. */
            double yOffset = (1.0D - eased) * DROP_HEIGHT;

            double wx = e.pos.getX();
            double wy = e.pos.getY() + yOffset;
            double wz = e.pos.getZ();

            /* Distance cull vs camera, same as SchematicPreview. */
            double dx = wx + 0.5D - camX;
            double dy = wy + 0.5D - camY;
            double dz = wz + 0.5D - camZ;
            if (dx * dx + dy * dy + dz * dz > MAX_RENDER_DISTANCE_SQ)
            {
                continue;
            }

            try
            {
                //? if >=26.1 {
                /*modelRenderer.tesselateBlock(
                    ausgabe,
                    (float) (wx - camX), (float) (wy - camY), (float) (wz - camZ),
                    world, e.pos, e.state,
                    modelle.get(e.state),
                    e.state.getSeed(e.pos)
                );
                *///?} else {
                stapel.pushPose();
                stapel.translate(wx - camX, wy - camY, wz - camZ);
                modelRenderer.tesselateBlock(world,
                    modelle.get(e.state).collectParts(net.minecraft.util.RandomSource.create(e.state.getSeed(e.pos))),
                    e.state, e.pos, stapel, consumer, false, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
                stapel.popPose();
                //?}
            }
            catch (Throwable ignore)
            {
                /* Same defensive try/catch as SchematicPreview — cross-
                 *  shaped plants and a few other model types throw when
                 *  probing neighbour states at virtual positions. Skip
                 *  the offender, keep rendering everything else. */
            }

            rendered++;

            if (rendered >= MAX_BLOCKS)
            {
                break;
            }
        }
        });

        matrices.popPose();
    }

}
