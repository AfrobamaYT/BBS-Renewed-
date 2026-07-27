package com.afrobama.bbstools.client.fracture;

import mchorse.bbs_mod.forms.entities.IEntity;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Spawn-side glue for Phase B fracture: extracts BOBJ bone fragments
 * and creates a {@link BonePhysicsBody} per bone, registered with
 * {@link BonePhysicsSimulator} for per-tick gravity integration. The
 * actor's mesh keeps rendering with its original BOBJ texture, but
 * each fractured bone's matrix is overridden each frame by the
 * {@code BOBJArmatureFractureOverrideMixin} so the bone visually
 * tumbles through the world while preserving its original
 * BOBJ-modelled silhouette.
 *
 * <p>Phase A (PhysicsMod stone-block-particle) was scrapped after we
 * confirmed PhysicsMod's render path can't easily be made invisible
 * — the custom verlet/euler simulator gives us full bone-matrix
 * control without fighting PhysicsMod's particle render loop.</p>
 */
public final class FractureSpawner
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/fracture/spawner");
    private static final java.util.Random RNG = new java.util.Random();

    public static final class Result
    {
        public final List<BonePhysicsBody> spawnedBodies;
        public final Set<Integer> fracturedBoneIndices;

        public Result(List<BonePhysicsBody> spawnedBodies, Set<Integer> fracturedBoneIndices)
        {
            this.spawnedBodies = spawnedBodies;
            this.fracturedBoneIndices = fracturedBoneIndices;
        }

        public boolean isEmpty()
        {
            return this.spawnedBodies == null || this.spawnedBodies.isEmpty();
        }
    }

    private FractureSpawner()
    {
    }

    /**
     * Spawn fracture bodies for the entire actor (or for the bones
     * whose names are in {@code allowedBoneNames} when group filtering
     * is desired).
     *
     * @param actor             the BBS replay actor
     * @param actorWorldX       actor's world X at fracture moment
     *                          (used as the body's initial position)
     * @param actorWorldY       actor world Y
     * @param actorWorldZ       actor world Z
     * @param actorVelocity     actor's linear velocity (blocks/tick) at
     *                          trigger tick — null for stationary
     * @param forceJitter       random impulse magnitude (0..2) added to
     *                          each piece's velocity for chaos
     * @param allowedBoneNames  null = all bones; non-null = filter
     * @param spawnTick         the film tick at which fracture fires
     * @param lifetimeTicks     auto-despawn after N ticks
     */
    public static Result fracture(IEntity actor,
                                  double actorWorldX,
                                  double actorWorldY,
                                  double actorWorldZ,
                                  Vector3f actorVelocity,
                                  float forceJitter,
                                  Set<String> allowedBoneNames,
                                  int spawnTick,
                                  int lifetimeTicks)
    {
        return fracture(actor, actorWorldX, actorWorldY, actorWorldZ,
            actorVelocity, forceJitter, allowedBoneNames, spawnTick,
            lifetimeTicks, null);
    }

    /**
     * Same as the no-clip variant but also copies the action clip's
     * tunable physics fields onto each spawned body so per-clip
     * gravity / damping / bounce / angular jitter takes effect.
     */
    public static Result fracture(IEntity actor,
                                  double actorWorldX,
                                  double actorWorldY,
                                  double actorWorldZ,
                                  Vector3f actorVelocity,
                                  float forceJitter,
                                  Set<String> allowedBoneNames,
                                  int spawnTick,
                                  int lifetimeTicks,
                                  com.afrobama.bbstools.fracture.ModelFractureActionClip tuning)
    {
        LOG.info("[FRACTURE-DIAG] FractureSpawner.fracture entered — actor={}",
            actor == null ? "NULL" : "ok");

        if (actor == null)
        {
            return new Result(new ArrayList<>(), new HashSet<>());
        }

        List<BoneMeshExtractor.BoneFragment> fragments = BoneMeshExtractor.extract(actor);
        if (fragments.isEmpty())
        {
            LOG.warn("[FRACTURE-DIAG] BoneMeshExtractor returned 0 fragments — see preceding diags");
            return new Result(new ArrayList<>(), new HashSet<>());
        }

        List<BonePhysicsBody> spawned = new ArrayList<>();
        Set<Integer> fracturedIndices = new HashSet<>();

        for (BoneMeshExtractor.BoneFragment frag : fragments)
        {
            if (allowedBoneNames != null && !allowedBoneNames.isEmpty()
                && !allowedBoneNames.contains(frag.boneName))
            {
                continue;
            }
            if (frag.isEmpty()) continue;
            if (BonePhysicsSimulator.hasFractured(actor, frag.boneIndex))
            {
                /* Already physics-driven from a prior trigger — skip
                 *  to avoid duplicate bodies for the same bone. */
                continue;
            }

            BonePhysicsBody body = createBodyFor(actor, frag,
                actorWorldX, actorWorldY, actorWorldZ,
                actorVelocity, forceJitter, spawnTick, lifetimeTicks);

            /* Copy per-clip tunable physics onto the body. Without
             *  this, defaults from BonePhysicsBody are used. */
            if (tuning != null)
            {
                body.gravity        = -Math.abs(tuning.gravity.get());
                body.linearDamping  = tuning.linearDamping.get();
                body.angularDamping = tuning.angularDamping.get();
                body.groundBounce   = -Math.abs(tuning.groundBounce.get());
                /* Rebuild angular velocity with custom jitter scale. */
                float aj = tuning.angularJitter.get();
                body.angularVelocity.set(
                    (RNG.nextFloat() - 0.5F) * aj,
                    (RNG.nextFloat() - 0.5F) * aj,
                    (RNG.nextFloat() - 0.5F) * aj);

                /* Particle / sound toggles. */
                body.enableImpactDust  = tuning.enableImpactDust.get();
                body.enableImpactSound = tuning.enableImpactSound.get();
                body.enableSmokeTrail  = tuning.enableSmokeTrail.get();
                body.enableCameraShake = tuning.enableCameraShake.get();

                /* Velocity multiplier scales the inherited-actor
                 *  velocity component up or down. */
                float velMul = tuning.velocityMultiplier.get();
                if (velMul != 1F)
                {
                    body.linearVelocity.mul(velMul);
                }

                /* Directional impulse adds a fixed vector to every
                 *  body's initial velocity — useful for explosion-
                 *  style fractures or directional ricochets. */
                body.linearVelocity.x += tuning.impulseX.get();
                body.linearVelocity.y += tuning.impulseY.get();
                body.linearVelocity.z += tuning.impulseZ.get();

                /* Ragdoll joint stiffness — propagate so the simulator's
                 *  constraint solver pulls bones toward their bind-pose
                 *  parents each tick. parentBoneIndex was already set
                 *  in createBodyFor from the fragment's parent linkage. */
                body.jointStiffness = tuning.jointStiffness.get();

                /* Slow-motion factor — < 1 makes pieces fall slower
                 *  for cinematic bullet-time, > 1 speeds things up. */
                body.slowMotion = tuning.slowMotion.get();

                /* Block-shatter — when a body hits a wall hard, spawn
                 *  PhysicsMod debris using THAT wall block's material. */
                body.enableBlockShatter = tuning.enableBlockShatter.get();
                body.blockShatterCount = tuning.blockShatterCount.get();

                /* Impact-point propagation — radial wave that fragments
                 *  surrounding blocks tick-by-tick from the impact site. */
                body.enablePropagation = tuning.enablePropagation.get();
                body.propagationRadius = tuning.propagationRadius.get();
                body.propagationSpeed = tuning.propagationSpeed.get();
                body.destroyBlocks = tuning.destroyBlocks.get();
            }

            BonePhysicsSimulator.add(body);
            spawned.add(body);
            fracturedIndices.add(frag.boneIndex);

            LOG.info("[FRACTURE-DIAG]   bone '{}' (idx {}) fracture body created at world ({}, {}, {}), vel=({}, {}, {})",
                frag.boneName, frag.boneIndex,
                body.position.x, body.position.y, body.position.z,
                body.linearVelocity.x, body.linearVelocity.y, body.linearVelocity.z);

            /* Piece-count multiplier: spawn (pieceCount-1) extra
             *  PhysicsMod-rendered debris chunks per bone, scattered
             *  inside the bone's AABB with random velocities centred
             *  on the bone's velocity. Gives the "extremely subdivided"
             *  look the user requested without needing a separate
             *  render path — PhysicsMod renders these block-particles
             *  itself with its own collision + tumble simulation. */
            if (tuning != null && tuning.pieceCount.get() > 1)
            {
                int extra = tuning.pieceCount.get() - 1;
                spawnDebrisCloud(body, frag, extra);
            }
        }

        /* Single-shot warning when the user picked a piece count that
         *  produces > ~250 PhysicsMod debris chunks in this fracture.
         *  PhysicsMod has its own particle ceiling and the extras get
         *  silently dropped past that. The BBS bone-bodies always
         *  spawn (those go through our own simulator) so the main
         *  cinematic effect still works — only the additional debris
         *  is capped. */
        if (tuning != null && tuning.pieceCount.get() > 1)
        {
            int totalExtras = spawned.size() * (tuning.pieceCount.get() - 1);
            if (totalExtras > 250)
            {
                LOG.info("Fracture requested {} debris chunks ({} bones × {} extras). PhysicsMod's particle cap may drop some. Lower pieceCount if visible debris loss is undesirable.",
                    totalExtras, spawned.size(), tuning.pieceCount.get() - 1);
            }
        }

        LOG.info("[FRACTURE-DIAG] FractureSpawner produced {} bodies for actor", spawned.size());
        return new Result(spawned, fracturedIndices);
    }

    private static BonePhysicsBody createBodyFor(IEntity actor,
                                                 BoneMeshExtractor.BoneFragment frag,
                                                 double actorX, double actorY, double actorZ,
                                                 Vector3f actorVelocity,
                                                 float forceJitter,
                                                 int spawnTick,
                                                 int lifetimeTicks)
    {
        BonePhysicsBody body = new BonePhysicsBody(actor, frag.boneIndex, frag.boneName,
            spawnTick, lifetimeTicks);

        /* Bind-pose pivot in model-local space — translation part of
         *  bone.boneMat. This is the point the bone naturally pivots
         *  around. */
        body.bindPivot.set(
            frag.bindBoneMatrix.m30(),
            frag.bindBoneMatrix.m31(),
            frag.bindBoneMatrix.m32());

        /* Capture the skinning matrix at fracture time so the override
         *  math can produce the bone's CURRENT animated transform.
         *  For an actor at bind/rest pose this is identity; for an
         *  animated actor it's the live skinning delta. */
        body.initialBoneMatrix.set(frag.worldMatrix);

        /* Compute the animated pivot in model-local space:
         *  worldMatrix * bindPivot (treated as a position) gives the
         *  bone's current joint position after the actor's animation
         *  has been applied. For idle/bind-pose this equals bindPivot. */
        body.animatedPivot.set(body.bindPivot);
        body.initialBoneMatrix.transformPosition(body.animatedPivot);

        /* Joint-ragdoll linkage — recorded on the body so the simulator
         *  can find the parent body during the constraint pass. */
        body.parentBoneIndex = frag.parentBoneIndex;
        body.bindDistanceToParent = frag.bindDistanceToParent;

        /* Initial body world position: actor world coords + ANIMATED
         *  pivot (so at t=0 the bone stays exactly at its current
         *  rendered position, even mid-animation). Reduces to bind
         *  pose for unanimated actors. */
        body.position.set(
            (float) (actorX + body.animatedPivot.x),
            (float) (actorY + body.animatedPivot.y),
            (float) (actorZ + body.animatedPivot.z));
        body.prevPosition.set(body.position);

        body.initialActorX = actorX;
        body.initialActorY = actorY;
        body.initialActorZ = actorZ;

        /* Initial rotation: identity (bone keeps bind-pose orientation
         *  at fracture moment). The override math derives rotation
         *  delta from this; starting at identity means t=0 visual
         *  exactly matches pre-fracture. */
        body.rotation.identity();

        /* Linear velocity: actor velocity + jitter. */
        if (actorVelocity != null) body.linearVelocity.set(actorVelocity);
        if (forceJitter > 0F)
        {
            body.linearVelocity.x += (RNG.nextFloat() - 0.5F) * 2F * forceJitter;
            body.linearVelocity.y += RNG.nextFloat() * forceJitter; /* upward bias */
            body.linearVelocity.z += (RNG.nextFloat() - 0.5F) * 2F * forceJitter;
        }

        /* Angular velocity: small random tumble. Real impact would
         *  derive this from the cross-product of impact-point and
         *  impact-force, but for cinematic shots a random tumble
         *  looks great and is far simpler. */
        body.angularVelocity.set(
            (RNG.nextFloat() - 0.5F) * 0.15F,
            (RNG.nextFloat() - 0.5F) * 0.15F,
            (RNG.nextFloat() - 0.5F) * 0.15F);

        /* Half-extents from bone's local AABB — used by the simulator
         *  for ground/wall collision. */
        computeHalfExtents(frag.verticesLocal, body.halfExtents);

        /* Per-body scale jitter — small random size variation so
         *  even bones of similar mesh look distinct as debris.
         *  ±5% by default; cinematic eye notices uniform debris
         *  immediately as "fake". Each body gets a different value.
         *  baseScale stores the spawn-time value so the lifetime
         *  fade-out tick math is stable (won't compound). */
        body.baseScale = 0.95F + RNG.nextFloat() * 0.10F;
        body.visualScale = body.baseScale;

        return body;
    }

    /**
     * Spawn N debris chunks scattered around a parent bone's body.
     * Each chunk is a PhysicsMod block-particle with a random initial
     * position inside the bone's AABB and a velocity equal to the
     * bone's velocity plus a small random scatter.
     *
     * <p>Doesn't allocate new {@link BonePhysicsBody} objects — these
     * chunks are PhysicsMod's own particles, not BBS-rendered bodies.
     * Despawn is also handled by PhysicsMod (its own lifetime cap).</p>
     */
    private static void spawnDebrisCloud(BonePhysicsBody parent,
                                         BoneMeshExtractor.BoneFragment frag,
                                         int count)
    {
        if (!FracturePhysicsBridge.isAvailable() || count <= 0) return;

        /* Spread = AABB half-extents so chunks are scattered through
         *  the bone's volume rather than clumped at one point. */
        float sx = parent.halfExtents.x;
        float sy = parent.halfExtents.y;
        float sz = parent.halfExtents.z;

        for (int i = 0; i < count; i++)
        {
            /* Position: bone center + random offset within the AABB. */
            double px = parent.position.x + (RNG.nextFloat() - 0.5F) * 2F * sx;
            double py = parent.position.y + (RNG.nextFloat() - 0.5F) * 2F * sy;
            double pz = parent.position.z + (RNG.nextFloat() - 0.5F) * 2F * sz;

            /* Velocity: bone velocity + lateral scatter + small upward
             *  bias so debris arcs nicely instead of just falling. */
            double vx = parent.linearVelocity.x + (RNG.nextFloat() - 0.5F) * 0.6F;
            double vy = parent.linearVelocity.y + RNG.nextFloat() * 0.4F + 0.05F;
            double vz = parent.linearVelocity.z + (RNG.nextFloat() - 0.5F) * 0.6F;

            FracturePhysicsBridge.spawnDebrisChunk(px, py, pz, vx, vy, vz, frag.boneIndex + i);
        }
    }

    private static void computeHalfExtents(List<Vector3f> vertices, Vector3f outHalfExtents)
    {
        if (vertices == null || vertices.isEmpty())
        {
            outHalfExtents.set(0.3F, 0.3F, 0.3F);
            return;
        }

        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (Vector3f v : vertices)
        {
            if (v.x < minX) minX = v.x;
            if (v.y < minY) minY = v.y;
            if (v.z < minZ) minZ = v.z;
            if (v.x > maxX) maxX = v.x;
            if (v.y > maxY) maxY = v.y;
            if (v.z > maxZ) maxZ = v.z;
        }
        outHalfExtents.set(
            Math.max(0.05F, (maxX - minX) * 0.5F),
            Math.max(0.05F, (maxY - minY) * 0.5F),
            Math.max(0.05F, (maxZ - minZ) * 0.5F));
    }

}
