package mchorse.bbs_mod.cubic.render.vao;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
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
        GpuBufferSlice transforms, GpuBufferSlice model, GpuBuffer skin, Texture texture)
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
            .getClampToEdge(com.mojang.renderpearl.api.textures.FilterMode.LINEAR);
        active.draws.add(new Draw(label, color, depth, area, pipeline, vao, transforms, model, skin,
            RenderSystem.getProjectionMatrixBuffer(), RenderSystem.getShaderFog(),
            RenderSystem.getGlobalSettingsUniform(), BBSRendering.isRenderingWorld()
                ? RenderSystem.getShaderLights() : UiLicht.scheibe(),
            texture.view(), texture.sampler(), mchorse.bbs_mod.forms.renderers.utils.FormUeberlagerung.view(),
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
        mchorse.bbs_mod.graphics.DrawPassLease lease = null;
        Draw previous = null;
        try
        {
            for (Draw draw : this.draws)
            {
                if (previous == null || !previous.compatible(draw))
                {
                    if (lease != null)
                    {
                        lease.close();
                        lease = null;
                    }
                    lease = mchorse.bbs_mod.graphics.DrawPassLease.open(
                        RenderSystem.getDevice().createCommandEncoder(), draw.label, draw.color, draw.depth, draw.area);
                }
                draw.render(lease);
                previous = draw;
            }
        }
        finally
        {
            this.draws.clear();
            if (lease != null)
            {
                lease.close();
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
        GpuBufferSlice transforms, GpuBufferSlice model, GpuBuffer skin,
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

        private void render(mchorse.bbs_mod.graphics.DrawPassLease lease)
        {
            RenderPass pass = lease.pass();
            pass.setPipeline(RenderSystem.getCompiledPipeline(lease.pipeline(this.pipeline)));
            if (this.projection != null) pass.setUniform("Projection", this.projection);
            if (this.fog != null) pass.setUniform("Fog", this.fog);
            if (this.globals != null) pass.setUniform("Globals", this.globals);
            if (this.lighting != null) pass.setUniform("Lighting", this.lighting);
            pass.setUniform("DynamicTransforms", this.transforms);
            pass.setUniform(ModelVAOPipeline.MODELL_BLOCK, this.model);
            if (this.skin != null) pass.setUniform("SkinMatrices", this.skin);
            pass.setUniform("Sampler0", this.texture, this.sampler);
            pass.setUniform("Sampler1", this.overlay, this.smooth);
            pass.setUniform("Sampler2", this.lightmap, this.smooth);
            this.vao.zeichnen(pass);
        }
    }
}
