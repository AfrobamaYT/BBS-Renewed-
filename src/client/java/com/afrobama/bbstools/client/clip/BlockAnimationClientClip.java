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
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlockAnimationClientClip extends BlockAnimationActionClip
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/clip/blockanim");

    private long lastFireWallclockMs;
    private int lastFireTick = Integer.MIN_VALUE;

    @Override
    protected void applyClientAction(IEntity entity, Film film, Replay replay, int tick)
    {
        long now = System.currentTimeMillis();
        if (tick == this.lastFireTick && (now - this.lastFireWallclockMs) < 500L) return;
        this.lastFireTick = tick;
        this.lastFireWallclockMs = now;

        int x0 = Math.min(this.minX.get(), this.maxX.get());
        int y0 = Math.min(this.minY.get(), this.maxY.get());
        int z0 = Math.min(this.minZ.get(), this.maxZ.get());
        int x1 = Math.max(this.minX.get(), this.maxX.get());
        int y1 = Math.max(this.minY.get(), this.maxY.get());
        int z1 = Math.max(this.minZ.get(), this.maxZ.get());

        try
        {
            BlockAnimation.captureAndAnimateRegion(
                new BlockPos(x0, y0, z0),
                new BlockPos(x1, y1, z1),
                new Vec3(this.offsetX.get(), this.offsetY.get(), this.offsetZ.get()),
                this.animDuration.get());
        }
        catch (IllegalArgumentException failure)
        {
            String message = "Block Animation failed: " + failure.getMessage();
            Minecraft client = Minecraft.getInstance();
            if (client.player != null) client.player.sendSystemMessage(Component.literal(message));
            LOG.warn("{} (tick {})", message, tick);
            return;
        }

        LOG.info("BlockAnimationClip fired tick={} region ({},{},{})->({},{},{}) offset=({},{},{}) duration={}s",
            tick, x0, y0, z0, x1, y1, z1, this.offsetX.get(), this.offsetY.get(), this.offsetZ.get(), this.animDuration.get());
    }

    @Override
    protected Clip create()
    {
        return new BlockAnimationClientClip();
    }

}
