package mchorse.bbs_mod.graphics;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.compat.alt.GpuFormat;
import com.mojang.blaze3d.systems.RenderPass;
import mchorse.bbs_mod.compat.alt.RenderPassDescriptor;
import mchorse.bbs_mod.compat.alt.BindGroupLayout;
import mchorse.bbs_mod.compat.alt.ColorTargetState;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/** Native full-frame linear resampling with opaque alpha for film preview and recording. */
public final class ExportBlit
{
    private static final RenderPipeline PIPELINE = RenderPipeline.builder()
        .withLocation(new ResourceLocation("bbs", "pipeline/export_opaque"))
        .withVertexShader(new ResourceLocation("minecraft", "core/screenquad"))
        .withFragmentShader(new ResourceLocation("bbs", "core/export_opaque"))
        .withBindGroupLayout(BindGroupLayout.builder()
            .withSampler("InSampler").build())
        .withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_ALL))
        .withPrimitiveTopology(VertexFormat.Mode.TRIANGLES)
        .withCull(false)
        .build();

    private ExportBlit() {}

    public static void blit(GpuTextureView source, GpuTextureView destination)
    {
        RenderSystem.assertOnRenderThread();
        if (source.texture() == destination.texture())
            throw new IllegalArgumentException("Export cannot sample and write the same texture");
        RenderPassDescriptor descriptor = RenderPassDescriptor.create(() -> "bbs-export-opaque")
            .withColorAttachment(destination).build();
        RenderPipeline pipeline = NativeTargetPipeline.forAttachments(PIPELINE, descriptor);
        try (RenderPass pass = mchorse.bbs_mod.compat.Geraet.get().createCommandEncoder().createRenderPass(descriptor))
        {
            pass.setPipeline(mchorse.bbs_mod.compat.Pipelines.compiled(pipeline));
            pass.setUniform("InSampler", source, mchorse.bbs_mod.compat.gpu.Vorgaben.samplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.draw(3, 1, 0, 0);
        }
    }
}
