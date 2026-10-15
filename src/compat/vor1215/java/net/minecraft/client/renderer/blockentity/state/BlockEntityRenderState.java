package net.minecraft.client.renderer.blockentity.state;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** BBS-Shim fuer Minecraft vor 1.21.9: Grundzustand eines Blockentity-Renderers wie 1.21.11. */
public class BlockEntityRenderState
{
    public BlockPos blockPos = BlockPos.ZERO;
    public BlockState blockState;
    public BlockEntityType<?> blockEntityType;
    public int lightCoords;
    public ModelFeatureRenderer.CrumblingOverlay breakProgress;

    public static void extractBase(BlockEntity entity, BlockEntityRenderState zustand, ModelFeatureRenderer.CrumblingOverlay bruch)
    {
        zustand.blockPos = entity.getBlockPos();
        zustand.blockState = entity.getBlockState();
        zustand.blockEntityType = entity.getType();
        zustand.lightCoords = entity.getLevel() != null ? LevelRenderer.getLightColor(entity.getLevel(), entity.getBlockPos()) : 15728880;
        zustand.breakProgress = bruch;
    }
}
