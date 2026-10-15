package mchorse.bbs_mod.compat.alt;

import mchorse.bbs_mod.compat.gpu.GebackenesBlockmodell;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.world.level.block.state.BlockState;

/** 26.2's BlockStateModelSet vor 1.21.5: das BakedModel des Blockrenderers als BlockStateModel. */
public final class BlockStateModelSet
{
    public BlockStateModel get(BlockState state)
    {
        return new GebackenesBlockmodell(Minecraft.getInstance().getBlockRenderer().getBlockModel(state), state);
    }

    public MaterialBaked getParticleMaterial(BlockState state)
    {
        return new MaterialBaked(Minecraft.getInstance().getBlockRenderer().getBlockModelShaper().getParticleIcon(state));
    }
}
