package com.afrobama.bbstools.client.fracture;

import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Radial impact-point destruction propagator. When a fracture body
 * hits a block hard enough AND propagation is enabled on the clip,
 * we queue a {@link Event} centred at the impact block. Per client
 * tick the event's BFS frontier expands outward by N blocks per tick,
 * spawning {@link FracturePhysicsBridge#spawnDebrisChunkWithState}
 * particles for each non-air block as the wave reaches it.
 *
 * <p>Cinematic effect: the user throws a villager into a tree and
 * the impact site fragments first, then the surrounding wood "cracks
 * outward" tick-by-tick — much more dramatic than the entire tree
 * exploding in a single frame.</p>
 *
 * <h2>Algorithm</h2>
 * <ol>
 *   <li>Trigger registers an Event with origin, motion direction,
 *       max radius, blocks-per-tick rate, and a frontier queue
 *       seeded with the origin block</li>
 *   <li>Each tick we pop {@code blocksPerTick} entries from the
 *       frontier:
 *     <ul>
 *       <li>Skip if already visited or beyond {@link #manhattanDist}
 *           {@code &gt; maxRadius}</li>
 *       <li>If non-air: spawn block-shatter debris with velocity in
 *           the impact direction + outward radial component</li>
 *       <li>Queue all 6 face neighbours for the next round</li>
 *     </ul>
 *   </li>
 *   <li>Event ends when frontier empties or its per-event {@code lifetimeTicks} budget elapses</li>
 * </ol>
 *
 * <h2>World-state safety</h2>
 * <p>We do NOT call {@code World.removeBlock} or otherwise modify
 * world state — only spawn visual particles. BBS cinematic takes
 * shouldn't permanently destroy the user's build. To make blocks
 * actually disappear, the user can pair this with the BBSTools world
 * snapshot/restore flow (out of scope here).</p>
 */
public final class BlockShatterPropagator
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/fracture/propagator");

    /** Floor for per-event lifetime when the user picks small radii.
     *  Real cap is computed per-event in the constructor and scales
     *  with maxRadius / blocksPerTick × tickInterval so slow cinematic
     *  cascades (tickInterval up to 40, blocksPerTick down to 1) don't
     *  get cut off after 80 ticks. */
    private static final int MIN_LIFETIME_TICKS = 80;
    /** Cap on simultaneous events to prevent runaway CPU when many
     *  bones impact at once. New events past this are dropped silently. */
    private static final int MAX_ACTIVE_EVENTS = 12;

    private static final List<Event> ACTIVE = new ArrayList<>();

    /* === Block-type override state (event-scoped) ===
     *
     *  PhysicsMod's MixinLevelChunk.setBlockState injection queues a
     *  BlockUpdate to PhysicsMod.updateQueue and processes it
     *  ASYNCHRONOUSLY on a later tick. So push/pop bracketed around
     *  one setBlockState call doesn't help — by the time PhysicsMod
     *  reads ConfigBlocks settings to decide debris type, our pop has
     *  already restored the original values. Net result: ~5% of debris
     *  leaks through as the user's original PhysicsMod block-mode (the
     *  "still many fractured + some blocky" residual mismatch the user
     *  reported even after the customizedBlocks fix).
     *
     *  Solution: hold the override for the WHOLE event lifetime plus a
     *  trailing linger period (~1 sec = 20 ticks) so PhysicsMod's
     *  deferred queue gets processed while our override is still active.
     *
     *  Refcount-based so multiple concurrent events don't clobber each
     *  other's saved state. First non-default event acquires; last
     *  release schedules a delayed pop. */
    private static int overrideRefcount;
    private static Object[] activeOverrideSaved;
    private static String activeOverrideMode;
    private static int overridePopLingerTicks;

    private static synchronized void acquireBlockTypeOverride(String blockMode)
    {
        if (blockMode == null || "default".equals(blockMode)) return;

        /* Cancel any pending pop — a new event re-arms the existing
         *  override instead of letting it expire. */
        overridePopLingerTicks = 0;

        if (overrideRefcount == 0 && activeOverrideSaved == null)
        {
            activeOverrideSaved = pushBlockTypeOverride(blockMode);
            activeOverrideMode = blockMode;
        }
        /* If a different mode is requested while one is already active,
         *  the first one wins — keeping things simple. Logged so we
         *  notice if the user does this in practice. */
        else if (!blockMode.equals(activeOverrideMode))
        {
            LOG.debug("[propagator] override-mode mismatch: active={}, requested={} (keeping active)",
                activeOverrideMode, blockMode);
        }
        overrideRefcount++;
    }

    private static synchronized void releaseBlockTypeOverride(String blockMode)
    {
        if (blockMode == null || "default".equals(blockMode)) return;
        if (overrideRefcount <= 0) return;
        overrideRefcount--;
        if (overrideRefcount == 0)
        {
            /* 20 ticks ≈ 1 sec — buffer for PhysicsMod's deferred
             *  updateQueue (typically processed within a tick or two). */
            overridePopLingerTicks = 20;
        }
    }

    private static synchronized void tickOverrideLinger()
    {
        if (overridePopLingerTicks <= 0) return;
        overridePopLingerTicks--;
        if (overridePopLingerTicks == 0 && activeOverrideSaved != null && overrideRefcount == 0)
        {
            popBlockTypeOverride(activeOverrideSaved);
            activeOverrideSaved = null;
            activeOverrideMode = null;
        }
    }

    private static final class Event
    {
        final BlockPos origin;
        final Vector3f impactDir;
        final int maxRadius;
        final int blocksPerTick;
        final int debrisChunksPerBlock;
        final boolean destroyBlocks;
        /** Optional axis-aligned bounding region — when non-null, the
         *  BFS only visits blocks INSIDE [regionMin, regionMax] inclusive.
         *  This is what the physics_block_destruction clip uses to
         *  constrain destruction to a user-marked tree / wall / ground
         *  region without affecting surrounding world. */
        final BlockPos regionMin;
        final BlockPos regionMax;
        /** Outward force scale — debris velocity = (block - origin).normalized * scatterForce.
         *  Higher = more violent katapult effect. */
        final float scatterForce;
        /** Extra velocity added to every chunk on top of radial. */
        final float upwardForce;
        final float directionX, directionY, directionZ;
        /** Random per-chunk velocity jitter magnitude. */
        final float scatterJitter;
        /** Initial downward (or upward, if negative) velocity bias
         *  that affects "weight" feel of debris. */
        final float gravityBias;
        /** Interval in client ticks between wave-expansion steps.
         *  1 = every tick (default), N = every Nth tick. Combined
         *  with blocksPerTick this gives full control over wave speed
         *  from sub-second-burst to multi-second cascade. */
        final int tickInterval;

        /** Destruction mode constant (radial / directional / top_down
         *  / bottom_up / instant / random). When non-radial, we
         *  pre-compute the full ordering at construction and process
         *  it via {@link #orderedQueue} instead of the BFS frontier. */
        final String mode;
        /** PhysicsMod block-physics-type override. "default" = no
         *  override, otherwise BLOCKY/FRACTURED/FRACTURED_VOXEL/PARTICLES. */
        final String blockMode;
        /** Debris scale multiplier (mirrors the emitter's physicsScale). */
        final float physicsScale;
        /** Per-debris lifetime in ticks. */
        final int debrisLifetime;
        /** Disable inter-debris collision so chunks don't brake each other. */
        final boolean noInterCollision;
        /** Pre-computed processing order for non-radial modes.
         *  null means use BFS frontier (radial). */
        java.util.List<BlockPos> orderedQueue;
        int orderedIndex;

        /** Per-event lifetime cap, scaled by tickInterval / blocksPerTick
         *  so slow cinematic cascades don't get truncated. */
        final int lifetimeTicks;

        /** Set when this event has acquired the global block-type
         *  override. Ensures we only release once per event regardless
         *  of which it.remove() branch fires. */
        boolean overrideAcquired;

        final Deque<BlockPos> frontier = new ArrayDeque<>();
        final Set<Long> visited = new HashSet<>();

        int ticksAlive;

        Event(BlockPos origin, Vector3f impactDir, int maxRadius, int blocksPerTick,
              int debrisChunksPerBlock, boolean destroyBlocks,
              BlockPos regionMin, BlockPos regionMax, float scatterForce,
              float upwardForce, float directionX, float directionY, float directionZ,
              float scatterJitter, float gravityBias, int tickInterval, String mode,
              String blockMode, float physicsScale, int debrisLifetime, boolean noInterCollision)
        {
            this.physicsScale = physicsScale;
            this.debrisLifetime = debrisLifetime;
            this.noInterCollision = noInterCollision;
            this.origin = origin.immutable();
            this.impactDir = impactDir;
            this.maxRadius = maxRadius;
            this.blocksPerTick = Math.max(1, blocksPerTick);
            this.debrisChunksPerBlock = Math.max(1, debrisChunksPerBlock);
            this.destroyBlocks = destroyBlocks;
            this.regionMin = regionMin == null ? null : regionMin.immutable();
            this.regionMax = regionMax == null ? null : regionMax.immutable();
            this.scatterForce = scatterForce;
            this.upwardForce = upwardForce;
            this.directionX = directionX;
            this.directionY = directionY;
            this.directionZ = directionZ;
            this.scatterJitter = scatterJitter;
            this.gravityBias = gravityBias;
            this.tickInterval = Math.max(1, tickInterval);
            this.mode = mode == null ? "radial" : mode;
            this.blockMode = blockMode == null ? "default" : blockMode;

            /* Compute lifetime budget. Worst-case ticks needed:
             *  ceil(maxRadius / blocksPerTick) × tickInterval, plus a
             *  small slack for late-tick stragglers. Floor at
             *  MIN_LIFETIME_TICKS so tiny radii still get a sane budget.
             *  Hard ceiling at 30s × 20t = 600 ticks per safety. */
            int worst = (Math.max(1, maxRadius) / this.blocksPerTick + 4) * this.tickInterval;
            this.lifetimeTicks = Math.max(MIN_LIFETIME_TICKS, Math.min(600, worst));

            this.frontier.offer(this.origin);

            /* For non-radial modes, pre-compute the full processing
             *  list at construction. Subsequent ticks pop from this
             *  list instead of the BFS frontier. */
            if (!"radial".equals(this.mode) && this.regionMin != null && this.regionMax != null)
            {
                this.orderedQueue = buildOrderedQueue(this);
                this.orderedIndex = 0;
            }
        }

        boolean inRegion(BlockPos p)
        {
            if (regionMin == null || regionMax == null) return true;
            return p.getX() >= regionMin.getX() && p.getX() <= regionMax.getX()
                && p.getY() >= regionMin.getY() && p.getY() <= regionMax.getY()
                && p.getZ() >= regionMin.getZ() && p.getZ() <= regionMax.getZ();
        }
    }

    private BlockShatterPropagator()
    {
    }

    /**
     * Trigger a new propagation event at {@code origin}. Caller must
     * supply a normalized impact direction (the bone's velocity at
     * impact, ideally) so the debris flies away from the source
     * impact rather than purely radially.
     */
    /**
     * Trigger an unbounded propagation (used by fracture-body wall hits).
     * Wave can reach any non-air block within maxRadius from origin.
     */
    public static synchronized void trigger(BlockPos origin, Vector3f impactDir,
                                            int maxRadius, int blocksPerTick,
                                            int debrisChunksPerBlock, boolean destroyBlocks)
    {
        triggerInRegion(origin, impactDir, maxRadius, blocksPerTick,
            debrisChunksPerBlock, destroyBlocks, null, null, 0F,
            0F, 0F, 0F, 0F, 0.3F, 0F, 1, "radial", "default", 1F, 200, false);
    }

    /**
     * Trigger a region-bounded propagation. Wave only visits blocks
     * inside [regionMin, regionMax] inclusive. Used by the
     * physics_block_destruction clip — user marks a tree/wall/ground
     * region, sets an impact point, only THAT region fragments.
     *
     * @param scatterForce  outward velocity magnitude per debris chunk
     *                      (in addition to the normal impactDir scatter).
     *                      Use 0 for default behavior, > 0 for explosive
     *                      "blocks katapult outward" effect.
     */
    public static synchronized void triggerInRegion(BlockPos origin, Vector3f impactDir,
                                                    int maxRadius, int blocksPerTick,
                                                    int debrisChunksPerBlock, boolean destroyBlocks,
                                                    BlockPos regionMin, BlockPos regionMax,
                                                    float scatterForce,
                                                    float upwardForce,
                                                    float directionX, float directionY, float directionZ,
                                                    float scatterJitter, float gravityBias,
                                                    int tickInterval, String mode, String blockMode,
                                                    float physicsScale, int debrisLifetime,
                                                    boolean noInterCollision)
    {
        if (origin == null) return;
        if (ACTIVE.size() >= MAX_ACTIVE_EVENTS)
        {
            /* Soft cap reached — drop new event silently. */
            return;
        }
        Event event = new Event(origin, impactDir, maxRadius, blocksPerTick,
            debrisChunksPerBlock, destroyBlocks, regionMin, regionMax, scatterForce,
            upwardForce, directionX, directionY, directionZ, scatterJitter, gravityBias, tickInterval, mode, blockMode,
            physicsScale, debrisLifetime, noInterCollision);
        ACTIVE.add(event);

        /* Acquire the global block-type override for the event's
         *  lifetime so PhysicsMod's deferred BlockUpdate queue (which
         *  processes asynchronously, after our setBlockState call has
         *  already returned) reads the overridden settings too. */
        if (event.blockMode != null && !"default".equals(event.blockMode))
        {
            acquireBlockTypeOverride(event.blockMode);
            event.overrideAcquired = true;
        }

        LOG.debug("[propagator] event at {} (r={}, speed={}, every {}t, mode={}, blockMode={}, scale={}, lifetime={}, destroy={})",
            origin, maxRadius, blocksPerTick, tickInterval, mode, blockMode, physicsScale, debrisLifetime, destroyBlocks);
    }

    /** Centralised event removal so we always release the override
     *  refcount, regardless of which exit branch the BFS / ordered
     *  queue / interval-gate path hits. */
    private static void removeEvent(Iterator<Event> it, Event e)
    {
        if (e.overrideAcquired)
        {
            releaseBlockTypeOverride(e.blockMode);
            e.overrideAcquired = false;
        }
        it.remove();
    }

    public static synchronized void onClientTick()
    {
        /* Tick the global override-pop linger every tick, even when no
         *  events are active — so the trailing cleanup still happens
         *  after the last event ends. */
        tickOverrideLinger();

        if (ACTIVE.isEmpty()) return;

        Level world = Minecraft.getInstance().level;
        if (world == null)
        {
            /* Force-release every event's override before clearing so
             *  the refcount + linger state stays consistent. */
            for (Event e : ACTIVE)
            {
                if (e.overrideAcquired)
                {
                    releaseBlockTypeOverride(e.blockMode);
                    e.overrideAcquired = false;
                }
            }
            ACTIVE.clear();
            return;
        }

        Iterator<Event> it = ACTIVE.iterator();
        while (it.hasNext())
        {
            Event e = it.next();
            e.ticksAlive++;

            /* tickInterval gating — only process on every Nth tick.
             *  N=1 = every tick (default), N=5 = every 5th tick (very
             *  slow cascade for cinematic rip-apart sequences). */
            if (e.tickInterval > 1 && (e.ticksAlive % e.tickInterval) != 0)
            {
                if (e.ticksAlive > e.lifetimeTicks) removeEvent(it, e);
                continue;
            }

            /* Non-radial modes use the pre-computed ordered queue. */
            if (e.orderedQueue != null)
            {
                int budget = "instant".equals(e.mode) ? Integer.MAX_VALUE : e.blocksPerTick;
                int processedOrdered = 0;
                while (processedOrdered < budget && e.orderedIndex < e.orderedQueue.size())
                {
                    BlockPos pos = e.orderedQueue.get(e.orderedIndex++);
                    BlockState state;
                    try { state = world.getBlockState(pos); }
                    catch (Throwable t) { state = null; }
                    if (state != null && !state.isAir())
                    {
                        /* IMPORTANT: destroy the world block FIRST, then
                         *  spawn the chunk into the now-empty space.
                         *  Reversing this order causes the chunk to spawn
                         *  inside the still-solid original block — PhysX
                         *  resolves the overlap by ejecting the chunk in
                         *  an arbitrary direction (often opposite to our
                         *  intended velocity = "blocks go backward" with
                         *  full-block physicsScale that the user reported). */
                        if (e.destroyBlocks)
                        {
                            BlockSnapshotRegistry.snapshotAndRemove(pos);
                            recordBlockDestroyed();
                            if (!"default".equals(e.blockMode))
                            {
                                FracturePhysicsBridge.cancelPendingPhysicsModUpdate(world, pos);
                            }
                        }
                        spawnDebrisForBlock(pos, state, e);
                        processedOrdered++;
                    }
                }
                if (e.orderedIndex >= e.orderedQueue.size() || e.ticksAlive > e.lifetimeTicks) removeEvent(it, e);
                continue;
            }

            int processed = 0;
            while (processed < e.blocksPerTick && !e.frontier.isEmpty())
            {
                BlockPos pos = e.frontier.poll();
                long key = pos.asLong();

                if (e.visited.contains(key)) continue;
                e.visited.add(key);

                if (manhattanDist(pos, e.origin) > e.maxRadius) continue;

                /* Region constraint: the BFS keeps expanding through
                 *  out-of-region blocks (otherwise a wave seeded
                 *  OUTSIDE the region dies immediately and nothing
                 *  destructs). We only PROCESS — i.e. spawn debris
                 *  and remove blocks — when the current block is
                 *  inside the user-defined region. */
                boolean inside = e.inRegion(pos);

                if (inside)
                {
                    BlockState state;
                    try { state = world.getBlockState(pos); }
                    catch (Throwable t) { state = null; }

                    if (state != null && !state.isAir())
                    {
                        /* Block-mode dispatch:
                         *  - default → small sparkly particles via our
                         *    legacy ParticleSpawner path (with full
                         *    direction/force/scatter control)
                         *  - blocky / fractured / fractured_voxel /
                         *    particles → BBS's PhysicsBlockFractureEmitter
                         *    spawn helpers, which use the proper
                         *    PhysicsWorld.addBlockParticle path with
                         *    full position+velocity control AND
                         *    per-call mode (so direction/force STILL
                         *    work for non-particle modes!) */

                        /* Destroy first, spawn second — see ordered-queue
                         *  branch comment above for the PhysX-overlap
                         *  rationale (chunks spawned inside still-solid
                         *  blocks get ejected backward = user's reported
                         *  "blocks fly backward instead of forward" with
                         *  large physicsScale). */
                        if (e.destroyBlocks)
                        {
                            BlockSnapshotRegistry.snapshotAndRemove(pos);
                            recordBlockDestroyed();
                            if (!"default".equals(e.blockMode))
                            {
                                FracturePhysicsBridge.cancelPendingPhysicsModUpdate(world, pos);
                            }
                        }
                        spawnDebrisForBlock(pos, state, e);

                        processed++;
                    }
                }

                /* Queue 6 face neighbours regardless of in-region
                 *  status — the wave needs to propagate through air
                 *  + out-of-region cells to reach the region from
                 *  any seed point. The maxRadius check above bounds
                 *  total expansion. */
                for (Direction d : Direction.values())
                {
                    BlockPos n = pos.relative(d);
                    if (!e.visited.contains(n.asLong())) e.frontier.offer(n);
                }
            }

            if (e.frontier.isEmpty() || e.ticksAlive > e.lifetimeTicks)
            {
                removeEvent(it, e);
            }
        }
    }

    private static void spawnDebrisForBlock(BlockPos pos, BlockState state, Event e)
    {
        java.util.Random rng = new java.util.Random();

        /* Snapshot body count once before this block's chunk batch so
         *  noInterCollision applies regardless of which spawn path each
         *  chunk takes (BBS emitter helpers OR legacy ParticleSpawner
         *  fallback for "default" blockMode). The BBS-emitter path also
         *  applies the mask itself, but redoing it on already-masked
         *  bodies is a harmless no-op. */
        Level world = Minecraft.getInstance().level;
        int bodiesBefore = (e.noInterCollision && world != null)
            ? FracturePhysicsBridge.snapshotBodyCount(world) : -1;

        /* For block-sized chunk modes (blocky / fractured / fractured_voxel)
         *  each spawn call creates ONE full block-sized physics body.
         *  Spawning N per block stacks N copies = the duplication user
         *  reported in the logs (235 blocks × chunksPerBlock=4 = 940
         *  spawn calls = ratio=4.00). Force chunkCount=1 for these.
         *  PARTICLES + default modes spawn smaller-than-block visuals
         *  so the slider still controls density there. */
        int chunkCount;
        switch (e.blockMode == null ? "default" : e.blockMode)
        {
            case "blocky":
            case "fractured":
            case "fractured_voxel":
                chunkCount = 1;
                break;
            default:
                chunkCount = e.debrisChunksPerBlock;
        }

        for (int i = 0; i < chunkCount; i++)
        {
            double sx = pos.getX() + 0.5D + (rng.nextFloat() - 0.5F) * 0.4F;
            double sy = pos.getY() + 0.5D + (rng.nextFloat() - 0.5F) * 0.4F;
            double sz = pos.getZ() + 0.5D + (rng.nextFloat() - 0.5F) * 0.4F;

            /* Velocity = impact direction (forward push) + radial
             *  component from origin (so the wave propagates with
             *  visible direction). */
            float radialX = pos.getX() + 0.5F - e.origin.getX();
            float radialY = pos.getY() + 0.5F - e.origin.getY();
            float radialZ = pos.getZ() + 0.5F - e.origin.getZ();
            float radialLen = (float) Math.sqrt(radialX * radialX + radialY * radialY + radialZ * radialZ);
            if (radialLen > 1e-4F)
            {
                radialX /= radialLen;
                radialY /= radialLen;
                radialZ /= radialLen;
            }

            /* Velocity composition (in priority of cinematic control):
             *   1. Radial scatter — outward from impact origin, scaled
             *      by (0.4 + scatterForce). Pure radial blast.
             *   2. Direction bias — fixed (dirX, dirY, dirZ) vector
             *      added to every chunk so user can push debris a
             *      specific way regardless of geometry.
             *   3. Upward force — extra Y kick on top.
             *   4. Impact-dir push — small contribution from where
             *      the BFS started relative to region center.
             *   5. Random jitter — chaos magnitude for organic feel.
             *   6. Gravity bias — initial -Y bias for "heavier" debris. */
            float jit = e.scatterJitter;
            double vx = radialX * (0.4F + e.scatterForce)
                      + e.directionX
                      + e.impactDir.x * 0.3F
                      + (rng.nextFloat() - 0.5F) * jit;
            double vy = radialY * (0.3F + e.scatterForce)
                      + e.upwardForce
                      + e.directionY
                      + e.impactDir.y * 0.3F
                      + rng.nextFloat() * jit
                      - e.gravityBias;
            double vz = radialZ * (0.4F + e.scatterForce)
                      + e.directionZ
                      + e.impactDir.z * 0.3F
                      + (rng.nextFloat() - 0.5F) * jit;

            /* Mode dispatch:
             *  - default → legacy ParticleSpawner path (small sparkly
             *    particles, hardcoded to PARTICLE in PhysicsMod's
             *    bytecode but works fine for "default" UX)
             *  - else → BBS's PhysicsBlockFractureEmitter helpers via
             *    reflection. These use the PROPER PhysicsMod spawn
             *    pipeline (PhysicsWorld.addBlockParticle +
             *    PxRigidDynamic.setLinearVelocity) so direction/force
             *    + mode BOTH work. Falls back to legacy if BBS's
             *    helpers can't be resolved. */
            if (!"default".equals(e.blockMode))
            {
                boolean ok = FracturePhysicsBridge.spawnViaBbsEmitter(
                    sx, sy, sz, vx, vy, vz, state, e.blockMode,
                    e.physicsScale, e.debrisLifetime, e.noInterCollision);
                if (ok) { recordSpawnCall(); continue; }
            }
            FracturePhysicsBridge.spawnDebrisChunkWithState(sx, sy, sz, vx, vy, vz, state, e.blockMode);
            recordSpawnCall();
        }

        /* Post-batch: if user wanted noInterCollision and we took the
         *  legacy path (or BBS emitter path failed mid-batch), apply
         *  the mask to all bodies added since bodiesBefore. Idempotent
         *  for already-masked bodies. */
        if (bodiesBefore >= 0 && world != null)
        {
            FracturePhysicsBridge.applyNoCollisionMask(world, bodiesBefore);
        }
    }

    /* Cached reflection handles for the block-type override path.
     *  Resolved once per JVM run; subsequent push/pop calls reuse them.
     *  Volatile so other threads see the populated values. */
    private static volatile java.lang.reflect.Field cachedTypeField;
    private static volatile Object cachedParentSetting;
    private static volatile java.util.Map<?, ?> cachedCustomizedBlocks;
    private static volatile boolean reflectionInitTried;

    private static void initBlockTypeReflection()
    {
        if (reflectionInitTried) return;
        reflectionInitTried = true;
        try
        {
            Class<?> configBlocksCls = Class.forName("net.diebuddies.config.ConfigBlocks");

            java.lang.reflect.Field parentField = configBlocksCls.getField("PARENT_BLOCK_SETTING");
            Object parentSetting = parentField.get(null);
            if (parentSetting == null) return;

            java.lang.reflect.Field customizedField = configBlocksCls.getField("customizedBlocks");
            Object customized = customizedField.get(null);

            cachedTypeField = parentSetting.getClass().getField("type");
            cachedParentSetting = parentSetting;
            if (customized instanceof java.util.Map<?, ?> m) cachedCustomizedBlocks = m;
        }
        catch (Throwable t)
        {
            LOG.debug("initBlockTypeReflection failed: {}", t.toString());
        }
    }

    /**
     * Reflectively swap PhysicsMod's block-type for the duration of one
     * setBlockState call. Overrides BOTH:
     *  - {@code PARENT_BLOCK_SETTING.type} (fallback for unregistered blocks)
     *  - every entry in {@code customizedBlocks} (per-block-ID overrides
     *    that beat the parent for blocks like oak_planks which default
     *    to BLOCKY in PhysicsMod's settings — without this second leg,
     *    those blocks leak through as the wrong mode = the user-reported
     *    "95% fractured / 5% blocky" residual mismatch).
     *
     * <p>Returns a saved-state Object[] that {@link #popBlockTypeOverride}
     * uses to restore. {@code null} = no override active or reflection
     * failed (caller treats as a no-op).</p>
     */
    private static Object[] pushBlockTypeOverride(String blockMode)
    {
        if (blockMode == null || "default".equals(blockMode)) return null;
        initBlockTypeReflection();
        if (cachedTypeField == null || cachedParentSetting == null) return null;

        try
        {
            Class<?> typeCls = Class.forName("net.diebuddies.physics.settings.blocks.BlockPhysicsType");
            @SuppressWarnings({ "unchecked", "rawtypes" })
            Object newType = Enum.valueOf((Class) typeCls, blockMode.toUpperCase());

            Object savedParentType = cachedTypeField.get(cachedParentSetting);
            cachedTypeField.set(cachedParentSetting, newType);

            /* Override every per-block override too. */
            java.util.Map<Object, Object> savedPerBlock = null;
            java.util.Map<?, ?> custom = cachedCustomizedBlocks;
            if (custom != null && !custom.isEmpty())
            {
                savedPerBlock = new java.util.IdentityHashMap<>(custom.size());
                for (java.util.Map.Entry<?, ?> entry : custom.entrySet())
                {
                    Object setting = entry.getValue();
                    if (setting == null) continue;
                    try
                    {
                        Object oldType = cachedTypeField.get(setting);
                        savedPerBlock.put(setting, oldType);
                        cachedTypeField.set(setting, newType);
                    }
                    catch (Throwable ignored) {}
                }
            }

            return new Object[]{ savedParentType, savedPerBlock };
        }
        catch (Throwable t)
        {
            LOG.debug("pushBlockTypeOverride('{}') failed: {}", blockMode, t.toString());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static void popBlockTypeOverride(Object[] saved)
    {
        if (saved == null || cachedTypeField == null) return;
        try
        {
            cachedTypeField.set(cachedParentSetting, saved[0]);
        }
        catch (Throwable ignored) {}

        java.util.Map<Object, Object> savedPerBlock = (java.util.Map<Object, Object>) saved[1];
        if (savedPerBlock == null) return;
        for (java.util.Map.Entry<Object, Object> entry : savedPerBlock.entrySet())
        {
            try { cachedTypeField.set(entry.getKey(), entry.getValue()); }
            catch (Throwable ignored) {}
        }
    }

    private static int manhattanDist(BlockPos a, BlockPos b)
    {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY()) + Math.abs(a.getZ() - b.getZ());
    }

    /** Pre-compute the processing order for non-radial modes by
     *  enumerating every block in the region and sorting/filtering
     *  per the mode's logic. Called once at Event construction. */
    private static java.util.List<BlockPos> buildOrderedQueue(Event e)
    {
        java.util.List<BlockPos> all = new java.util.ArrayList<>();
        for (int x = e.regionMin.getX(); x <= e.regionMax.getX(); x++)
        {
            for (int y = e.regionMin.getY(); y <= e.regionMax.getY(); y++)
            {
                for (int z = e.regionMin.getZ(); z <= e.regionMax.getZ(); z++)
                {
                    all.add(new BlockPos(x, y, z));
                }
            }
        }

        switch (e.mode)
        {
            case "directional":
                /* Keep only blocks where (block - impact) · direction > 0
                 *  (block is "ahead" of impact along the direction
                 *  vector). Sort by distance from impact so closest go
                 *  first. */
                {
                    float dx = e.directionX, dy = e.directionY, dz = e.directionZ;
                    float dlen = (float) Math.sqrt(dx*dx + dy*dy + dz*dz);
                    if (dlen < 1e-4F)
                    {
                        /* No direction set — fall back to radial-by-distance. */
                        all.sort((a, b) -> Integer.compare(manhattanDist(a, e.origin), manhattanDist(b, e.origin)));
                        break;
                    }
                    float ndx = dx/dlen, ndy = dy/dlen, ndz = dz/dlen;
                    java.util.List<BlockPos> filtered = new java.util.ArrayList<>();
                    for (BlockPos p : all)
                    {
                        float vx = p.getX() + 0.5F - e.origin.getX();
                        float vy = p.getY() + 0.5F - e.origin.getY();
                        float vz = p.getZ() + 0.5F - e.origin.getZ();
                        if (vx*ndx + vy*ndy + vz*ndz > 0F) filtered.add(p);
                    }
                    filtered.sort((a, b) -> Integer.compare(manhattanDist(a, e.origin), manhattanDist(b, e.origin)));
                    return filtered;
                }
            case "top_down":
                all.sort((a, b) -> Integer.compare(b.getY(), a.getY()));
                break;
            case "bottom_up":
                all.sort((a, b) -> Integer.compare(a.getY(), b.getY()));
                break;
            case "instant":
                /* Order doesn't matter; every block processed in the
                 *  first tick anyway. Keep natural enumeration. */
                break;
            case "random":
                java.util.Collections.shuffle(all, new java.util.Random());
                break;
            default:
                /* Unknown mode — natural order. */
                break;
        }
        return all;
    }

    public static synchronized void clearAll()
    {
        /* Release every event's override hold before clearing so the
         *  refcount returns to zero and the trailing pop fires on the
         *  next tick. Without this, a clearAll() during an active event
         *  would leave the override permanently applied. */
        for (Event e : ACTIVE)
        {
            if (e.overrideAcquired)
            {
                releaseBlockTypeOverride(e.blockMode);
                e.overrideAcquired = false;
            }
        }
        ACTIVE.clear();
    }

    public static synchronized int activeEventCount()
    {
        return ACTIVE.size();
    }

    /* === Per-fire spawn-call counter ===
     *
     * Counts explicit spawnViaBbsEmitter / spawnDebrisChunkWithState
     * invocations + blocks-destroyed during the lifetime of each event.
     * Far more reliable than physics-body-count delta which is polluted
     * by concurrent despawns of older debris (the body-count diff
     * showed -1 even though we definitely spawned 42 new bodies, because
     * 43 older debris from prior fires hit their lifetime cap during
     * the 2-sec measurement window).
     *
     * Each fire snapshots the counters at start, schedules a delayed
     * report that prints (spawnCalls - startSnapshot) and
     * (blocksDestroyed - startSnapshot). If those are equal, our spawn
     * path is 1:1 and any visual "duplication" the user perceives is
     * stacked debris from PRIOR fires (still alive within their
     * debrisLifetime window), not new doubling. */
    private static long totalSpawnCalls;
    private static long totalBlocksDestroyed;

    static void recordSpawnCall() { totalSpawnCalls++; }
    static void recordBlockDestroyed() { totalBlocksDestroyed++; }

    private static final java.util.List<long[]> PENDING_REPORTS = new java.util.ArrayList<>();
    private static int reportTickCounter;

    public static synchronized void scheduleBodyCountReport(int bodiesBefore, int delayTicks)
    {
        /* Snapshot the counters at fire-time so the post-fire delta
         *  measures only THIS fire's spawns / destroys. */
        PENDING_REPORTS.add(new long[]{
            bodiesBefore,
            reportTickCounter + delayTicks,
            totalSpawnCalls,
            totalBlocksDestroyed });
    }

    public static synchronized void tickBodyCountReports()
    {
        if (PENDING_REPORTS.isEmpty()) return;
        reportTickCounter++;
        java.util.Iterator<long[]> it = PENDING_REPORTS.iterator();
        while (it.hasNext())
        {
            long[] rec = it.next();
            if (reportTickCounter < rec[1]) continue;

            int before = (int) rec[0];
            long startSpawnCalls = rec[2];
            long startBlocksDestroyed = rec[3];
            it.remove();

            net.minecraft.world.level.Level w;
            try
            {
                w = net.minecraft.client.Minecraft.getInstance().level;
                if (w == null) continue;
            }
            catch (Throwable t) { continue; }

            int after = FracturePhysicsBridge.snapshotBodyCount(w);
            int bodyDelta = (before >= 0 && after >= 0) ? (after - before) : -1;

            long spawnCalls = totalSpawnCalls - startSpawnCalls;
            long blocksDestroyed = totalBlocksDestroyed - startBlocksDestroyed;
            double spawnRatio = blocksDestroyed > 0 ? (double) spawnCalls / blocksDestroyed : 0.0;

            String msg = String.format(
                "blocksDestroyed=%d  spawnCalls=%d  spawnRatio=%.2f  (bodyCountDelta=%d, polluted by concurrent despawn)",
                blocksDestroyed, spawnCalls, spawnRatio, bodyDelta);
            LOG.info("[block-destruction] POST-FIRE: {}", msg);

            if (com.afrobama.bbstools.perf.BBSPerfConfig.VERBOSE_DESTRUCTION_CHAT)
            {
                try
                {
                    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                    if (mc != null && mc.player != null)
                    {
                        boolean dup = blocksDestroyed > 0 && spawnCalls > blocksDestroyed;
                        String color = dup ? "§c" : "§a";
                        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "§6[BBSTools-debug] §fdestroyed=§e" + blocksDestroyed
                            + " §fspawnCalls=" + color + spawnCalls
                            + " §fratio=" + color + String.format("%.2f", spawnRatio)
                            + (dup ? " §c(SPAWN-PATH DOUBLES!)" : " §a(spawn-path 1:1)")));
                    }
                }
                catch (Throwable ignored) {}
            }
        }
    }

}
