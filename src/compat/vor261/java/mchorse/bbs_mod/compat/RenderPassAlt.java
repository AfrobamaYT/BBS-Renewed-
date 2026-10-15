package mchorse.bbs_mod.compat;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;

/**
 * 26.2's RenderPass calls, for the versions before 26.1: textures as sampler uniforms, vertex
 * buffers as slices in up to 16 bindings, draws in Vulkan's argument order with instances.
 * Injected into RenderPass; Mojang's only implementation, GlRenderPass, implements them
 * ({@link mchorse.bbs_mod.compat.mixin.GlRenderPassAltMixin}).
 */
public interface RenderPassAlt
{
    default void setUniform(String name, GpuTextureView view, GpuSampler sampler)
    {
        ((RenderPass) this).bindTexture(name, view, sampler);
    }

    default void setVertexBuffer(int binding, GpuBufferSlice slice)
    {
        throw new UnsupportedOperationException("compat-Mixin fehlt");
    }

    default void draw(int vertexCount, int instanceCount, int firstVertex, int firstInstance)
    {
        throw new UnsupportedOperationException("compat-Mixin fehlt");
    }

    default void drawIndexed(int indexCount, int instanceCount, int firstIndex, int baseVertex, int firstInstance)
    {
        throw new UnsupportedOperationException("compat-Mixin fehlt");
    }

    /** The slices set through {@link #setVertexBuffer(int, GpuBufferSlice)}, by binding. */
    default GpuBufferSlice[] bbsBindungen()
    {
        throw new UnsupportedOperationException("compat-Mixin fehlt");
    }

    default int bbsErsteInstanz()
    {
        throw new UnsupportedOperationException("compat-Mixin fehlt");
    }
}
