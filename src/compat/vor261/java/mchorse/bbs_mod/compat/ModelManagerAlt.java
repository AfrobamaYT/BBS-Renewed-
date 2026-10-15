package mchorse.bbs_mod.compat;

import mchorse.bbs_mod.compat.alt.BlockStateModelSet;
import mchorse.bbs_mod.compat.alt.FluidStateModelSet;

/** 26.2's model sets on the ModelManager, for the versions before 26.1. Injected into ModelManager. */
public interface ModelManagerAlt
{
    default BlockStateModelSet getBlockStateModelSet()
    {
        return new BlockStateModelSet();
    }

    default FluidStateModelSet getFluidStateModelSet()
    {
        return new FluidStateModelSet();
    }
}
