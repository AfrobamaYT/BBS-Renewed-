package com.afrobama.bbstools.client.fracture;

import mchorse.bbs_mod.forms.entities.IEntity;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Per-bone physics state for the verlet/euler bone-shatter simulator
 * used by Phase B fracture. One instance per fractured bone.
 *
 * <p><b>Why custom sim instead of PhysicsMod?</b> PhysicsMod's spawn
 * APIs require a Model with both visual + physics meshes; making the
 * visual invisible without breaking the physics path is fragile. Our
 * use case only needs gravity + simple block collision + a transform
 * we can read each frame. A 100-line custom integrator gives us full
 * control + zero PhysicsMod render conflicts and keeps the bone
 * rendering through BBS's normal BOBJ pipeline with its original
 * texture intact.</p>
 *
 * <h2>State</h2>
 * <ul>
 *   <li>{@link #position} world position of the bone's pivot</li>
 *   <li>{@link #prevPosition} for verlet velocity inference</li>
 *   <li>{@link #linearVelocity} blocks/tick</li>
 *   <li>{@link #rotation} world-space orientation quaternion</li>
 *   <li>{@link #angularVelocity} radians/tick around each axis</li>
 *   <li>{@link #initialBoneMatrix} the bone's BBS world matrix at
 *       fracture time — used to compute the matrix-override correctly
 *       (we apply (currentTransform * initialMatrix^-1) so vertex
 *       skinning ends up at world position)</li>
 * </ul>
 *
 * <h2>Lifecycle</h2>
 * <p>Bodies persist until the simulator marks them as {@link #dead}
 * (lifetime exceeded, settled below threshold, or explicit despawn).
 * The {@link FractureRegistry} cleanup path reads {@link #dead} and
 * stops calling tick on dead bodies.</p>
 */
public final class BonePhysicsBody
{
    public final IEntity actor;
    public final int boneIndex;
    public final String boneName;

    public final Vector3f position = new Vector3f();
    public final Vector3f prevPosition = new Vector3f();
    public final Vector3f linearVelocity = new Vector3f();

    public final Quaternionf rotation = new Quaternionf();
    public final Quaternionf prevRotation = new Quaternionf();
    public final Vector3f angularVelocity = new Vector3f();

    /** Bone's bind-pose pivot in MODEL-LOCAL space — the translation
     *  part of {@code bone.boneMat}. Vertices that belong to this bone
     *  are roughly clustered around this point in their bind pose.
     *  We subtract this in the override matrix so rotation pivots
     *  around the bone's natural centre instead of the model origin. */
    public final org.joml.Vector3f bindPivot = new org.joml.Vector3f();

    /** Bone's CURRENT pivot at fracture time — equals
     *  {@code initialBoneMatrix.transformPosition(bindPivot)} which
     *  for an animated actor differs from {@link #bindPivot}. For an
     *  idle actor at bind pose these are equal. The override matrix
     *  uses this so the body visually spawns at the actor's current
     *  pose pivot rather than snapping back to bind pose. */
    public final org.joml.Vector3f animatedPivot = new org.joml.Vector3f();

    /** Skinning matrix at fracture time
     *  ({@code armature.matrices[boneIndex]}). Captured once at spawn
     *  and used in the override math so bone vertices stay in their
     *  animated positions — not snapped back to bind pose. For
     *  unanimated bones this is identity and the override math
     *  reduces to the original bind-pose-spawn formula. */
    public final org.joml.Matrix4f initialBoneMatrix = new org.joml.Matrix4f();

    /** Actor's world position at fracture moment — used to compensate
     *  for actor's continued motion after fracture so the body's
     *  world coordinates remain absolute. */
    public double initialActorX;
    public double initialActorY;
    public double initialActorZ;

    /** AABB half-extents for ground-collision testing. Computed from
     *  the bone's local vertex bounds at fracture time. */
    public final Vector3f halfExtents = new Vector3f(0.3F, 0.3F, 0.3F);

    /** Tick at which this body was spawned — used for lifetime cap. */
    public final int spawnTick;
    /** Lifetime cap in ticks (-1 = no cap). */
    public final int lifetimeTicks;

    /* Per-body physics tuning, copied from the spawning clip's fields.
     *  Defaults match the simulator's old static values. */
    public float gravity        = -0.04F;
    public float linearDamping  = 0.99F;
    public float angularDamping = 0.97F;
    public float groundBounce   = -0.35F;

    /** Per-body scale jitter. Multiplier applied uniformly to vertices
     *  so each piece looks slightly different even when many bones
     *  share similar shape. 1.0 = original size; range typically
     *  0.85..1.15. Applied as a scale matrix in the override.
     *  Set per-body at spawn time with a small random delta and then
     *  ramped down to 0 over the last {@link #FADE_OUT_TICKS} ticks
     *  of lifetime for a smooth despawn instead of a hard pop. */
    public float visualScale = 1F;
    /** Spawn-time scale snapshot — restored each tick BEFORE fade
     *  multiplier so fade is computed on the constant base, not
     *  compounding tick-over-tick. */
    public float baseScale = 1F;
    /** Length of the lifetime fade-out tail in ticks. */
    public static final int FADE_OUT_TICKS = 20;

    /** First-impact sound flag — set to true after the body hits the
     *  ground for the first time, so we don't spam the player with
     *  a sound every tick once the body settles. */
    public boolean playedFirstImpactSound;

    /* Per-body particle / sound toggles, copied from the spawning
     *  clip's fields. Defaults are all-on; the clip can disable
     *  individual effects for performance or stylistic reasons. */
    public boolean enableImpactDust  = true;
    public boolean enableImpactSound = true;
    public boolean enableSmokeTrail  = true;
    public boolean enableCameraShake;
    public boolean enableBlockShatter;
    public int blockShatterCount = 6;
    public boolean enablePropagation;
    public int propagationRadius = 5;
    public int propagationSpeed = 4;
    public boolean destroyBlocks;

    /** Whether the body has settled (very low velocity for many ticks)
     *  so we can stop integrating it. Still rendered as final pose. */
    public boolean settled;
    public int settledCounter;

    /** Ticks elapsed since spawn — incremented each tick by the
     *  simulator. Used for the lifetime fade-out tail (when
     *  ticksAlive >= lifetimeTicks - FADE_OUT_TICKS, scale ramps
     *  linearly from baseScale to 0 for a smooth despawn). */
    public int ticksAlive;

    /** Marked dead by the simulator/registry — skipped on next tick
     *  AND on next render-override pass. */
    public boolean dead;

    /* === Joint-constraint (ragdoll) state === */

    /** Parent bone's index in the armature, or -1 if this body has no
     *  parent. Used by the joint solver pass in BonePhysicsSimulator
     *  to fetch the parent's body and apply the distance constraint.
     *  -1 = root bone; constraint is skipped. */
    public int parentBoneIndex = -1;

    /** Target distance from this body's position to its parent's
     *  position — captured at fracture time from the bind-pose
     *  geometry. Constraint solver pulls/pushes the two bones along
     *  the connecting vector to keep this distance. */
    public float bindDistanceToParent;

    /** Stiffness multiplier applied per constraint iteration. 0 = no
     *  constraint (current fracture behavior); 1 = rigid (full
     *  correction every tick). 0.3-0.5 = soft ragdoll feel. Copied
     *  from the spawning clip's jointStiffness slider. */
    public float jointStiffness;

    /** Time-scale for this body's physics integration. <1.0 = slow-mo,
     *  1.0 = real time, >1.0 = fast-forward. Implemented as a tick-rate
     *  multiplier in BonePhysicsSimulator: per client-tick we add this
     *  to {@link #tickAccumulator} and run a tickBody whenever the
     *  accumulator crosses 1.0. */
    public float slowMotion = 1F;

    /** Sub-tick accumulator for slow-motion. Reset to 0 at spawn. */
    public float tickAccumulator;

    /** True while the user is grabbing this body with the gravity-gun
     *  feature. While set, the simulator skips its physics tick — the
     *  GravityGunHandler is responsible for setting position each
     *  client tick. Cleared on release/throw. */
    public boolean gravityGunHeld;

    public BonePhysicsBody(IEntity actor, int boneIndex, String boneName,
                           int spawnTick, int lifetimeTicks)
    {
        this.actor = actor;
        this.boneIndex = boneIndex;
        this.boneName = boneName;
        this.spawnTick = spawnTick;
        this.lifetimeTicks = lifetimeTicks;
    }

    /**
     * Compute the matrix override for this bone. Goal: bone's vertices
     * — which are stored in MODEL-LOCAL coords near the bone's
     * {@link #bindPivot} — should render at {@code body.world +
     * body.rotation * (vert - bindPivot)} so the bone keeps its shape,
     * pivots around its own natural centre, and follows the simulated
     * body in world space.
     *
     * <h2>Math derivation</h2>
     * <p>BBS does {@code vertWorld = T(actorNow) * armatures[i] * vertLocal}.</p>
     *
     * <p>We want: {@code vertWorld = bodyWorld + R * (vertLocal - bindPivot)}.</p>
     *
     * <p>Solving for armatures[i] = M_override:</p>
     * <pre>
     *   T(actorNow) * M * vertLocal = body + R*(vertLocal - bindPivot)
     *   M * vertLocal = T(actorNow)^-1 * (body + R*(vertLocal - bindPivot))
     *                 = (body - actorNow) + R*(vertLocal - bindPivot)
     *
     *   Build M as: T(body - actorNow - R*bindPivot) * R
     *      verify:  M * vertLocal = T(body - actorNow - R*bindPivot) * R * vertLocal
     *                             = R*vertLocal + (body - actorNow - R*bindPivot)
     *                             = (body - actorNow) + R*(vertLocal - bindPivot) ✓
     * </pre>
     */
    public Matrix4f computeOverrideMatrix(double actorNowWorldX,
                                          double actorNowWorldY,
                                          double actorNowWorldZ,
                                          Matrix4f out)
    {
        return this.computeOverrideMatrixInterpolated(
            actorNowWorldX, actorNowWorldY, actorNowWorldZ, 1F, out);
    }

    /**
     * Render-time interpolated override matrix. Pass {@code tickDelta}
     * in [0..1] (where 0 = previous tick state, 1 = current tick state)
     * so the bone moves smoothly at high render fps even though the
     * simulator only ticks at 20 Hz.
     *
     * <p>Note: uses {@code Quaternionf.slerp} for rotation interp —
     * better than nlerp for large rotation deltas, indistinguishable
     * for small per-tick deltas.</p>
     */
    /* Reusable scratch values to avoid per-frame allocations of
     *  Quaternionf/Vector3f. Each body has its own so the simulator's
     *  per-actor iteration doesn't race on shared state. */
    private final Quaternionf scratchRot = new Quaternionf();
    private final Vector3f scratchPivot = new Vector3f();

    public Matrix4f computeOverrideMatrixInterpolated(double actorNowWorldX,
                                                      double actorNowWorldY,
                                                      double actorNowWorldZ,
                                                      float tickDelta,
                                                      Matrix4f out)
    {
        /* Lerp position from prev → current. */
        float interpX = this.prevPosition.x + (this.position.x - this.prevPosition.x) * tickDelta;
        float interpY = this.prevPosition.y + (this.position.y - this.prevPosition.y) * tickDelta;
        float interpZ = this.prevPosition.z + (this.position.z - this.prevPosition.z) * tickDelta;

        /* Slerp rotation into scratch (avoids per-frame allocation). */
        this.scratchRot.set(this.prevRotation).slerp(this.rotation, tickDelta);

        /* Compute R * (animatedPivot * scale). animatedPivot is the
         *  bone's CURRENT pivot at fracture time — for a posed actor
         *  this differs from bindPivot. Using animatedPivot keeps the
         *  bone visually anchored at its current pose at fracture
         *  moment, then the body's rigid motion pivots around that
         *  same point. Scale applied here so apparent pivot stays
         *  centred even at non-1.0 scale. */
        this.scratchPivot.set(this.animatedPivot).mul(this.visualScale);
        this.scratchRot.transform(this.scratchPivot);
        Vector3f rotatedPivot = this.scratchPivot;
        Quaternionf interpRot = this.scratchRot;

        /* Override math:
         *   M = T(body - actor - R*animatedPivot) * R * S * initialBoneMatrix
         *
         *  Verify at t=0 with R=identity, body=actor+animatedPivot:
         *    M*vertLocal
         *      = T(animatedPivot - animatedPivot) * I * I * worldMatrix * vertLocal
         *      = worldMatrix * vertLocal
         *   → vertWorld = T(actor) * worldMatrix * vertLocal — exactly
         *     the BBS-rendered animated vertex position at fracture
         *     time. ✓
         *
         *  For idle/bind-pose bones, initialBoneMatrix = identity and
         *  animatedPivot = bindPivot, so this reduces to the original
         *  formula and the locomotive case is unchanged. */
        out.identity();
        out.translate(
            (float) (interpX - actorNowWorldX) - rotatedPivot.x,
            (float) (interpY - actorNowWorldY) - rotatedPivot.y,
            (float) (interpZ - actorNowWorldZ) - rotatedPivot.z);
        out.rotate(interpRot);
        if (this.visualScale != 1F) out.scale(this.visualScale);
        out.mul(this.initialBoneMatrix);
        return out;
    }

}
