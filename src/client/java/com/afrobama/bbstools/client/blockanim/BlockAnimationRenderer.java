// Coded by Afrobama/Claude — Block Animation renderer.
// For each AnimatedBlock in BlockAnimation, render the block's mesh at
// its current animated position. Uses MC's BlockRenderManager to draw
// blocks as model meshes.
//
// Caveat: the source blocks in the world are NOT removed/modified — we
// just overlay copies. Real terrain stays intact. For visual cinematic
// recording this is correct (BBS captures the rendered frames).
package com.afrobama.bbstools.client.blockanim;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import mchorse.bbs_mod.graphics.Blockmodell;
import net.minecraft.world.phys.Vec3;
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

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return;

        Vec3 camPos = ctx.gameRenderer().getLevelRenderState().cameraRenderState.pos;
        PoseStack matrices = ctx.matrixStack();
        SubmitNodeCollector consumers = new mchorse.bbs_mod.compat.gpu.SofortSammler(ctx.consumers());

        for (var ab : blocks)
        {
            Vec3 p = ab.currentPos();
            matrices.pushPose();
            matrices.translate(p.x - camPos.x, p.y - camPos.y, p.z - camPos.z);
            try
            {
                Blockmodell.einreichen(
                    ab.state,
                    matrices,
                    consumers,
                    0x00F000F0,  /* full brightness lightmap */
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
            }
            catch (Throwable t)
            {
                /* don't log per-block per-frame */
            }
            matrices.popPose();
        }

        /* 26.2: endBatch ist ersatzlos entfallen. Es leerte den Sofort-Puffer der Ebene, die
         * gerade beschrieben wurde. Es gibt keinen mehr: eingereichte Auftraege zeichnet der
         * Bildgraph, wenn er an der Reihe ist. */
    }

}
