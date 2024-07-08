package mchorse.bbs_mod.actions.types.blocks;

import mchorse.bbs_mod.actions.SuperFakePlayer;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.settings.values.ValueBlockState;
import mchorse.bbs_mod.utils.clips.Clip;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;

public class PlaceBlockActionClip extends BlockActionClip
{
    public final ValueBlockState state = new ValueBlockState("state");

    public PlaceBlockActionClip()
    {
        this.add(this.state);
    }

    @Override
    public void apply(SuperFakePlayer player, Film film, Replay replay, int tick)
    {
        BlockPos pos = new BlockPos(this.x.get(), this.y.get(), this.z.get());

        if (this.state.get().getBlock() == Blocks.AIR)
        {
            player.getWorld().breakBlock(pos, true);
        }
        else
        {
            player.getWorld().setBlockState(pos, this.state.get());
        }
    }

    @Override
    protected Clip create()
    {
        return new PlaceBlockActionClip();
    }
}