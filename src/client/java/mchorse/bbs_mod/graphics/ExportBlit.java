package mchorse.bbs_mod.graphics;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/** Native full-frame linear resampling with opaque alpha for film preview and recording. */
public final class ExportBlit
{
    private static final RenderPipeline PIPELINE = RenderPipeline.builder()
        .withLocation(Identifier.fromNamespaceAndPath("bbs", "pipeline/export_opaque"))
        .withVertexShader(Identifier.fromNamespaceAndPath("minecraft", "core/screenquad"))
        .withFragmentShader(Identifier.fromNamespaceAndPath("bbs", "core/export_opaque"))
        .withBindGroupLayout(BindGroupLayout.builder()
            .withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER).build())
        .withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_ALL))
        .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
        .withCull(false)
        .build();

    private ExportBlit() {}

    public static void blit(GpuTextureView source, GpuTextureView destination)
    {
        RenderSystem.assertOnRenderThread();
        if (source.texture() == destination.texture())
            throw new IllegalArgumentException("Export cannot sample and write the same texture");
        RenderPassDescriptor descriptor = RenderPassDescriptor.builder(() -> "bbs-export-opaque")
            .withColorAttachment(destination).build();
        RenderPipeline pipeline = NativeTargetPipeline.forAttachments(PIPELINE, descriptor);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(descriptor))
        {
            pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
            pass.setUniform("InSampler", source, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.draw(3, 1, 0, 0);
        }
    }
}
