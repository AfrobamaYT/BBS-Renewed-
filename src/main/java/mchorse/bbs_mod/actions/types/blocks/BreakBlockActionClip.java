package mchorse.bbs_mod.actions.types.blocks;

import mchorse.bbs_mod.forms.entities.IEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.component.SwingAnimation;
import mchorse.bbs_mod.actions.SuperFakePlayer;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.clips.Clip;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;

public class BreakBlockActionClip extends BlockActionClip
{
    public final ValueInt progress = new ValueInt("progress", 0);

    public BreakBlockActionClip()
    {
        super();

        this.add(this.progress);
    }

    /* Minecraft 26.x swings only client-side while a block keeps being mined and sends a punch
     * packet instead, so the recorder (which reads the server player's swing) caught the first
     * blow and nothing after: the hand stood still while a tree came down (owner, 2026-10-14;
     * in his film 'test' 71 break stages against 11 swipes). Every recorded break stage is a
     * blow, about one per swing, so playing it back swings too - recordings already made
     * included. Progress -1 is the end of the break, not a blow. */
    @Override
    public boolean isClient()
    {
        return true;
    }

    @Override
    protected void applyClientAction(IEntity entity, Film film, Replay replay, int tick)
    {
        if (this.progress.get() >= 0)
        {
            entity.swingArm();
        }
    }

    @Override
    public void applyAction(LivingEntity actor, SuperFakePlayer player, Film film, Replay replay, int tick)
    {
        player.level().destroyBlockProgress(player.getId(), new BlockPos(this.x.get(), this.y.get(), this.z.get()), this.progress.get());

        if (actor != null && this.progress.get() >= 0)
        {
            actor.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
        }
    }

    @Override
    protected Clip create()
    {
        return new BreakBlockActionClip();
    }
}