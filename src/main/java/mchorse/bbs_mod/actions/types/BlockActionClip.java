package mchorse.bbs_mod.actions.types;

import mchorse.bbs_mod.actions.SuperFakePlayer;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.settings.values.ValueBlockState;
import mchorse.bbs_mod.settings.values.ValueInt;
import mchorse.bbs_mod.utils.clips.Clip;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;

public class BlockActionClip extends ActionClip
{
    public final ValueInt x = new ValueInt("x", 0);
    public final ValueInt y = new ValueInt("y", 0);
    public final ValueInt z = new ValueInt("z", 0);
    public final ValueBlockState state = new ValueBlockState("state", Blocks.AIR.getDefaultState());

    public BlockActionClip()
    {
        this.add(this.state);
        this.add(this.x);
        this.add(this.y);
        this.add(this.z);
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
        return new BlockActionClip();
    }
}