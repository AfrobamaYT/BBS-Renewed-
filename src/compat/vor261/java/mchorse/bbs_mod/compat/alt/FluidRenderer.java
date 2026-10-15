package mchorse.bbs_mod.compat.alt;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import java.util.function.Function;

/**
 * 26.2's FluidRenderer, for the versions before 26.1: the block renderer's renderLiquid (which
 * also honours Fabric's FluidRenderHandlers). 26.2 asks the output per section layer; before
 * 26.1 one consumer takes everything, so the layer argument is null.
 */
public final class FluidRenderer
{
    public FluidRenderer(FluidStateModelSet models)
    {}

    public void tesselate(BlockAndTintGetter world, BlockPos pos, Function<Object, VertexConsumer> output, BlockState state, FluidState fluid)
    {
        Minecraft.getInstance().getBlockRenderer().renderLiquid(pos, world, output.apply(null), state, fluid);
    }
}
