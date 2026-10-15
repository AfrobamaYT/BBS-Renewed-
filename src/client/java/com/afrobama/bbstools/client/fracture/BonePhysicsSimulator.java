package com.afrobama.bbstools.client.fracture;

import mchorse.bbs_mod.bobj.BOBJArmature;
import mchorse.bbs_mod.forms.entities.IEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Singleton verlet/euler integrator for fracture-spawned bone bodies.
 * Each registered {@link BonePhysicsBody} is integrated one tick per
 * client tick under gravity + damping + simple block collision, and
 * its current transform is exposed via {@link #applyOverrides} which
 * the BOBJArmature mixin calls to override the bone matrices.
 *
 * <h2>Integration scheme</h2>
 * <p>Semi-implicit Euler — simple, stable for cinematic frame rates.
 * Per tick:</p>
 * <pre>
 *   linearVelocity += gravity
 *   linearVelocity *= linearDamping  (0.99 by default)
 *   position += linearVelocity
 *   collision-resolve(position, halfExtents)  // clamp to ground
 *   angularVelocity *= angularDamping (0.97)
 *   rotation = Quaternionf(angularVelocity * dt) * rotation
 * </pre>
 *
 * <h2>Collision</h2>
 * <p>Per-body AABB intersection test against blocks within the body's
 * world-space bounding box. On hit we resolve the dominant penetration
 * axis (Y for ground hits, X/Z for wall hits) — clamping position
 * back to surface and reversing/damping velocity along that axis. Not
 * Newtonian-correct but visually plausible for cinematic shots.</p>
 *
 * <h2>Settled detection</h2>
 * <p>When linearVelocity² + angularVelocity² stays below a threshold
 * for {@link #SETTLE_TICKS} ticks, the body is marked settled — we
 * stop integrating it (final pose persists) until despawn time. Saves
 * CPU + makes the cinematic rest-pose stable.</p>
 */
public final class BonePhysicsSimulator
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/fracture/sim");

    /* Defaults are now per-body (see BonePhysicsBody), but wall-bounce
     *  and settle thresholds stay global because they're stable
     *  across all fracture types. */
    private static final float WALL_BOUNCE  = -0.40F;
    private static final float SETTLE_VEL_SQ = 0.001F;
    private static final int   SETTLE_TICKS = 12;

    /** Active bodies, keyed by actor reference. WeakHashMap so we
     *  don't leak when actors get GC'd. */
    private static final Map<IEntity, List<BonePhysicsBody>> BY_ACTOR = new java.util.WeakHashMap<>();

    private BonePhysicsSimulator()
    {
    }

    /**
     * Register a freshly-spawned body for simulation. Caller is
     * expected to have populated the body's initial state already
     * (position, velocity, initialBoneMatrix, etc.).
     */
    public static synchronized void add(BonePhysicsBody body)
    {
        if (body == null || body.actor == null) return;
        BY_ACTOR.computeIfAbsent(body.actor, (k) -> new ArrayList<>()).add(body);
    }

    /**
     * Per-client-tick integration. Called from
     * {@code BBSToolsModClient}'s tick hook.
     */
    public static synchronized void onClientTick()
    {
        if (BY_ACTOR.isEmpty()) return;

        Level world = Minecraft.getInstance().level;

        for (List<BonePhysicsBody> bodies : BY_ACTOR.values())
        {
            Iterator<BonePhysicsBody> it = bodies.iterator();
            while (it.hasNext())
            {
                BonePhysicsBody b = it.next();

                if (b.dead)
                {
                    it.remove();
                    continue;
                }

                /* Increment age every tick (even for settled bodies)
                 *  so the lifetime fade ticks down regardless of
                 *  motion state. */
                b.ticksAlive++;

                /* Lifetime fade-out: ramp visualScale from baseScale
                 *  → 0 over the last FADE_OUT_TICKS ticks before
                 *  lifetime expires. After expiry the body is dead
                 *  (FractureRegistry handles that), but during the
                 *  tail the scale shrink gives a smooth despawn pop
                 *  instead of an abrupt disappearance. */
                if (b.lifetimeTicks > 0)
                {
                    int ticksUntilExpire = b.lifetimeTicks - b.ticksAlive;
                    if (ticksUntilExpire <= BonePhysicsBody.FADE_OUT_TICKS && ticksUntilExpire > 0)
                    {
                        float fade = (float) ticksUntilExpire / (float) BonePhysicsBody.FADE_OUT_TICKS;
                        b.visualScale = b.baseScale * fade;
                    }
                    else if (ticksUntilExpire <= 0)
                    {
                        b.visualScale = 0F;
                        b.dead = true;
                    }
                    else
                    {
                        b.visualScale = b.baseScale;
                    }
                }

                if (b.settled) continue;

                /* Held by gravity-gun → skip integration entirely.
                 *  GravityGunHandler sets position directly each client
                 *  tick; we don't want gravity, damping, collision, or
                 *  ragdoll constraints to fight the hold. */
                if (b.gravityGunHeld) continue;

                /* Slow-motion: accumulate slowMotion (which can be
                 *  > 1 for fast-forward or < 1 for slow-mo). Run
                 *  tickBody once per integer crossing of the
                 *  accumulator. */
                b.tickAccumulator += b.slowMotion;
                int subSteps = 0;
                while (b.tickAccumulator >= 1F && subSteps < 5)
                {
                    b.tickAccumulator -= 1F;
                    tickBody(b, world);
                    subSteps++;
                }
            }

            /* Joint-constraint pass — ragdoll mode. Runs ONLY for
             *  bodies whose jointStiffness > 0; the constraint pulls
             *  each body toward its parent so connected bones tumble
             *  together like a falling ragdoll. Multiple iterations
             *  produce stiffer joints (similar to position-based
             *  dynamics — more iters = more rigid). 4 iterations is
             *  enough for visually plausible chained joints without
             *  noticeable jitter. */
            applyJointConstraints(bodies, 4);

            /* Inter-body AABB collision: push overlapping bodies
             *  apart so debris piles up on the ground instead of
             *  passing through each other. O(N²) per actor — fine
             *  for typical bone counts (~20). */
            resolveInterBody(bodies);
        }
    }

    /**
     * Verlet-style position-based distance constraints between each
     * body and its parent body. After physics integration, for each
     * body with a valid parent and stiffness > 0:
     *
     * <pre>
     *   d = current distance from body to parent
     *   error = (d - bindDistance) / d
     *   half = error * 0.5 * stiffness
     *   move body toward parent by `half`
     *   move parent toward body by `half`
     * </pre>
     *
     * <p>Iterating multiple times converges toward the rest length —
     * 4 iterations gives a soft-rigid feel at our scale. We don't
     * compensate angular momentum (cheap solver); the slight energy
     * drift is fine for cinematic shots.</p>
     */
    /** Reusable map for the constraint solver — avoids per-tick
     *  allocation (one HashMap × N bodies × 60fps). */
    private static final java.util.HashMap<Integer, BonePhysicsBody> SOLVER_BY_BONE =
        new java.util.HashMap<>(64);

    private static void applyJointConstraints(List<BonePhysicsBody> bodies, int iterations)
    {
        if (bodies.size() < 2) return;

        /* Quick lookup by boneIndex so we don't pay O(N²) per
         *  constraint resolve. Reuses static map cleared each call —
         *  the simulator is single-threaded so sharing is safe. */
        SOLVER_BY_BONE.clear();
        boolean anyConstrained = false;
        for (BonePhysicsBody b : bodies)
        {
            if (b.dead) continue;
            SOLVER_BY_BONE.put(b.boneIndex, b);
            if (b.jointStiffness > 0F && b.parentBoneIndex >= 0) anyConstrained = true;
        }
        if (!anyConstrained)
        {
            SOLVER_BY_BONE.clear();
            return;
        }
        java.util.HashMap<Integer, BonePhysicsBody> byBone = SOLVER_BY_BONE;

        try
        {
        for (int iter = 0; iter < iterations; iter++)
        {
            for (BonePhysicsBody body : bodies)
            {
                if (body.dead) continue;
                if (body.jointStiffness <= 0F) continue;
                if (body.parentBoneIndex < 0) continue;
                if (body.bindDistanceToParent <= 0F) continue;

                BonePhysicsBody parent = byBone.get(body.parentBoneIndex);
                if (parent == null || parent.dead) continue;

                float dx = body.position.x - parent.position.x;
                float dy = body.position.y - parent.position.y;
                float dz = body.position.z - parent.position.z;
                float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (d < 1e-5F) continue;

                float diff = (d - body.bindDistanceToParent) / d;

                /* Un-settle when the constraint is being significantly
                 *  violated. Without this, a settled bone whose parent
                 *  moves would stay frozen while the parent flies
                 *  away — the rest of the chain ends up tugging
                 *  awkwardly against an anchor. With un-settle, the
                 *  whole chain wakes up and follows organically. */
                if (Math.abs(diff) > 0.05F)
                {
                    if (body.settled)   { body.settled = false; body.settledCounter = 0; }
                    if (parent.settled) { parent.settled = false; parent.settledCounter = 0; }
                }

                /* Stiffness ∈ (0, 1]. Halve the correction so we
                 *  apply equal-and-opposite to body and parent. */
                float k = body.jointStiffness * 0.5F;
                float ox = dx * diff * k;
                float oy = dy * diff * k;
                float oz = dz * diff * k;

                /* Pull body toward parent by ox, parent toward body
                 *  by ox (sign reversed). If the parent is settled
                 *  we still tug — settled is a render-stability flag
                 *  that we override under joint forces, otherwise the
                 *  ragdoll would yank against frozen ancestors. */
                if (!body.settled)
                {
                    body.position.x -= ox;
                    body.position.y -= oy;
                    body.position.z -= oz;
                }
                else
                {
                    parent.position.x += ox * 2F;
                    parent.position.y += oy * 2F;
                    parent.position.z += oz * 2F;
                    continue;
                }

                if (!parent.settled)
                {
                    parent.position.x += ox;
                    parent.position.y += oy;
                    parent.position.z += oz;
                }
                else
                {
                    /* Parent is settled (e.g. resting on ground) so
                     *  it should anchor the chain — apply the full
                     *  correction to the body instead. */
                    body.position.x -= ox;
                    body.position.y -= oy;
                    body.position.z -= oz;
                }
            }
        }
        }
        finally
        {
            /* Release references so old bodies aren't kept alive by
             *  the static map after their actor has unloaded. */
            SOLVER_BY_BONE.clear();
        }
    }

    /**
     * Spawn a tiny smoke particle behind a fast-moving body so it
     * trails a wake. Threshold prevents settling/slow bodies from
     * emitting (which would look like incessant smoke clouds).
     */
    private static void emitTrailIfFast(BonePhysicsBody b, Level world)
    {
        if (!b.enableSmokeTrail) return;

        try
        {
            float speedSq = b.linearVelocity.lengthSquared();
            /* Speed threshold of 0.25 blocks/tick² → ~0.5 blocks/tick.
             *  Below that the body looks slow enough to be tumbling
             *  on the ground, no trail. */
            if (speedSq < 0.25F) return;

            double sx = b.prevPosition.x;
            double sy = b.prevPosition.y;
            double sz = b.prevPosition.z;

            /* PhysicsMod's SmokeDomain produces volumetric realistic
             *  smoke that disperses, drifts with simulated wind, and
             *  shades correctly with light. Density scales with the
             *  body's speed so faster bodies leave thicker trails. */
            float density = Math.min(1.4F, 0.5F + (float) Math.sqrt(speedSq) * 0.4F);
            boolean spawned = FracturePhysicsBridge.spawnRealisticSmoke(sx, sy, sz, density);

            if (!spawned)
            {
                /* Fallback: vanilla SMOKE particle so the trail is at
                 *  least visible without PhysicsMod loaded. */
                world.addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE,
                    sx, sy, sz,
                    (Math.random() - 0.5D) * 0.02D,
                    Math.random() * 0.03D,
                    (Math.random() - 0.5D) * 0.02D);
            }
        }
        catch (Throwable ignored) {}
    }

    /**
     * Play a one-shot impact sound at the contact location using the
     * impacted block's vanilla {@code BlockSoundGroup} — gets the
     * right "thud" character for the surface (stone vs wood vs
     * grass). Volume + pitch scale with impact speed so harder
     * impacts sound heavier.
     */
    private static void playImpactSound(Level world, float x, float y, float z,
                                        BlockState impactedBlock, float impactSpeed)
    {
        try
        {
            net.minecraft.world.level.block.SoundType group = impactedBlock.getSoundType();
            if (group == null) return;

            net.minecraft.sounds.SoundEvent stepSound = group.getStepSound();
            if (stepSound == null) return;

            float volume = Math.min(1F, 0.4F + impactSpeed * 0.5F);
            float pitch = 0.9F + (float) Math.random() * 0.15F;

            world.playLocalSound(x, y, z, stepSound,
                net.minecraft.sounds.SoundSource.BLOCKS,
                volume, pitch, false);
        }
        catch (Throwable ignored)
        {
            /* Sound API failures shouldn't break fracture rendering. */
        }
    }

    /**
     * Spawn a spark + crit-magic burst at hard-impact locations for
     * cinematic flair. CRIT particle = small white/yellow streaks
     * (Minecraft uses it for critical hits — looks like sparks).
     * Combined with dust + sound + smoke trail this gives heavy
     * impacts a layered, weighty "wham" feel.
     */
    private static void spawnImpactSparks(Level world, float x, float y, float z, float impactSpeed)
    {
        try
        {
            int sparkCount = Math.min(12, Math.max(4, (int) (impactSpeed * 6F)));
            java.util.Random rng = new java.util.Random();
            for (int i = 0; i < sparkCount; i++)
            {
                double vx = (rng.nextDouble() - 0.5D) * impactSpeed * 0.8D;
                double vy = rng.nextDouble() * impactSpeed * 0.5D;
                double vz = (rng.nextDouble() - 0.5D) * impactSpeed * 0.8D;
                world.addParticle(net.minecraft.core.particles.ParticleTypes.CRIT, x, y, z, vx, vy, vz);
            }
        }
        catch (Throwable ignored) {}
    }

    /**
     * Spawn N PhysicsMod block-particles using the impacted block's
     * BlockState — gives the visual that the wall block has shattered
     * into chunks of its own material. The block itself stays put
     * (we don't modify world state for cinematic safety); the debris
     * is purely visual.
     *
     * <p>Velocity vector points outward from the impact point + away
     * from the bone body so chunks fly toward the camera convincingly.
     * Each chunk gets its own random scatter so the burst looks
     * organic rather than mechanically uniform.</p>
     */
    private static void spawnBlockShatter(BlockState block, int blockX, int blockY, int blockZ,
                                          float bodyX, float bodyY, float bodyZ,
                                          float impactSpeed, int count)
    {
        if (!FracturePhysicsBridge.isAvailable() || count <= 0) return;

        try
        {
            /* Outward direction = from body toward block center. */
            float outX = (blockX + 0.5F) - bodyX;
            float outY = (blockY + 0.5F) - bodyY;
            float outZ = (blockZ + 0.5F) - bodyZ;
            float len = (float) Math.sqrt(outX * outX + outY * outY + outZ * outZ);
            if (len > 1e-4F) { outX /= len; outY /= len; outZ /= len; }

            java.util.Random rng = new java.util.Random();
            float speedScale = Math.min(2F, 0.4F + impactSpeed * 0.5F);

            for (int i = 0; i < count; i++)
            {
                /* Spawn at the block's surface facing the body, with a
                 *  small random offset so chunks don't all start at
                 *  the same point. */
                double sx = blockX + 0.5F - outX * 0.4F + (rng.nextFloat() - 0.5F) * 0.4F;
                double sy = blockY + 0.5F - outY * 0.4F + (rng.nextFloat() - 0.5F) * 0.4F;
                double sz = blockZ + 0.5F - outZ * 0.4F + (rng.nextFloat() - 0.5F) * 0.4F;

                /* Velocity: outward direction + lateral scatter +
                 *  small upward bias for a visible arc. */
                double vx = -outX * speedScale + (rng.nextFloat() - 0.5F) * 0.5F;
                double vy = -outY * speedScale + rng.nextFloat() * 0.3F;
                double vz = -outZ * speedScale + (rng.nextFloat() - 0.5F) * 0.5F;

                FracturePhysicsBridge.spawnDebrisChunkWithState(sx, sy, sz, vx, vy, vz, block);
            }
        }
        catch (Throwable ignored) {}
    }

    /**
     * Spawn a quick dust burst at an impact location using the
     * impacted block's texture (BlockStateParticleEffect → block
     * particles look like the chunk that was disturbed). Particle
     * count + initial velocity scale with impact speed so a hard
     * crash produces a thicker burst than a gentle landing.
     *
     * <p>If PhysicsMod is loaded we additionally spawn a few
     * realistic-smoke puffs at the impact for a layered, volumetric
     * "boom" effect — the user's request was to prefer PhysicsMod's
     * smoke over vanilla SMOKE.</p>
     */
    private static void spawnImpactDust(Level world, float x, float y, float z,
                                        BlockState impactedBlock, float impactSpeed)
    {
        try
        {
            int count = Math.min(20, Math.max(3, (int) (impactSpeed * 8F)));
            net.minecraft.core.particles.BlockParticleOption effect =
                new net.minecraft.core.particles.BlockParticleOption(
                    net.minecraft.core.particles.ParticleTypes.BLOCK, impactedBlock);

            java.util.Random rng = new java.util.Random();
            for (int i = 0; i < count; i++)
            {
                double vx = (rng.nextDouble() - 0.5D) * impactSpeed * 0.6D;
                double vy = rng.nextDouble() * impactSpeed * 0.4D;
                double vz = (rng.nextDouble() - 0.5D) * impactSpeed * 0.6D;

                world.addParticle(effect, x, y, z, vx, vy, vz);
            }

            /* PhysicsMod smoke puffs co-located with the dust — gives
             *  the impact a real-volume haze that lingers + disperses
             *  rather than just tiny block-chip particles. Count + density
             *  scale with impact speed. */
            if (FracturePhysicsBridge.isSmokeAvailable() && impactSpeed > 0.2F)
            {
                int smokeCount = Math.min(6, Math.max(1, (int) (impactSpeed * 3F)));
                float density = Math.min(1.5F, 0.6F + impactSpeed * 0.8F);
                for (int i = 0; i < smokeCount; i++)
                {
                    double dx = (rng.nextDouble() - 0.5D) * 0.6D;
                    double dy = rng.nextDouble() * 0.4D;
                    double dz = (rng.nextDouble() - 0.5D) * 0.6D;
                    FracturePhysicsBridge.spawnRealisticSmoke(x + dx, y + dy, z + dz, density);
                }
            }
        }
        catch (Throwable ignored)
        {
            /* Particle spawn is purely visual fluff — never break
             *  fracture rendering if the particle API misbehaves. */
        }
    }

    /**
     * Pairwise AABB-overlap resolution. For each pair of non-settled
     * bodies whose world AABBs intersect, push them apart along the
     * smallest-penetration axis and exchange a small angular impulse
     * so the contact looks dynamic.
     */
    private static void resolveInterBody(List<BonePhysicsBody> bodies)
    {
        int n = bodies.size();
        for (int i = 0; i < n; i++)
        {
            BonePhysicsBody a = bodies.get(i);
            if (a.dead || a.settled) continue;

            for (int j = i + 1; j < n; j++)
            {
                BonePhysicsBody b = bodies.get(j);
                if (b.dead || b.settled) continue;

                /* Skip inter-body resolution between joint-constrained
                 *  parent-child pairs. The joint solver enforces the
                 *  bind-pose distance; pushing them apart on top of
                 *  that produces oscillating jitter (constraint pulls,
                 *  collision pushes, repeat). For pairs without joints
                 *  (jointStiffness=0 on both) we still resolve so
                 *  free-fly fracture pieces don't pass through each
                 *  other. */
                if ((a.jointStiffness > 0F || b.jointStiffness > 0F)
                    && (a.parentBoneIndex == b.boneIndex
                     || b.parentBoneIndex == a.boneIndex))
                {
                    continue;
                }

                float dx = b.position.x - a.position.x;
                float dy = b.position.y - a.position.y;
                float dz = b.position.z - a.position.z;
                float overlapX = (a.halfExtents.x + b.halfExtents.x) - Math.abs(dx);
                float overlapY = (a.halfExtents.y + b.halfExtents.y) - Math.abs(dy);
                float overlapZ = (a.halfExtents.z + b.halfExtents.z) - Math.abs(dz);

                if (overlapX <= 0F || overlapY <= 0F || overlapZ <= 0F) continue;

                /* Smallest-overlap axis = most likely contact direction. */
                if (overlapY < overlapX && overlapY < overlapZ)
                {
                    float push = overlapY * 0.5F * (dy >= 0F ? 1F : -1F);
                    b.position.y += push;
                    a.position.y -= push;
                    /* exchange a fraction of the y-velocities */
                    float avg = (a.linearVelocity.y + b.linearVelocity.y) * 0.5F;
                    a.linearVelocity.y = avg - 0.1F * Math.signum(push);
                    b.linearVelocity.y = avg + 0.1F * Math.signum(push);
                }
                else if (overlapX < overlapZ)
                {
                    float push = overlapX * 0.5F * (dx >= 0F ? 1F : -1F);
                    b.position.x += push;
                    a.position.x -= push;
                    float avg = (a.linearVelocity.x + b.linearVelocity.x) * 0.5F;
                    a.linearVelocity.x = avg - 0.1F * Math.signum(push);
                    b.linearVelocity.x = avg + 0.1F * Math.signum(push);
                }
                else
                {
                    float push = overlapZ * 0.5F * (dz >= 0F ? 1F : -1F);
                    b.position.z += push;
                    a.position.z -= push;
                    float avg = (a.linearVelocity.z + b.linearVelocity.z) * 0.5F;
                    a.linearVelocity.z = avg - 0.1F * Math.signum(push);
                    b.linearVelocity.z = avg + 0.1F * Math.signum(push);
                }
            }
        }
    }

    /** Hard caps so numerical issues / weird input don't produce
     *  ridiculous physics blow-ups (bones flying at lightspeed). */
    private static final float MAX_LINEAR_SPEED = 8F;
    private static final float MAX_ANGULAR_SPEED = 1.5F;
    private static final float MAX_LINEAR_SQ = MAX_LINEAR_SPEED * MAX_LINEAR_SPEED;

    private static void tickBody(BonePhysicsBody b, Level world)
    {
        /* 1. Apply gravity + damping to linear motion. Constants
         *  come from the body itself (set by FractureSpawner from
         *  the action clip's tunable fields). */
        b.linearVelocity.y += b.gravity;
        b.linearVelocity.mul(b.linearDamping);

        /* 1b. Velocity caps — prevents tangential-impulse loops
         *  or extreme actor velocities from accumulating into
         *  inhumanly fast bodies. */
        if (b.linearVelocity.lengthSquared() > MAX_LINEAR_SQ)
        {
            b.linearVelocity.normalize().mul(MAX_LINEAR_SPEED);
        }
        if (b.angularVelocity.x > MAX_ANGULAR_SPEED) b.angularVelocity.x = MAX_ANGULAR_SPEED;
        if (b.angularVelocity.x < -MAX_ANGULAR_SPEED) b.angularVelocity.x = -MAX_ANGULAR_SPEED;
        if (b.angularVelocity.y > MAX_ANGULAR_SPEED) b.angularVelocity.y = MAX_ANGULAR_SPEED;
        if (b.angularVelocity.y < -MAX_ANGULAR_SPEED) b.angularVelocity.y = -MAX_ANGULAR_SPEED;
        if (b.angularVelocity.z > MAX_ANGULAR_SPEED) b.angularVelocity.z = MAX_ANGULAR_SPEED;
        if (b.angularVelocity.z < -MAX_ANGULAR_SPEED) b.angularVelocity.z = -MAX_ANGULAR_SPEED;

        /* 2. Integrate position — prevPosition snapshots state at
         *  start of tick so the renderer can interpolate prev → now
         *  for sub-tick smoothness at high frame rates. */
        b.prevPosition.set(b.position);
        b.prevRotation.set(b.rotation);
        b.position.add(b.linearVelocity);

        /* 3. World-block collision (simple AABB-vs-block) */
        if (world != null) resolveCollision(b, world);

        /* 3b. Smoke trail: bones moving fast through the air kick up
         *  a small wake. Threshold-gated so settling bodies don't
         *  emit; intensity scales with speed so a violently flying
         *  cabin trails more than a tumbling wheel. */
        if (world != null) emitTrailIfFast(b, world);

        /* 4. Angular damping + integrate rotation. */
        b.angularVelocity.mul(b.angularDamping);
        Quaternionf delta = new Quaternionf().rotateXYZ(
            b.angularVelocity.x, b.angularVelocity.y, b.angularVelocity.z);
        b.rotation.premul(delta);
        b.rotation.normalize();

        /* 5. Settle detection — when motion drops below threshold for
         *  N consecutive ticks, lock the body's pose. We additionally
         *  hard-freeze prev/current state so render-interpolation
         *  produces exactly the same matrix every frame (no jitter
         *  from floating-point drift). */
        float linSq = b.linearVelocity.lengthSquared();
        float angSq = b.angularVelocity.lengthSquared();
        if (linSq + angSq < SETTLE_VEL_SQ)
        {
            b.settledCounter++;
            if (b.settledCounter >= SETTLE_TICKS)
            {
                b.settled = true;
                /* Lock prev = current so interpolation freezes the
                 *  body at its final pose exactly. Zero out the
                 *  velocities to prevent any further accumulation. */
                b.prevPosition.set(b.position);
                b.prevRotation.set(b.rotation);
                b.linearVelocity.zero();
                b.angularVelocity.zero();
                LOG.debug("[FRACTURE-DIAG] body for bone '{}' settled at ({}, {}, {})",
                    b.boneName, b.position.x, b.position.y, b.position.z);
            }
        }
        else
        {
            b.settledCounter = 0;
        }
    }

    /**
     * Resolve AABB-vs-block penetration. Iterate blocks within the
     * body's bounding box; for each non-air block, push the body out
     * along the dominant penetration axis and dampen velocity on
     * that axis (bounce). Simple but visually OK for cinematic falls.
     */
    private static void resolveCollision(BonePhysicsBody b, Level world)
    {
        float minX = b.position.x - b.halfExtents.x;
        float minY = b.position.y - b.halfExtents.y;
        float minZ = b.position.z - b.halfExtents.z;
        float maxX = b.position.x + b.halfExtents.x;
        float maxY = b.position.y + b.halfExtents.y;
        float maxZ = b.position.z + b.halfExtents.z;

        int bx0 = (int) Math.floor(minX), bx1 = (int) Math.floor(maxX);
        int by0 = (int) Math.floor(minY), by1 = (int) Math.floor(maxY);
        int bz0 = (int) Math.floor(minZ), bz1 = (int) Math.floor(maxZ);

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int x = bx0; x <= bx1; x++)
        {
            for (int y = by0; y <= by1; y++)
            {
                for (int z = bz0; z <= bz1; z++)
                {
                    pos.set(x, y, z);
                    BlockState s;
                    try { s = world.getBlockState(pos); }
                    catch (Throwable t) { continue; }
                    if (s.isAir()) continue;

                    /* Compute penetration along each axis. The smallest
                     *  penetration is the resolution direction. */
                    float penX = Math.min(maxX - x, x + 1F - minX);
                    float penY = Math.min(maxY - y, y + 1F - minY);
                    float penZ = Math.min(maxZ - z, z + 1F - minZ);

                    if (penY < penX && penY < penZ)
                    {
                        /* Vertical resolution — most common for falling debris. */
                        if (b.linearVelocity.y < 0F)
                        {
                            /* Capture impact speed for the dust effect. */
                            float impactSpeed = -b.linearVelocity.y;

                            /* Hit ground: clamp position, bounce + dampen. */
                            b.position.y = (float) y + 1F + b.halfExtents.y;
                            b.linearVelocity.y *= b.groundBounce;
                            b.linearVelocity.x *= 0.85F;
                            b.linearVelocity.z *= 0.85F;

                            /* Tangential-to-angular impulse. */
                            float tanX = b.linearVelocity.x;
                            float tanZ = b.linearVelocity.z;
                            b.angularVelocity.x += tanZ * 0.6F;
                            b.angularVelocity.z -= tanX * 0.6F;
                            b.angularVelocity.mul(0.85F);

                            /* Dust burst — gated by per-body toggle. */
                            if (b.enableImpactDust && impactSpeed > 0.15F)
                            {
                                spawnImpactDust(world, b.position.x, b.position.y - b.halfExtents.y,
                                    b.position.z, s, impactSpeed);
                            }

                            /* HARD impact (>0.4 blocks/tick): add spark
                             *  + crit-hit-magic particles for that
                             *  metallic "wham" feel. Same toggle as
                             *  dust — sparks are an extension of dust. */
                            if (b.enableImpactDust && impactSpeed > 0.4F)
                            {
                                spawnImpactSparks(world, b.position.x,
                                    b.position.y - b.halfExtents.y, b.position.z, impactSpeed);
                            }

                            /* Camera shake on heavy impacts — gated by the
                             *  per-body toggle so it's opt-in. We feed
                             *  impact speed straight to the shake
                             *  manager which scales it to degrees of
                             *  pitch/yaw perturbation. */
                            if (b.enableCameraShake
                                && impactSpeed > CameraShakeManager.IMPACT_SHAKE_THRESHOLD)
                            {
                                CameraShakeManager.addShake(impactSpeed);
                            }

                            /* Block-shatter on hard impact — spawn N
                             *  PhysicsMod debris chunks using THIS
                             *  block's BlockState (so a stone wall
                             *  throws stone chunks, oak beam throws
                             *  oak chunks). The block stays put — the
                             *  debris is purely visual cinematic. */
                            if (b.enableBlockShatter && impactSpeed > 0.3F && b.blockShatterCount > 0)
                            {
                                spawnBlockShatter(s, x, y, z, b.position.x, b.position.y, b.position.z,
                                    impactSpeed, b.blockShatterCount);
                            }

                            /* Impact-point propagation — radial wave
                             *  fragments surrounding blocks. Only on
                             *  HARD ground impacts so a gentle landing
                             *  doesn't trigger a chain reaction. */
                            if (b.enablePropagation && impactSpeed > 0.5F)
                            {
                                BlockShatterPropagator.trigger(
                                    new BlockPos(x, y, z),
                                    new org.joml.Vector3f(
                                        b.linearVelocity.x,
                                        b.linearVelocity.y,
                                        b.linearVelocity.z),
                                    b.propagationRadius,
                                    b.propagationSpeed,
                                    Math.max(1, b.blockShatterCount / 2),
                                    b.destroyBlocks);
                            }

                            /* First-impact sound — gated by per-body toggle. */
                            if (b.enableImpactSound && !b.playedFirstImpactSound && impactSpeed > 0.2F)
                            {
                                playImpactSound(world, b.position.x,
                                    b.position.y - b.halfExtents.y, b.position.z,
                                    s, impactSpeed);
                                b.playedFirstImpactSound = true;
                            }
                        }
                        else
                        {
                            b.position.y = (float) y - b.halfExtents.y;
                            b.linearVelocity.y = 0F;
                        }
                    }
                    else if (penX < penZ)
                    {
                        /* X-axis wall hit: bounce + add angular impulse
                         *  perpendicular to motion (wall normal × velocity).
                         *  For an X-wall the normal is ±X; cross with the
                         *  body's vY/vZ produces a Z/Y rotation. */
                        float wallImpactSpeed = Math.abs(b.linearVelocity.x);
                        if (b.linearVelocity.x > 0F)
                        {
                            b.position.x = (float) x - b.halfExtents.x;
                            b.linearVelocity.x *= WALL_BOUNCE;
                        }
                        else
                        {
                            b.position.x = (float) x + 1F + b.halfExtents.x;
                            b.linearVelocity.x *= WALL_BOUNCE;
                        }
                        b.angularVelocity.y += b.linearVelocity.z * 0.4F;
                        b.angularVelocity.z -= b.linearVelocity.y * 0.4F;
                        b.angularVelocity.mul(0.85F);

                        if (b.enableBlockShatter && wallImpactSpeed > 0.3F && b.blockShatterCount > 0)
                        {
                            spawnBlockShatter(s, x, y, z, b.position.x, b.position.y, b.position.z,
                                wallImpactSpeed, b.blockShatterCount);
                        }
                        if (b.enablePropagation && wallImpactSpeed > 0.5F)
                        {
                            BlockShatterPropagator.trigger(
                                new BlockPos(x, y, z),
                                new org.joml.Vector3f(
                                    b.linearVelocity.x,
                                    b.linearVelocity.y,
                                    b.linearVelocity.z),
                                b.propagationRadius,
                                b.propagationSpeed,
                                Math.max(1, b.blockShatterCount / 2),
                                b.destroyBlocks);
                        }
                        if (b.enableImpactDust && wallImpactSpeed > 0.3F)
                        {
                            spawnImpactDust(world, b.position.x, b.position.y, b.position.z, s, wallImpactSpeed);
                        }
                        if (b.enableCameraShake && wallImpactSpeed > CameraShakeManager.IMPACT_SHAKE_THRESHOLD)
                        {
                            CameraShakeManager.addShake(wallImpactSpeed);
                        }
                    }
                    else
                    {
                        /* Z-axis wall hit: similar tangential impulse. */
                        float wallImpactSpeed = Math.abs(b.linearVelocity.z);
                        if (b.linearVelocity.z > 0F)
                        {
                            b.position.z = (float) z - b.halfExtents.z;
                            b.linearVelocity.z *= WALL_BOUNCE;
                        }
                        else
                        {
                            b.position.z = (float) z + 1F + b.halfExtents.z;
                            b.linearVelocity.z *= WALL_BOUNCE;
                        }
                        b.angularVelocity.x += b.linearVelocity.y * 0.4F;
                        b.angularVelocity.y -= b.linearVelocity.x * 0.4F;
                        b.angularVelocity.mul(0.85F);

                        if (b.enableBlockShatter && wallImpactSpeed > 0.3F && b.blockShatterCount > 0)
                        {
                            spawnBlockShatter(s, x, y, z, b.position.x, b.position.y, b.position.z,
                                wallImpactSpeed, b.blockShatterCount);
                        }
                        if (b.enablePropagation && wallImpactSpeed > 0.5F)
                        {
                            BlockShatterPropagator.trigger(
                                new BlockPos(x, y, z),
                                new org.joml.Vector3f(
                                    b.linearVelocity.x,
                                    b.linearVelocity.y,
                                    b.linearVelocity.z),
                                b.propagationRadius,
                                b.propagationSpeed,
                                Math.max(1, b.blockShatterCount / 2),
                                b.destroyBlocks);
                        }
                        if (b.enableImpactDust && wallImpactSpeed > 0.3F)
                        {
                            spawnImpactDust(world, b.position.x, b.position.y, b.position.z, s, wallImpactSpeed);
                        }
                        if (b.enableCameraShake && wallImpactSpeed > CameraShakeManager.IMPACT_SHAKE_THRESHOLD)
                        {
                            CameraShakeManager.addShake(wallImpactSpeed);
                        }
                    }

                    /* One block-resolution per tick is enough — picking
                     *  the smallest penetration here is approximate but
                     *  fine for cinematic visuals. Bail to next tick. */
                    return;
                }
            }
        }
    }

    /**
     * Called by {@code BOBJArmatureFractureOverrideMixin} at TAIL of
     * setupMatrices. Walks the actor's active bodies and overrides
     * the corresponding {@code armature.matrices[i]} with the body's
     * current world transform.
     *
     * <p>{@code cameraOrigin} is the camera's world position which we
     * subtract because BBS's renderer expects matrices in
     * camera-relative space (so vertex transforms produce small
     * floats stable for GPU). The body's world coords get translated
     * into the same frame.</p>
     */
    public static synchronized void applyOverrides(IEntity actor, BOBJArmature armature)
    {
        if (actor == null || armature == null || armature.matrices == null) return;

        List<BonePhysicsBody> bodies = BY_ACTOR.get(actor);
        if (bodies == null || bodies.isEmpty()) return;

        double actorX = actor.getX();
        double actorY = actor.getY();
        double actorZ = actor.getZ();

        /* Read MC's render tick-delta (sub-tick fraction for smooth
         *  60+ fps render) so we can lerp body positions from prev →
         *  current state, eliminating the 20Hz "stepping" look. */
        float tickDelta;
        try
        {
            /* 26.2: Minecraft#getTimer heisst getDeltaTracker — dieselbe Quelle, gleicher
             * Rueckgabewert, nur umbenannt. */
            tickDelta = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
            if (tickDelta < 0F) tickDelta = 0F;
            if (tickDelta > 1F) tickDelta = 1F;
        }
        catch (Throwable t)
        {
            tickDelta = 1F;
        }

        Matrix4f scratch = new Matrix4f();
        for (BonePhysicsBody b : bodies)
        {
            if (b.dead) continue;
            if (b.boneIndex < 0 || b.boneIndex >= armature.matrices.length) continue;

            Matrix4f override = b.computeOverrideMatrixInterpolated(
                actorX, actorY, actorZ, tickDelta, scratch);
            armature.matrices[b.boneIndex] = new Matrix4f(override);
        }
    }

    /**
     * Despawn all bodies belonging to a specific actor — used by
     * {@link FractureRegistry} on rewind / loop-reset / film-stop.
     */
    public static synchronized void clearActor(IEntity actor)
    {
        if (actor == null) return;
        List<BonePhysicsBody> bodies = BY_ACTOR.remove(actor);
        if (bodies != null)
        {
            for (BonePhysicsBody b : bodies) b.dead = true;
        }
    }

    public static synchronized void clearAll()
    {
        for (List<BonePhysicsBody> bodies : BY_ACTOR.values())
        {
            for (BonePhysicsBody b : bodies) b.dead = true;
        }
        BY_ACTOR.clear();
    }

    public static synchronized int activeBodyCount()
    {
        int n = 0;
        for (List<BonePhysicsBody> bodies : BY_ACTOR.values()) n += bodies.size();
        return n;
    }

    /**
     * Defensive snapshot of the per-actor body lists for read-only
     * traversal outside the simulator's locked tick. Used by the
     * gravity-gun raycaster to find the closest fracture body without
     * having to expose the internal map.
     */
    public static synchronized java.util.Map<IEntity, List<BonePhysicsBody>> snapshotBodies()
    {
        java.util.Map<IEntity, List<BonePhysicsBody>> copy = new java.util.HashMap<>(BY_ACTOR.size() * 2);
        for (java.util.Map.Entry<IEntity, List<BonePhysicsBody>> e : BY_ACTOR.entrySet())
        {
            copy.put(e.getKey(), new java.util.ArrayList<>(e.getValue()));
        }
        return copy;
    }

    /** Returns true if this actor already has a fracture body for the
     *  given bone — used by spawn paths to avoid duplicate bodies on
     *  re-trigger or when the collision watcher revisits a bone that
     *  fractured the previous tick. */
    public static synchronized boolean hasFractured(IEntity actor, int boneIndex)
    {
        if (actor == null || boneIndex < 0) return false;
        List<BonePhysicsBody> bodies = BY_ACTOR.get(actor);
        if (bodies == null) return false;
        for (BonePhysicsBody b : bodies)
        {
            if (!b.dead && b.boneIndex == boneIndex) return true;
        }
        return false;
    }

}
