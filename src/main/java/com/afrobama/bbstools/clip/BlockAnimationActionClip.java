// Coded by Afrobama/Claude — Block Animation as a BBS Action Clip.
// Replaces the temporary /bbsanim command. User selects blocks via the
// Block Selection Mode (TAB), captures the AABB into this clip, then
// places the clip on the BBS timeline at any tick. Clip parameters:
// region AABB + offset vector + duration. On clip activation during
// replay playback -> captures all blocks in the AABB and animates them
// over duration along the offset vector. Real world blocks remain
// unchanged — animation is a client-side render overlay.
//
// Future iters: per-block keyframe channels (each block's start/end
// position keyframeable independently) via KeyframeChannel.
package com.afrobama.bbstools.clip;

import mchorse.bbs_mod.actions.types.ActionClip;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.clips.Clip;

public class BlockAnimationActionClip extends ActionClip
{
    /* === Region (inclusive AABB) === */
    public final ValueInt minX = new ValueInt("min_x", 0, -30000000, 30000000);
    public final ValueInt minY = new ValueInt("min_y", 0, -64, 320);
    public final ValueInt minZ = new ValueInt("min_z", 0, -30000000, 30000000);
    public final ValueInt maxX = new ValueInt("max_x", 0, -30000000, 30000000);
    public final ValueInt maxY = new ValueInt("max_y", 0, -64, 320);
    public final ValueInt maxZ = new ValueInt("max_z", 0, -30000000, 30000000);

    /** Animation offset in blocks. Each block moves from its start
     *  position to (start + offset) over the clip's animation duration.
     *  Example for gate-fly-up: offsetY = 10, offsetX/Z = 0 → blocks
     *  fly straight up 10 blocks. */
    public final ValueFloat offsetX = new ValueFloat("offset_x", 0F, -100F, 100F);
    public final ValueFloat offsetY = new ValueFloat("offset_y", 10F, -100F, 100F);
    public final ValueFloat offsetZ = new ValueFloat("offset_z", 0F, -100F, 100F);

    /** Animation duration in seconds. 1.5s = quick gate-reveal,
     *  3s = slow cinematic morph. */
    public final ValueFloat animDuration = new ValueFloat("anim_duration", 1.5F, 0.1F, 30F);

    public BlockAnimationActionClip()
    {
        super();
        this.add(this.minX);
        this.add(this.minY);
        this.add(this.minZ);
        this.add(this.maxX);
        this.add(this.maxY);
        this.add(this.maxZ);
        this.add(this.offsetX);
        this.add(this.offsetY);
        this.add(this.offsetZ);
        this.add(this.animDuration);
    }

    @Override
    public boolean isClient() { return true; }

    @Override
    protected Clip create()
    {
        return new BlockAnimationActionClip();
    }

}
