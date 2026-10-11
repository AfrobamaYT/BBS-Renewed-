// Coded by Afrobama/Claude — Client subclass of BlockAnimationActionClip.
// Replaces the temporary /bbsanim command with a proper BBS clip.
// On clip activation at its start tick during replay playback:
// captures all non-air blocks in the clip's AABB region and animates
// them along (offsetX, offsetY, offsetZ) over animDuration seconds.
//
// Throttled to prevent scrub-storms: skips re-fire within 500ms of
// the previous fire at the same tick (same pattern as
// PhysicsBlockDestructionClientClip).
package com.afrobama.bbstools.client.clip;

import com.afrobama.bbstools.client.blockanim.BlockAnimation;
import com.afrobama.bbstools.clip.BlockAnimationActionClip;

import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.utils.clips.Clip;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlockAnimationClientClip extends BlockAnimationActionClip
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/clip/blockanim");

    @Override
    protected void applyClientAction(IEntity entity, Film film, Replay replay, int tick)
    {
        BlockAnimation.prepareClip(this);
    }

    @Override
    protected Clip create()
    {
        return new BlockAnimationClientClip();
    }

}
