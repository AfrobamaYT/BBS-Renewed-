package mchorse.bbs_mod.integrations.vitrail;

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
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.vitrail.render.storage.BbsEmissionStorage;
import mchorse.bbs_mod.graphics.GpuReadback;
import mchorse.bbs_mod.graphics.NativeTargetPipeline;
import net.minecraft.resources.ResourceLocation;
import java.nio.ByteBuffer;
import java.util.Optional;

/** Post-final opaque resolve. The caller must establish that no translucent world layer contributes above it. */
public final class VitrailEmissionCompositor implements AutoCloseable
{
    private static final RenderPipeline RESOLVE = pipeline("resolve", true);
    private static final RenderPipeline STATUS = pipeline("status", false);
    private final GpuTexture status;
    private final GpuTextureView statusView;

    private static RenderPipeline pipeline(String name, boolean readsScene)
    {
        BindGroupLayout.Builder bindings = BindGroupLayout.builder()
            .withUniform(BbsEmissionStorage.NAME, UniformType.UNIFORM_BUFFER);
        if (readsScene) bindings.withSampler("CompletedScene")
            .withSampler("SceneDepth");
        return RenderPipeline.builder()
            .withLocation(ResourceLocation.fromNamespaceAndPath("bbs", "pipeline/vitrail_emission_" + name))
            .withVertexShader(ResourceLocation.fromNamespaceAndPath("minecraft", "core/screenquad"))
            .withFragmentShader(ResourceLocation.fromNamespaceAndPath("bbs", "core/vitrail_emission_" + name))
            .withBindGroupLayout(bindings.build())
            .withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_ALL))
            .withPrimitiveTopology(VertexFormat.Mode.TRIANGLES).withCull(false).build();
    }

    public VitrailEmissionCompositor()
    {
        RenderSystem.assertOnRenderThread();
        this.status = mchorse.bbs_mod.compat.Geraet.get().createTexture("bbs-emission-status",
            GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_SRC, GpuFormat.RGBA8_UNORM, 1, 1, 1, 1);
        try { this.statusView = mchorse.bbs_mod.compat.Geraet.get().createTextureView(this.status); }
        catch (Throwable failure) { this.status.close(); throw failure; }
    }

    /** Input and output must be separate images. A partial/foreign translucent contribution is explicitly unsupported here. */
    public void composeOpaque(GpuTextureView completed, GpuTextureView savedDepth,
        GpuTextureView target, boolean hasTranslucentContributions)
    {
        RenderSystem.assertOnRenderThread();
        if (hasTranslucentContributions)
            throw new IllegalStateException("Vitrail emission needs completed background/layers for translucent contributions");
        if (completed.texture() == target.texture() || savedDepth.texture() == target.texture())
            throw new IllegalArgumentException("Emission resolve cannot sample a written attachment");
        if (completed.getWidth(0) != target.getWidth(0) || completed.getHeight(0) != target.getHeight(0)
            || savedDepth.getWidth(0) != target.getWidth(0) || savedDepth.getHeight(0) != target.getHeight(0))
            throw new IllegalArgumentException("Emission scene/depth/output extents differ");
        RenderPassDescriptor descriptor = RenderPassDescriptor.create(() -> "bbs-emission-opaque-resolve")
            .withColorAttachment(target).build();
        try (RenderPass pass = mchorse.bbs_mod.compat.Geraet.get().createCommandEncoder().createRenderPass(descriptor))
        {
            pass.setPipeline(mchorse.bbs_mod.compat.Pipelines.compiled(NativeTargetPipeline.forAttachments(RESOLVE, descriptor)));
            BbsEmissionStorage.bind(pass);
            pass.setUniform("CompletedScene", completed, mchorse.bbs_mod.compat.gpu.Vorgaben.samplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.setUniform("SceneDepth", savedDepth, mchorse.bbs_mod.compat.gpu.Vorgaben.samplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.draw(3, 1, 0, 0);
        }
        this.validate();
    }

    private void validate()
    {
        RenderPassDescriptor descriptor = RenderPassDescriptor.create(() -> "bbs-emission-resolve-status")
            .withColorAttachment(this.statusView).build();
        try (RenderPass pass = mchorse.bbs_mod.compat.Geraet.get().createCommandEncoder().createRenderPass(descriptor))
        {
            pass.setPipeline(mchorse.bbs_mod.compat.Pipelines.compiled(STATUS));
            BbsEmissionStorage.bind(pass);
            pass.draw(3, 1, 0, 0);
        }
        try (GpuReadback.Raw result = GpuReadback.readRaw(this.status))
        {
            ByteBuffer bytes = result.data();
            int flags = Byte.toUnsignedInt(bytes.get(0)) | Byte.toUnsignedInt(bytes.get(1)) << 8
                | Byte.toUnsignedInt(bytes.get(2)) << 16 | Byte.toUnsignedInt(bytes.get(3)) << 24;
            if (flags != 0) throw new IllegalStateException("Vitrail emission resolve rejected captured fragments: flags=" + flags
                + " (1=overflow,2=extent,4=translucent,8=list,16=early-depth,32=equal-depth order,64=invalid material)");
        }
    }

    @Override public void close()
    {
        RenderSystem.assertOnRenderThread();
        try { this.statusView.close(); } finally { this.status.close(); }
    }
}
