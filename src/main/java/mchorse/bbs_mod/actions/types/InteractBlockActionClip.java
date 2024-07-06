package mchorse.bbs_mod.actions.types;

import mchorse.bbs_mod.actions.SuperFakePlayer;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.settings.values.ValueBoolean;
import mchorse.bbs_mod.settings.values.ValueDouble;
import mchorse.bbs_mod.settings.values.ValueInt;
import mchorse.bbs_mod.utils.EnumUtils;
import mchorse.bbs_mod.utils.clips.Clip;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class InteractBlockActionClip extends BlockActionClip
{
    public final ValueDouble hitX = new ValueDouble("hitX", 0D);
    public final ValueDouble hitY = new ValueDouble("hitY", 0D);
    public final ValueDouble hitZ = new ValueDouble("hitZ", 0D);
    public final ValueBoolean hand = new ValueBoolean("hand", true);
    public final ValueInt direction = new ValueInt("direction", 0);
    public final ValueBoolean inside = new ValueBoolean("inside", false);

    public InteractBlockActionClip()
    {
        this.add(this.hand);
        this.add(this.hitX);
        this.add(this.hitY);
        this.add(this.hitZ);
        this.add(this.direction);
        this.add(this.inside);
    }

    @Override
    public void apply(SuperFakePlayer player, Film film, Replay replay, int tick)
    {
        BlockPos pos = new BlockPos(this.x.get(), this.y.get(), this.z.get());
        Vec3d vec = new Vec3d(this.hitX.get(), this.hitY.get(), this.hitZ.get());
        BlockHitResult result = new BlockHitResult(vec, EnumUtils.getValue(this.direction.get(), Direction.values(), Direction.UP), pos, this.inside.get());

        player.getWorld().getBlockState(pos).onUse(player.getWorld(), player, this.hand.get() ? Hand.MAIN_HAND : Hand.OFF_HAND, result);
    }

    @Override
    protected Clip create()
    {
        return new InteractBlockActionClip();
    }
}