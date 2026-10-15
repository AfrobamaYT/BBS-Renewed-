package mchorse.bbs_mod.integrations.vitrail;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import mchorse.bbs_mod.graphics.GehaltenesNetz;
import mchorse.bbs_mod.graphics.SofortZeichner;
import mchorse.bbs_mod.graphics.texture.Texture;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Matrix4f;

/** Retained BBS emitter meshes use the existing dedicated pack particle family. */
public final class VitrailRetainedParticles
{
    private VitrailRetainedParticles()
    {}

    public static boolean active()
    {
        return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("vitrail")
            && mchorse.bbs_mod.graphics.GpuBackend.isVulkan() && dev.vitrail.render.PackChain.drawingPack();
    }

    public static boolean eligible(RenderPipeline pipeline)
    {
        return pipeline == RenderPipelines.TRANSLUCENT_PARTICLE;
    }

    public static Matrix4f view()
    {
        return dev.vitrail.render.BbsNativeGeometry.particleView();
    }

    public static void draw(GehaltenesNetz mesh, Matrix4f view, Texture texture,
        RenderPass.RenderArea area)
    {
        if (texture == null) throw new IllegalStateException("Retained particle mesh has no texture");
        dev.vitrail.render.EntityDraw.endGroup();
        dev.vitrail.render.GraphicsApi.suspendLevelPass();
        com.mojang.blaze3d.textures.GpuTextureView color = dev.vitrail.render.GameRender.drawColour(null);
        com.mojang.blaze3d.textures.GpuTextureView depth = dev.vitrail.render.GameRender.drawDepth(null);
        net.minecraft.client.renderer.StagedVertexBuffer.ExecuteInfo execution = mesh.execution(RenderPipelines.TRANSLUCENT_PARTICLE);
        com.mojang.blaze3d.buffers.GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(view);
        RenderPipeline pipeline = dev.vitrail.render.ParticleDraw.group(true, color, depth);
        if (pipeline == null)
        {
            /* The stock family declined this draw (including its disabled option): use its real
             * pre-composite feature target, never the main target the final overwrites. */
            dev.vitrail.render.ParticleDraw.endGroup();
            RenderPass.RenderArea clipped = mchorse.bbs_mod.graphics.Zeichenbereich.begrenztAuf(area,
                color.getWidth(0), color.getHeight(0));
            mesh.zeichnen(RenderPipelines.TRANSLUCENT_PARTICLE, view, null,
                SofortZeichner.entityTextur(texture), color, depth, pass ->
                {
                    if (clipped != null) pass.enableScissor(clipped.x(), clipped.y(), clipped.width(), clipped.height());
                    else pass.disableScissor();
                });
            return;
        }
        try
        {
            RenderPassDescriptor descriptor = dev.vitrail.render.ParticleDraw.descriptor();
            com.mojang.blaze3d.systems.CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            try (RenderPass pass = descriptor == null
                ? encoder.createRenderPass(RenderPassDescriptor.create(() -> "bbs-retained-particles")
                    .withColorAttachment(color).withDepthAttachment(depth).build())
                : dev.vitrail.render.GeometryHold.open(encoder, descriptor))
            {
                dev.vitrail.render.ParticleDraw.opened(pass);
                dev.vitrail.render.GraphicsApi.setPipeline(pass, pipeline);
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", transforms);
                pass.setUniform("Sampler2", net.minecraft.client.Minecraft.getInstance().gameRenderer.lightmap(),
                    RenderSystem.getSamplerCache().getClampToEdge(com.mojang.blaze3d.textures.FilterMode.LINEAR));
                com.mojang.blaze3d.textures.GpuTextureView target = descriptor == null
                    ? color : descriptor.colorAttachments().getFirst().textureView();
                RenderPass.RenderArea clipped = mchorse.bbs_mod.graphics.Zeichenbereich.begrenztAuf(area,
                    target.getWidth(0), target.getHeight(0));
                if (clipped != null) pass.enableScissor(clipped.x(), clipped.y(), clipped.width(), clipped.height());
                else pass.disableScissor();
                pass.setUniform("Sampler0", texture.view(), texture.sampler());
                pass.setVertexBuffer(0, execution.vertexBuffer().slice());
                pass.setIndexBuffer(execution.indexBuffer(), execution.indexType());
                pass.drawIndexed(execution.indexCount(), 1, execution.firstIndex(), execution.baseVertex(), 0);
            }
        }
        finally { dev.vitrail.render.ParticleDraw.endGroup(); }
    }
}
