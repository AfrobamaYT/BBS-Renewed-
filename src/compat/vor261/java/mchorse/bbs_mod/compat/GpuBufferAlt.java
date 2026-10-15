package mchorse.bbs_mod.compat;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;

/** 26.2's GpuBuffer.map(offset, length, read, write); before 26.1 the encoder maps a slice. Injected into GpuBuffer. */
public interface GpuBufferAlt
{
    default mchorse.bbs_mod.compat.alt.MappedView map(long offset, long length, boolean read, boolean write)
    {
        GpuBuffer buffer = (GpuBuffer) this;

        GpuBuffer.MappedView innen = Geraet.get().createCommandEncoder().mapBuffer(buffer.slice(offset, length), read, write);

        return new mchorse.bbs_mod.compat.alt.MappedView(innen.data(), buffer.slice(offset, length), innen::close);
    }
}
