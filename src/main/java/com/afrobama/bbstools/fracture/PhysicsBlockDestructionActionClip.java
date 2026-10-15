package com.afrobama.bbstools.fracture;

import mchorse.bbs_mod.actions.types.ActionClip;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.clips.Clip;

/**
 * Action clip that destroys a user-marked region of MC blocks via
 * PhysicsMod debris with a configurable impact point. The destruction
 * radiates outward block-by-block from {@code impact*} through the
 * region defined by {@code min*} / {@code max*} corners, leaving any
 * blocks outside the region untouched.
 *
 * <p><b>Use cases</b>:</p>
 * <ul>
 *   <li>Mark a tree's bounding box, set the impact point at the trunk
 *       center → villager flies into trunk → tree splinters from
 *       the impact point outward, leaves and surrounding world
 *       unaffected.</li>
 *   <li>Mark a 20×1×20 ground patch, set impact point under a player
 *       landing spot → ground blocks katapult upward + outward at the
 *       moment the player hits.</li>
 *   <li>Mark a wall section, set impact point at the loco's predicted
 *       crash position → wall fragments from impact, surrounding
 *       structure stays intact.</li>
 * </ul>
 *
 * <p>Region is an inclusive AABB in world coords. Impact point is in
 * world coords too. Both can be edited in the clip's UI panel via
 * "Set from looked-at block" buttons or typed directly.</p>
 *
 * <p>Unlike {@code model_fracture}, this clip is NOT attached to a
 * BBS actor — it fires standalone at its start tick and operates
 * purely on world blocks. The BFS wave processes blocksPerTick blocks
 * per client tick until the wave reaches the region edge or runs out.</p>
 */
public class PhysicsBlockDestructionActionClip extends ActionClip
{
    /* === Region (inclusive AABB) === */
    public final ValueInt minX = new ValueInt("min_x", 0, -30000000, 30000000);
    public final ValueInt minY = new ValueInt("min_y", 0, -64, 320);
    public final ValueInt minZ = new ValueInt("min_z", 0, -30000000, 30000000);
    public final ValueInt maxX = new ValueInt("max_x", 0, -30000000, 30000000);
    public final ValueInt maxY = new ValueInt("max_y", 0, -64, 320);
    public final ValueInt maxZ = new ValueInt("max_z", 0, -30000000, 30000000);

    /* === Impact point (world coords) === */
    public final ValueInt impactX = new ValueInt("impact_x", 0, -30000000, 30000000);
    public final ValueInt impactY = new ValueInt("impact_y", 0, -64, 320);
    public final ValueInt impactZ = new ValueInt("impact_z", 0, -30000000, 30000000);

    /** Outward force scale — how violently blocks fly away from the
     *  impact point. 0 = soft drift, 1 = brisk scatter, 3 = explosive
     *  katapult. */
    public final ValueFloat scatterForce = new ValueFloat("scatter_force", 1F, 0F, 10F);

    /** Vertical kick added to every chunk's velocity. Positive
     *  values lift the explosion upward (good for "ground katapult"
     *  effect). Negative values pull it down (collapse / sink). */
    public final ValueFloat upwardForce = new ValueFloat("upward_force", 0F, -5F, 10F);

    /** Direction bias — defines the AXIS along which the directional
     *  force pushes every chunk. Treat (directionX, Y, Z) as a unit
     *  direction; the actual velocity contribution = normalize(dir)
     *  × {@link #directionForce}. So you set the AXIS here and the
     *  STRENGTH via directionForce — much easier than computing the
     *  vector magnitude manually. */
    public final ValueFloat directionX = new ValueFloat("direction_x", 0F, -10F, 10F);
    public final ValueFloat directionY = new ValueFloat("direction_y", 0F, -10F, 10F);
    public final ValueFloat directionZ = new ValueFloat("direction_z", 0F, -10F, 10F);

    /** Magnitude of the directional force in blocks/tick. Used as a
     *  scalar multiplier on normalize(directionX, Y, Z). 0 = no
     *  directional push (blocks stay radial-only); 5 = strong push;
     *  20 = explosion-tier blast in the chosen direction. */
    public final ValueFloat directionForce = new ValueFloat("direction_force", 0F, 0F, 30F);

    /** Random scatter jitter — magnitude of per-chunk velocity noise
     *  ON TOP of the radial + directional vectors. 0 = perfectly
     *  uniform, 1 = visible randomness, 3 = very chaotic. */
    public final ValueFloat scatterJitter = new ValueFloat("scatter_jitter", 0.3F, 0F, 3F);

    /** Gravity applied to spawned debris. PhysicsMod uses its own
     *  default but cinematic crashes often want LESS gravity for
     *  longer hang time. (Note: this scales the visible debris
     *  particles' tendency to fall; PhysicsMod ultimately controls
     *  the actual physics. We pass it as a downward bias on the
     *  initial velocity for "lighter" feel.) */
    public final ValueFloat gravityBias = new ValueFloat("gravity_bias", 0F, -2F, 2F);

    /** BFS wave expansion rate (blocks per client tick). 1 = slow
     *  cascade, 4 = default, 30 = near-instant burst. */
    public final ValueInt propagationSpeed = new ValueInt("propagation_speed", 4, 1, 30);

    /** Interval (in client ticks) between wave-expansion steps. 1 =
     *  process every tick (default), 5 = process every 5th tick (very
     *  slow cascade good for cinematic), 20 = once per second.
     *  Combine with low {@code propagationSpeed} for super-slow
     *  block-by-block destruction. */
    public final ValueInt tickInterval = new ValueInt("tick_interval", 1, 1, 40);

    /** Maximum manhattan radius the wave can reach from the impact
     *  point. Capped also by the region's bounds. */
    public final ValueInt maxRadius = new ValueInt("max_radius", 30, 1, 100);

    /** PhysicsMod debris chunks spawned per processed block. */
    public final ValueInt chunksPerBlock = new ValueInt("chunks_per_block", 4, 1, 20);

    /** When true, snapshot+remove the world block (real cinematic
     *  destruction). When false, only spawn particles, the block
     *  stays put. Singleplayer-only effective. */
    public final ValueBoolean destroyBlocks = new ValueBoolean("destroy_blocks", true);

    /** Optional: name of an existing replay whose live X/Y/Z keyframes
     *  define the impact point at trigger time, overriding the static
     *  impactX/Y/Z fields. Use case: create a regular replay (any
     *  actor/marker model), animate it to wherever you want the
     *  impact origin to be, paste its name here. The clip reads its
     *  position at the trigger frame.
     *
     *  Empty string = use the static impactX/Y/Z fields instead. */
    public final ValueString impactReplayName = new ValueString("impact_replay_name", "");

    /** When true, render an in-world wireframe overlay of the region
     *  AABB + impact-point cross + direction-arrow so the user can
     *  see exactly where + how blocks will fly before pressing play.
     *  Off by default to keep the editor clean. */
    public final ValueBoolean showWireframe = new ValueBoolean("show_wireframe", false);

    /* Destruction-mode constants — selectable via the editor's cycle
     *  button. Each defines a different ORDERING of block processing. */
    public static final String MODE_RADIAL      = "radial";
    public static final String MODE_DIRECTIONAL = "directional";
    public static final String MODE_TOP_DOWN    = "top_down";
    public static final String MODE_BOTTOM_UP   = "bottom_up";
    public static final String MODE_INSTANT     = "instant";
    public static final String MODE_RANDOM      = "random";

    /** Destruction mode — controls the order in which blocks within
     *  the region are fragmented:
     *  - radial:      BFS wave outward from impact point (default)
     *  - directional: only blocks where the impact-to-block vector
     *                 aligns with (directionX, Y, Z) get hit (cone)
     *  - top_down:    process highest-Y first (collapsing roof)
     *  - bottom_up:   process lowest-Y first (eruption)
     *  - instant:     entire region at once (single-tick burst)
     *  - random:      shuffled order (chaotic disintegration) */
    public final ValueString destructionMode = new ValueString("destruction_mode", MODE_RADIAL);

    /* PhysicsMod block-physics-type override constants. PhysicsMod
     *  defines its own enum BlockPhysicsType: BLOCKY, FRACTURED,
     *  PARTICLES, FRACTURED_VOXEL, OFF, MAIN_RULE. We mirror the
     *  visual ones here. "default" means "use whatever PhysicsMod is
     *  configured for in its own settings" (no override). */
    public static final String BLOCK_MODE_DEFAULT          = "default";
    public static final String BLOCK_MODE_BLOCKY           = "blocky";
    public static final String BLOCK_MODE_FRACTURED        = "fractured";
    public static final String BLOCK_MODE_FRACTURED_VOXEL  = "fractured_voxel";
    public static final String BLOCK_MODE_PARTICLES        = "particles";

    /** Override PhysicsMod's BlockPhysicsType for the duration of this
     *  clip's destruction. {@code default} = don't touch user's
     *  PhysicsMod settings (typical). The other modes temporarily
     *  switch PhysicsMod's global block-physics-type before our
     *  spawn batch so the spawned debris shape matches the user's
     *  cinematic intent (blocky cubes vs realistic shattered chunks
     *  vs voxel chunks vs sparkly particles). */
    public final ValueString blockMode = new ValueString("block_mode", BLOCK_MODE_DEFAULT);

    /** Physics-debris scale multiplier (mirrors PhysicsBlockFractureEmitter's
     *  physicsScale). 0.5 = small chunks, 1.0 = normal block-sized,
     *  2.0 = big chunky debris. */
    public final ValueFloat physicsScale = new ValueFloat("physics_scale", 1F, 0.1F, 5F);

    /** Per-debris lifetime in ticks (20 ticks = 1 second). PhysicsMod
     *  despawns the debris after this many ticks. Default 200 = 10 sec. */
    public final ValueInt debrisLifetime = new ValueInt("debris_lifetime", 200, 20, 1200);

    /** When true, spawned debris won't collide with other debris from
     *  the same destruction wave — they pass through each other but
     *  still hit the world (terrain, walls). Lets force/scatter
     *  reach full magnitude without piles of chunks ramming each
     *  other and braking. Default off for natural pile-up behavior. */
    public final ValueBoolean noInterCollision = new ValueBoolean("no_inter_collision", false);

    public PhysicsBlockDestructionActionClip()
    {
        super();

        this.add(this.minX);
        this.add(this.minY);
        this.add(this.minZ);
        this.add(this.maxX);
        this.add(this.maxY);
        this.add(this.maxZ);
        this.add(this.impactX);
        this.add(this.impactY);
        this.add(this.impactZ);
        this.add(this.scatterForce);
        this.add(this.upwardForce);
        this.add(this.directionX);
        this.add(this.directionY);
        this.add(this.directionZ);
        this.add(this.directionForce);
        this.add(this.scatterJitter);
        this.add(this.gravityBias);
        this.add(this.propagationSpeed);
        this.add(this.maxRadius);
        this.add(this.chunksPerBlock);
        this.add(this.destroyBlocks);
        this.add(this.impactReplayName);
        this.add(this.tickInterval);
        this.add(this.showWireframe);
        this.add(this.destructionMode);
        this.add(this.blockMode);
        this.add(this.physicsScale);
        this.add(this.debrisLifetime);
        this.add(this.noInterCollision);
    }

    @Override
    public boolean isClient()
    {
        return true;
    }

    @Override
    protected Clip create()
    {
        return new PhysicsBlockDestructionActionClip();
    }

}
