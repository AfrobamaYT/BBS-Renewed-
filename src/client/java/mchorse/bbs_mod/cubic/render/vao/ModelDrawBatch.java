package mchorse.bbs_mod.cubic.render.vao;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.graphics.UiLicht;
import mchorse.bbs_mod.graphics.texture.Texture;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

/** Ordered draws for one all-VAO model. Uploads finish before any render pass opens. */
public final class ModelDrawBatch implements AutoCloseable
{
    /* Half of ModelVAORenderer's 256 uniform blocks: never overwrite a queued draw. */
    private static final int MAX_DRAWS = 128;
    private static ModelDrawBatch active;
    private final ModelDrawBatch parent;
    private final List<Draw> draws = new ArrayList<>();
    private final boolean enabled;
    private boolean closed;

    public static ModelDrawBatch begin()
    {
        RenderSystem.assertOnRenderThread();
        if (active != null)
        {
            active.flush();
        }
        ModelDrawBatch batch = new ModelDrawBatch();
        active = batch;
        return batch;
    }

    /** A shared, mutable VAO must be drawn before the next upload changes its buffers. */
    public static void beforeMeshUpdate(IModelVAO vao)
    {
        if (active != null)
        {
            for (Draw draw : active.draws)
            {
                if (draw.vao == vao)
                {
                    active.flush();
                    return;
                }
            }
        }
    }

    static void beforeUniformWrite()
    {
        if (active != null && active.draws.size() >= MAX_DRAWS)
        {
            active.flush();
        }
    }

    static boolean collect(Supplier<String> label, GpuTextureView color, GpuTextureView depth,
        RenderPass.RenderArea area, RenderPipeline pipeline, IModelVAO vao,
        GpuBufferSlice transforms, GpuBuffer model, GpuBuffer skin, Texture texture)
    {
        if (active == null || !active.enabled)
        {
            return false;
        }
        if (texture == null)
        {
            active.flush();
            return false;
        }

        net.minecraft.client.renderer.GameRenderer renderer = Minecraft.getInstance().gameRenderer;
        GpuSampler smooth = RenderSystem.getSamplerCache()
            .getClampToEdge(com.mojang.blaze3d.textures.FilterMode.LINEAR);
        active.draws.add(new Draw(label, color, depth, area, pipeline, vao, transforms, model, skin,
            RenderSystem.getProjectionMatrixBuffer(), RenderSystem.getShaderFog(),
            RenderSystem.getGlobalSettingsUniform(), BBSRendering.isRenderingWorld()
                ? RenderSystem.getShaderLights() : UiLicht.scheibe(),
            texture.view(), texture.sampler(), renderer.overlayTexture().getTextureView(),
            renderer.lightmap(), smooth));
        return true;
    }

    private ModelDrawBatch()
    {
        this.parent = active;
        this.enabled = !Boolean.getBoolean("bbs.model.separatePasses")
            && !(FabricLoader.getInstance().isModLoaded("iris")
                && net.irisshaders.iris.api.v0.IrisApi.getInstance().isShaderPackInUse());
    }

    private void flush()
    {
        RenderPass pass = null;
        Draw previous = null;
        try
        {
            for (Draw draw : this.draws)
            {
                if (previous == null || !previous.compatible(draw))
                {
                    if (pass != null)
                    {
                        pass.close();
                        pass = null;
                    }
                    pass = draw.area == null
                        ? RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                            draw.label, draw.color, Optional.empty(), draw.depth, OptionalDouble.empty())
                        : RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                            draw.label, draw.color, Optional.empty(), draw.depth, OptionalDouble.empty(), draw.area);
                }
                draw.render(pass);
                previous = draw;
            }
        }
        finally
        {
            this.draws.clear();
            if (pass != null)
            {
                pass.close();
            }
        }
    }

    @Override
    public void close()
    {
        if (this.closed)
        {
            return;
        }
        if (active != this)
        {
            throw new IllegalStateException("Model draw batches must close in reverse nesting order");
        }
        try
        {
            this.flush();
        }
        finally
        {
            this.closed = true;
            active = this.parent;
        }
    }

    private record Draw(Supplier<String> label, GpuTextureView color, GpuTextureView depth,
        RenderPass.RenderArea area, RenderPipeline pipeline, IModelVAO vao,
        GpuBufferSlice transforms, GpuBuffer model, GpuBuffer skin,
        GpuBufferSlice projection, GpuBufferSlice fog, GpuBuffer globals, GpuBufferSlice lighting,
        GpuTextureView texture, GpuSampler sampler, GpuTextureView overlay,
        GpuTextureView lightmap, GpuSampler smooth)
    {
        private boolean compatible(Draw other)
        {
            return this.color == other.color && this.depth == other.depth
                && this.pipeline == other.pipeline && Objects.equals(this.area, other.area)
                && Objects.equals(this.projection, other.projection)
                && Objects.equals(this.fog, other.fog) && this.globals == other.globals
                && Objects.equals(this.lighting, other.lighting)
                && (this.skin == null) == (other.skin == null);
        }

        private void render(RenderPass pass)
        {
            pass.setPipeline(this.pipeline);
            if (this.projection != null) pass.setUniform("Projection", this.projection);
            if (this.fog != null) pass.setUniform("Fog", this.fog);
            if (this.globals != null) pass.setUniform("Globals", this.globals);
            if (this.lighting != null) pass.setUniform("Lighting", this.lighting);
            pass.setUniform("DynamicTransforms", this.transforms);
            pass.setUniform(ModelVAOPipeline.MODELL_BLOCK, this.model);
            if (this.skin != null) pass.setUniform("SkinMatrices", this.skin);
            pass.bindTexture("Sampler0", this.texture, this.sampler);
            pass.bindTexture("Sampler1", this.overlay, this.smooth);
            pass.bindTexture("Sampler2", this.lightmap, this.smooth);
            this.vao.zeichnen(pass);
        }
    }
}
