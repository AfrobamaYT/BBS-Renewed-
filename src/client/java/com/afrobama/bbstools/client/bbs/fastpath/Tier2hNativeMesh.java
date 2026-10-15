package com.afrobama.bbstools.client.bbs.fastpath;

import com.afrobama.bbstools.client.bbs.mesh.SkinnedMeshIR;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.buffers.GpuBuffer;
import mchorse.bbs_mod.graphics.texture.Texture;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/** Persistent native static mesh and independently owned instance chunks. */
final class Tier2hNativeMesh implements AutoCloseable
{
    final SkinnedMeshIR source;
    final GpuBuffer vertices;
    final GpuBuffer indices;
    final int bones;
    final int indexCount;
    final Tier2hPaletteTexture atlas;
    final List<Chunk> chunks = new ArrayList<>();

    Tier2hNativeMesh(SkinnedMeshIR source)
    {
        if (source.format != 'C' || source.vertCount != source.quadCount * 4)
            throw new IllegalArgumentException("Native cubic instancing needs complete rigid quads");
        this.source = source;
        this.bones = Math.max(1, source.boneCount);
        this.indexCount = Math.multiplyExact(source.quadCount, 6);
        this.atlas = new Tier2hPaletteTexture(this.bones);
        ByteBuffer data = MemoryUtil.memAlloc(Math.multiplyExact(source.vertCount, Tier2hNativePipeline.VERTEX_BYTES))
            .order(ByteOrder.nativeOrder());
        try
        {
            Tier2hVertexPacking.mesh(data, source);
            this.vertices = mchorse.bbs_mod.compat.Geraet.get().createBuffer(() -> "bbs-tier2h-mesh", mchorse.bbs_mod.compat.gpu.PufferNutzung.USAGE_VERTEX, data);
        }
        finally
        {
            MemoryUtil.memFree(data);
        }
        data = MemoryUtil.memAlloc(Math.multiplyExact(this.indexCount, 4)).order(ByteOrder.nativeOrder());
        try
        {
            Tier2hVertexPacking.indices(data, source.quadCount);
            this.indices = mchorse.bbs_mod.compat.Geraet.get().createBuffer(() -> "bbs-tier2h-indices", mchorse.bbs_mod.compat.gpu.PufferNutzung.USAGE_INDEX, data);
        }
        catch (RuntimeException | Error failure)
        {
            this.vertices.close();
            throw failure;
        }
        finally
        {
            MemoryUtil.memFree(data);
        }
    }

    Chunk chunk(int index)
    {
        while (this.chunks.size() <= index) this.chunks.add(new Chunk(this.bones));
        return this.chunks.get(index);
    }

    @Override
    public void close()
    {
        this.chunks.forEach(Chunk::close);
        this.atlas.close();
        this.vertices.close();
        this.indices.close();
    }

    static final class Chunk implements AutoCloseable
    {
        GpuBuffer instances;
        final Tier2hPaletteTexture palette;
        int count;
        Texture texture;
        com.mojang.blaze3d.textures.GpuTextureView diffuse;
        com.mojang.blaze3d.textures.GpuSampler sampler;
        boolean vat;

        Chunk(int bones)
        {
            this.palette = new Tier2hPaletteTexture(bones);
        }

        void upload(ByteBuffer data, int count)
        {
            if (this.instances == null || this.instances.size() < data.remaining())
            {
                if (this.instances != null) this.instances.close();
                this.instances = mchorse.bbs_mod.compat.Geraet.get().createBuffer(() -> "bbs-tier2h-instances",
                    mchorse.bbs_mod.compat.gpu.PufferNutzung.USAGE_VERTEX | mchorse.bbs_mod.compat.gpu.PufferNutzung.USAGE_COPY_DST, data);
            }
            else mchorse.bbs_mod.compat.Geraet.get().createCommandEncoder().writeToBuffer(this.instances.slice(0, data.remaining()), data);
            this.count = count;
        }

        @Override
        public void close()
        {
            if (this.instances != null) this.instances.close();
            this.instances = null;
            this.palette.close();
        }
    }
}
