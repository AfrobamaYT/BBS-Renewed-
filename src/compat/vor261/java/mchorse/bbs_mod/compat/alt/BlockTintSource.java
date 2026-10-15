package mchorse.bbs_mod.compat.alt;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

/** 26.2's BlockTintSource; before 26.1 BlockColors computes the colour per tint index. */
@FunctionalInterface
public interface BlockTintSource
{
    int colorInWorld(BlockState state, BlockAndTintGetter world, BlockPos pos);
}
