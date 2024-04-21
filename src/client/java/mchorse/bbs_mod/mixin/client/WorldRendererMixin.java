package mchorse.bbs_mod.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.ui.framework.UIScreen;
import net.fabricmc.fabric.impl.client.rendering.WorldRenderContextImpl;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin
{
    @Inject(method = "renderLayer", at = @At("TAIL"))
    public void onRenderChunkLayer(RenderLayer layer, MatrixStack stack, double x, double y, double z, Matrix4f positionMatrix, CallbackInfo info)
    {
        if (layer == RenderLayer.getSolid())
        {
            WorldRenderContextImpl worldRenderContext = new WorldRenderContextImpl();
            MinecraftClient mc = MinecraftClient.getInstance();

            worldRenderContext.prepare(
                mc.worldRenderer, stack, mc.getTickDelta(), mc.getRenderTime(), false,
                mc.gameRenderer.getCamera(), mc.gameRenderer, mc.gameRenderer.getLightmapTextureManager(),
                RenderSystem.getProjectionMatrix(), mc.getBufferBuilders().getEntityVertexConsumers(), null, false, mc.world
            );

            if (mc.currentScreen instanceof UIScreen screen)
            {
                screen.renderInWorld(worldRenderContext);
            }

            BBSModClient.getFilms().render(worldRenderContext);
        }
    }
}