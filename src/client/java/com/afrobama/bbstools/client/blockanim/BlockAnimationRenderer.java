// Coded by Afrobama/Claude — Block Animation renderer.
// For each AnimatedBlock in BlockAnimation, render the block's mesh at
// its current animated position. Uses MC's BlockRenderManager to draw
// blocks as model meshes.
//
// Caveat: the source blocks in the world are NOT removed/modified — we
// just overlay copies. Real terrain stays intact. For visual cinematic
// recording this is correct (BBS captures the rendered frames).
package com.afrobama.bbstools.client.blockanim;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BlockAnimationRenderer
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/blockanim/render");

    private BlockAnimationRenderer() {}

    public static void register()
    {
        WorldRenderEvents.AFTER_ENTITIES.register(BlockAnimationRenderer::onRender);
        LOG.info("Block-animation renderer registered");
    }

    private static void onRender(WorldRenderContext ctx)
    {
        var blocks = BlockAnimation.getActiveBlocks();
        if (blocks.isEmpty()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null) return;

        BlockRenderManager brm = mc.getBlockRenderManager();
        Vec3d camPos = ctx.camera().getPos();
        MatrixStack matrices = ctx.matrixStack();
        VertexConsumerProvider.Immediate consumers = mc.getBufferBuilders().getEntityVertexConsumers();

        for (var ab : blocks)
        {
            Vec3d p = ab.currentPos();
            matrices.push();
            matrices.translate(p.x - camPos.x, p.y - camPos.y, p.z - camPos.z);
            try
            {
                brm.renderBlockAsEntity(
                    ab.state,
                    matrices,
                    consumers,
                    0x00F000F0,  /* full brightness lightmap */
                    net.minecraft.client.render.OverlayTexture.DEFAULT_UV);
            }
            catch (Throwable t)
            {
                /* don't log per-block per-frame */
            }
            matrices.pop();
        }
        consumers.draw();
    }

}
