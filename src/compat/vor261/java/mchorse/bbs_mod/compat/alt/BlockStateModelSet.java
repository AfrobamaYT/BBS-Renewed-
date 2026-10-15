package mchorse.bbs_mod.compat.alt;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.world.level.block.state.BlockState;

/** 26.2's BlockStateModelSet lookup; before 26.1 the block renderer's model shaper answers it. */
public final class BlockStateModelSet
{
    public BlockStateModel get(BlockState state)
    {
        return Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
    }

    /** 26.2's particle material of a block state; before 26.1 the model shaper's particle icon. */
    public MaterialBaked getParticleMaterial(BlockState state)
    {
        return new MaterialBaked(Minecraft.getInstance().getBlockRenderer().getBlockModelShaper().getParticleIcon(state));
    }
}
