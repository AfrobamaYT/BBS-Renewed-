package com.afrobama.bbstools.fracture;

import mchorse.bbs_mod.actions.types.ActionClip;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.clips.Clip;

/**
 * Action clip that triggers physics-based fracture of a BBS BOBJ-modelled
 * actor at a specific film tick. Each bone of the actor's model becomes
 * an independent PhysicsMod-Pro convex rigidbody with the bone's mesh.
 *
 * <p>Server-side companion class — the data fields, serialisation, and
 * factory live here. The actual fracture spawning lives in
 * {@code com.afrobama.bbstools.client.fracture.ModelFractureClientClip}
 * which extends this class and overrides {@code applyClientAction()}.
 * Splitting like this matches the pattern of all other clip types in BBS
 * (e.g. {@code TrackerClip} server / {@code TrackerClientClip} client).</p>
 *
 * <p><b>Fields:</b></p>
 * <ul>
 *   <li>{@code groupName} — fracture-group name from the model's
 *       {@code fracture_groups.json} (e.g. "front_cabin", "wagon_1").
 *       Empty string = fracture every bone of the model. (Phase 3 feature)</li>
 *   <li>{@code triggerMode} — when this clip activates fracture:
 *     <ul>
 *       <li>{@code "manual"} (default): fracture fires at the clip's start tick</li>
 *       <li>{@code "on_collision"}: starting at the clip's start tick a per-tick
 *           AABB watcher runs; bones break individually as they collide with
 *           world blocks (Phase 6 feature)</li>
 *       <li>{@code "on_velocity_drop"}: fracture fires when actor velocity
 *           falls > velocityDropThreshold in one tick (Phase 6 feature)</li>
 *     </ul>
 *   </li>
 *   <li>{@code forceJitter} — random force magnitude (0..2) added to each
 *       fragment's initial velocity to make the fracture look chaotic
 *       rather than uniformly outward</li>
 *   <li>{@code inheritVelocity} — when true, fragments inherit the actor's
 *       linear velocity at the trigger frame. Off = pieces fall straight down.</li>
 *   <li>{@code lifetime} — seconds the physics fragments stay alive before
 *       auto-despawn. Limits memory + perf for long films.</li>
 *   <li>{@code velocityDropThreshold} — only relevant for
 *       {@code "on_velocity_drop"} mode. Magnitude of velocity decrease per
 *       tick required to trigger fracture (default 0.5 blocks/tick).</li>
 * </ul>
 */
public class ModelFractureActionClip extends ActionClip
{
    public static final String TRIGGER_MANUAL = "manual";
    public static final String TRIGGER_ON_COLLISION = "on_collision";
    public static final String TRIGGER_ON_VELOCITY_DROP = "on_velocity_drop";

    public final ValueString groupName = new ValueString("group_name", "");
    public final ValueString triggerMode = new ValueString("trigger_mode", TRIGGER_MANUAL);
    public final ValueFloat forceJitter = new ValueFloat("force_jitter", 0.4F, 0F, 2F);
    public final ValueBoolean inheritVelocity = new ValueBoolean("inherit_velocity", true);
    public final ValueInt lifetime = new ValueInt("lifetime", 10, 1, 120);
    public final ValueFloat velocityDropThreshold = new ValueFloat("velocity_drop_threshold", 0.5F, 0.05F, 5F);

    /* Phase-B tunable physics (per-clip override). */
    public final ValueFloat gravity = new ValueFloat("gravity", 0.04F, 0F, 0.5F);
    public final ValueFloat linearDamping = new ValueFloat("linear_damping", 0.99F, 0.5F, 1F);
    public final ValueFloat angularDamping = new ValueFloat("angular_damping", 0.97F, 0.5F, 1F);
    public final ValueFloat groundBounce = new ValueFloat("ground_bounce", 0.35F, 0F, 1F);
    public final ValueFloat angularJitter = new ValueFloat("angular_jitter", 0.15F, 0F, 1F);

    /* Particle / sound effects toggles. */
    public final ValueBoolean enableImpactDust  = new ValueBoolean("enable_impact_dust", true);
    public final ValueBoolean enableImpactSound = new ValueBoolean("enable_impact_sound", true);
    public final ValueBoolean enableSmokeTrail  = new ValueBoolean("enable_smoke_trail", true);

    /* Directional impulse: a velocity vector ADDED to every fragment's
     *  initial velocity. Use this to make fracture pieces fly in a
     *  specific direction, like an explosion centred ahead of the
     *  actor or a battering-ram style force from one side. */
    public final ValueFloat impulseX = new ValueFloat("impulse_x", 0F, -10F, 10F);
    public final ValueFloat impulseY = new ValueFloat("impulse_y", 0F, -10F, 10F);
    public final ValueFloat impulseZ = new ValueFloat("impulse_z", 0F, -10F, 10F);

    /* Velocity multiplier: scales the inherited actor velocity so
     *  pieces can fly OUT faster (>1) or slower (<1) than the actor.
     *  e.g. 2.0 = pieces ricochet at twice actor speed = "explosion".
     *  e.g. 0.3 = pieces drop without much horizontal motion. */
    public final ValueFloat velocityMultiplier = new ValueFloat("velocity_multiplier", 1F, 0F, 5F);

    /* Piece count multiplier — how many extra flying debris chunks to
     *  spawn PER bone alongside the main bone-body. 1 = only the bone-
     *  body (current behavior, 52 pieces for a 52-bone locomotive).
     *  8 = bone-body + 7 extra debris chunks per bone (52*8 = 416
     *  total flying things). The extras are PhysicsMod-rendered convex
     *  chunks built from the bone's own vertices subsetted into K
     *  Voronoi-like clusters so they have a similar shape silhouette
     *  to the original bone — they look like "the bone shattering
     *  into K bits" rather than random stone cubes. */
    public final ValueInt pieceCount = new ValueInt("piece_count", 1, 1, 30);

    /* When pieceCount > 1, optional camera shake on heavy impacts.
     *  Off by default to avoid surprising users; enabling adds organic
     *  pitch/yaw perturbation when bone bodies hit the ground hard. */
    public final ValueBoolean enableCameraShake = new ValueBoolean("enable_camera_shake", false);

    /* Ragdoll mode — joint-constrained fracture. When jointStiffness
     *  > 0 the simulator pulls each fractured bone toward its
     *  bind-pose parent, keeping the bones connected like a
     *  ragdoll. Texture + body parts are preserved (no proxy entity)
     *  because we keep using the BBS BOBJ render path with bone
     *  matrix overrides — the only difference vs. fracture is the
     *  joint constraint solver pulling siblings together each tick.
     *
     *  Values:
     *    0.00 = current behavior (bones fly apart freely)
     *    0.30 = soft ragdoll (joints stretchy, pieces drift apart)
     *    0.60 = standard ragdoll (joints pull together visibly)
     *    1.00 = rigid (bones lock to bind-pose distance)
     *
     *  For a humanoid character actor falling over, use 0.5-0.8.
     *  For a vehicle disintegrating into chunks, use 0.0. */
    public final ValueFloat jointStiffness = new ValueFloat("joint_stiffness", 0F, 0F, 1F);

    /* Slow-motion playback for the fracture pieces. Below 1.0 the
     *  physics integrate every Nth client-tick so debris falls
     *  visibly slower — perfect for the bullet-time crash shot every
     *  cinematic crash needs. Above 1.0 the integrator runs multiple
     *  steps per tick so action speeds up.
     *
     *  Recommended:
     *    1.00 = real time (default)
     *    0.50 = slow-mo (half speed)
     *    0.25 = bullet-time
     *    0.10 = extreme bullet-time (good for the moment-of-impact
     *           freeze frame)
     *
     *  Note: this scales ONLY the bone-physics integration. The
     *  film camera's render tick stays unchanged, so the slow-mo
     *  is exclusively on the fractured pieces — a slow-motion
     *  Matrix-bullet effect with the rest of the world at normal
     *  speed. */
    public final ValueFloat slowMotion = new ValueFloat("slow_motion", 1F, 0.05F, 3F);

    /* Block-shatter on impact: when a fracture body hits a non-air
     *  block hard enough, spawn PhysicsMod debris particles that look
     *  like chunks of THAT block's material — stone wall produces
     *  stone chunks, oak beam produces oak chunks. The block itself
     *  stays put (BBS cinematic doesn't permanently modify the world)
     *  but visually it LOOKS shattered. */
    public final ValueBoolean enableBlockShatter = new ValueBoolean("enable_block_shatter", false);

    /* How many debris chunks to spawn per block-impact event. Each
     *  chunk inherits a random subset of the impact velocity, scaled
     *  by impact speed, so a hard hit throws more violent debris. */
    public final ValueInt blockShatterCount = new ValueInt("block_shatter_count", 6, 0, 30);

    /* Impact-point propagation: when a fracture body hits a block hard
     *  enough AND propagation is on, trigger a radial wave from the
     *  impact point that fragments surrounding blocks tick-by-tick.
     *  Cinematic effect: villager thrown into tree → impact point
     *  cracks first, then surrounding wood splinters outward.
     *  Disabled by default because the per-tick block scan adds a
     *  small CPU cost. */
    public final ValueBoolean enablePropagation = new ValueBoolean("enable_propagation", false);

    /** Max manhattan radius (in blocks) the wave can reach from the
     *  impact origin. Hard cap also enforced by MAX_LIFETIME_TICKS. */
    public final ValueInt propagationRadius = new ValueInt("propagation_radius", 5, 1, 20);

    /** How many blocks per tick the BFS frontier processes. Higher =
     *  faster wave (instant-ish), lower = slower cascade. */
    public final ValueInt propagationSpeed = new ValueInt("propagation_speed", 4, 1, 30);

    /* Actually destroy blocks during cinematic playback (set to air),
     *  with original BlockState snapshotted for restoration on rewind
     *  / fracture-clear / world unload. Singleplayer-only effective —
     *  multiplayer servers revert client-side block edits.
     *
     *  Use case: villager thrown into tree should ACTUALLY split the
     *  tree apart, not just spawn particles on intact blocks. */
    public final ValueBoolean destroyBlocks = new ValueBoolean("destroy_blocks", false);

    public ModelFractureActionClip()
    {
        super();

        this.add(this.groupName);
        this.add(this.triggerMode);
        this.add(this.forceJitter);
        this.add(this.inheritVelocity);
        this.add(this.lifetime);
        this.add(this.velocityDropThreshold);
        this.add(this.gravity);
        this.add(this.linearDamping);
        this.add(this.angularDamping);
        this.add(this.groundBounce);
        this.add(this.angularJitter);
        this.add(this.enableImpactDust);
        this.add(this.enableImpactSound);
        this.add(this.enableSmokeTrail);
        this.add(this.impulseX);
        this.add(this.impulseY);
        this.add(this.impulseZ);
        this.add(this.velocityMultiplier);
        this.add(this.pieceCount);
        this.add(this.enableCameraShake);
        this.add(this.jointStiffness);
        this.add(this.slowMotion);
        this.add(this.enableBlockShatter);
        this.add(this.blockShatterCount);
        this.add(this.enablePropagation);
        this.add(this.propagationRadius);
        this.add(this.propagationSpeed);
        this.add(this.destroyBlocks);
    }

    @Override
    public boolean isClient()
    {
        /* Pure visual effect — runs only client-side. The server doesn't
         *  need to know about physics fragments since they're cosmetic
         *  PhysicsMod entities, not real MC entities. */
        return true;
    }

    @Override
    protected Clip create()
    {
        return new ModelFractureActionClip();
    }

}
