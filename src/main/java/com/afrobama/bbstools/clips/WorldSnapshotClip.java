// Coded by Afrobama/Claude — WorldSnapshotClip scaffold (2026-05-16).
// Restores a previously saved snapshot of block states inside an AABB
// region at the moment this clip activates on the BBS timeline.
//
// Use case: cinematic shots where the world needs to "reset" between
// takes — e.g. a building gets destroyed in shot A, then shot B starts
// with the building intact again because a WorldSnapshotClip at tick=0
// re-applies the saved pre-destruction snapshot.
//
// Scaffolding scope (this commit):
//   * Class extends ActionClip — the proven base for side-effect clips
//     in this codebase (BlockAnimationActionClip, ModelFractureActionClip,
//     PhysicsBlockDestructionActionClip all extend ActionClip too).
//   * Value fields declared so the clip serialises + shows up in the
//     BBS editor sidebar.
//   * applyClientAction(IEntity, Film, Replay, int) overridden as a
//     log-only stub. Real snapshot-restore logic (load .nbt → iterate
//     positions → world.setBlockState) lands in a follow-up iteration,
//     dispatched server-side via a packet because client setBlockState
//     calls don't persist on dedicated servers.
//
// API notes captured during scaffolding:
//   * A3's research called for `applyClip(ClipContext, Position)` —
//     that's the CameraClip override. ActionClip uses applyClientAction
//     with (IEntity, Film, Replay, int tick). Verified against
//     ModelFractureClientClip and BlockAnimationClientClip in this repo.
//   * A3 also suggested a `ValueBlockPos` type. That class does NOT
//     exist in mchorse.bbs_mod.settings.values.*. The proven idiom in
//     BlockAnimationActionClip is three ValueInts per BlockPos — we
//     follow that here to stay portable across BBS-CML and BBS-Refreshed.
//   * isClient() == true: matches every other ActionClip subclass in
//     this codebase. Server-side world mutation will be dispatched via
//     a BBSTools packet from the client side (planned follow-up).
package com.afrobama.bbstools.clips;

import mchorse.bbs_mod.actions.types.ActionClip;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.clips.Clip;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorldSnapshotClip extends ActionClip
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/snapshot-clip");

    /* === Region (inclusive AABB), stored as 3 ValueInts per corner.
     *     Matches the codebase convention from BlockAnimationActionClip. */
    public final ValueInt minX = new ValueInt("min_x", 0, -30000000, 30000000);
    public final ValueInt minY = new ValueInt("min_y", 0, -64, 320);
    public final ValueInt minZ = new ValueInt("min_z", 0, -30000000, 30000000);
    public final ValueInt maxX = new ValueInt("max_x", 0, -30000000, 30000000);
    public final ValueInt maxY = new ValueInt("max_y", 0, -64, 320);
    public final ValueInt maxZ = new ValueInt("max_z", 0, -30000000, 30000000);

    /** Filename of the saved snapshot inside
     *  &lt;world&gt;/bbstools/snapshots/&lt;snapshotFile&gt;.nbt. The captured
     *  format is up to the snapshot-capture command (planned: NBT
     *  blob of BlockState[] keyed by relative position to minX/Y/Z). */
    public final ValueString snapshotFile = new ValueString("snapshot_file", "");

    /** Whether to also restore block entities (chests with items,
     *  signs with text, etc.) — slower but needed for narrative-heavy
     *  shots. Default off so simple terrain-reset shots are fast. */
    public final ValueBoolean restoreBlockEntities = new ValueBoolean("restore_block_entities", false);

    /** When true, only blocks that differ from the snapshot get
     *  rewritten — saves per-tick chunk-update cost when 90 % of the
     *  region is already correct. */
    public final ValueBoolean diffOnly = new ValueBoolean("diff_only", true);

    /* Throttle bookkeeping — same pattern as BlockAnimationClientClip.
     * Prevents scrub-storm where the user drags the timeline cursor
     * across the clip's start tick repeatedly and we fire restore
     * dozens of times per second. */
    private long lastFireWallclockMs;
    private int lastFireTick = Integer.MIN_VALUE;

    public WorldSnapshotClip()
    {
        super();

        this.add(this.minX);
        this.add(this.minY);
        this.add(this.minZ);
        this.add(this.maxX);
        this.add(this.maxY);
        this.add(this.maxZ);
        this.add(this.snapshotFile);
        this.add(this.restoreBlockEntities);
        this.add(this.diffOnly);
    }

    /** Client-driven: matches every other ActionClip subclass in this
     *  codebase. The actual world mutation gets dispatched server-side
     *  via a BBSTools packet from inside applyClientAction (follow-up
     *  iteration — for now we just log). */
    @Override
    public boolean isClient()
    {
        return true;
    }

    @Override
    protected void applyClientAction(IEntity entity, Film film, Replay replay, int tick)
    {
        long now = System.currentTimeMillis();
        if (tick == this.lastFireTick && (now - this.lastFireWallclockMs) < 500L)
        {
            return;
        }
        this.lastFireTick = tick;
        this.lastFireWallclockMs = now;

        try
        {
            int x0 = Math.min(this.minX.get(), this.maxX.get());
            int y0 = Math.min(this.minY.get(), this.maxY.get());
            int z0 = Math.min(this.minZ.get(), this.maxZ.get());
            int x1 = Math.max(this.minX.get(), this.maxX.get());
            int y1 = Math.max(this.minY.get(), this.maxY.get());
            int z1 = Math.max(this.minZ.get(), this.maxZ.get());

            String file = this.snapshotFile.get();
            if (file == null || file.isEmpty())
            {
                LOG.warn("WorldSnapshotClip fired at tick {} but snapshot_file is empty — set it via the sidebar.", tick);
                return;
            }

            if (x0 == x1 && y0 == y1 && z0 == z1)
            {
                LOG.warn("WorldSnapshotClip fired at tick {} but region is empty (single block at {},{},{}) — capture an AABB first.",
                    tick, x0, y0, z0);
                return;
            }

            LOG.info(
                "WorldSnapshotClip fired tick={} region=[{},{},{}..{},{},{}] file='{}' diffOnly={} restoreBE={} (RESTORE LOGIC PENDING — scaffold only)",
                tick,
                x0, y0, z0, x1, y1, z1,
                file,
                this.diffOnly.get(),
                this.restoreBlockEntities.get()
            );

            /* TODO(next iter):
             *   1. Resolve `file` against <world>/bbstools/snapshots/.
             *   2. Read .nbt blob -> Map<BlockPos, BlockState>.
             *   3. Send WorldSnapshotRestorePacket(x0..z1, file, diffOnly,
             *      restoreBE) to server.
             *   4. Server reads snapshot, world.setBlockState() per pos
             *      with NOTIFY|NO_RERENDER flags. If restoreBE, also
             *      writes captured BlockEntity NBT.
             */
        }
        catch (Throwable t)
        {
            /* Log-first rule: never swallow silently. */
            LOG.warn("WorldSnapshotClip apply failed at tick {}: {}", tick, t.toString(), t);
        }
    }

    @Override
    protected Clip create()
    {
        return new WorldSnapshotClip();
    }

}
