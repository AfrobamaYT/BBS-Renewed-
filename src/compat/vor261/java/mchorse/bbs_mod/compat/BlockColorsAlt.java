package mchorse.bbs_mod.compat;

import mchorse.bbs_mod.compat.alt.BlockTintSource;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 26.2's BlockColors.getTintSource, for the versions before 26.1. 26.2 returns null for an
 * untinted index, BlockColors.getColor before 26.1 returns -1 (white) -- the same result for
 * the callers, which treat both as white. Injected into BlockColors.
 */
public interface BlockColorsAlt
{
    default BlockTintSource getTintSource(BlockState state, int tintIndex)
    {
        BlockColors colors = (BlockColors) this;

        return (s, world, pos) -> colors.getColor(s, world, pos, tintIndex);
    }
}
