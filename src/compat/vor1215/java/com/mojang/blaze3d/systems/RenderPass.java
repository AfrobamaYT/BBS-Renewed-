package com.mojang.blaze3d.systems;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import mchorse.bbs_mod.compat.RenderPassAlt;

import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/* BBS-Shim fuer Minecraft vor 1.21.5: oeffentliche Form der 1.21.11-Fassung, eigene Implementierung
 * (siehe docs/MEHRVERSION.md "vor1215"). */
public interface RenderPass extends AutoCloseable, RenderPassAlt
{
    void pushDebugGroup(Supplier<String> label);
    void popDebugGroup();
    void setPipeline(RenderPipeline pipeline);
    void bindTexture(String name, GpuTextureView view, GpuSampler sampler);
    void setUniform(String name, GpuBuffer buffer);
    void setUniform(String name, GpuBufferSlice slice);
    void enableScissor(int x, int y, int width, int height);
    void disableScissor();
    void setVertexBuffer(int slot, GpuBuffer buffer);
    void setIndexBuffer(GpuBuffer buffer, VertexFormat.IndexType type);
    void drawIndexed(int baseVertex, int firstIndex, int indexCount, int instanceCount);
    <T> void drawMultipleIndexed(Collection<Draw<T>> draws, GpuBuffer defaultIndexBuffer, VertexFormat.IndexType defaultIndexType, Collection<String> dynamicUniforms, T context);
    void draw(int firstVertex, int vertexCount);

    @Override
    void close();

    record Draw<T>(int slot, GpuBuffer vertexBuffer, GpuBuffer indexBuffer, VertexFormat.IndexType indexType, int firstIndex, int indexCount,
        BiConsumer<T, UniformUploader> uniformUploaderConsumer)
    {
        public Draw(int slot, GpuBuffer vertexBuffer, GpuBuffer indexBuffer, VertexFormat.IndexType indexType, int firstIndex, int indexCount)
        {
            this(slot, vertexBuffer, indexBuffer, indexType, firstIndex, indexCount, null);
        }
    }

    interface UniformUploader
    {
        void upload(String name, GpuBufferSlice slice);
    }
}
