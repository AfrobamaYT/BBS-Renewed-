package mchorse.bbs_mod.compat;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;

/**
 * vor1215: 1.21.11s GpuBuffer-Methoden an 1.21.4s vorhandenem GpuBuffer (Mixin GpuBufferAltMixin).
 * Nutzungsbits kennt nur der Schicht-Puffer GlBuffer; fremde Puffer melden 0.
 */
public interface GpuBufferAlt
{
    private GpuBuffer puffer()
    {
        return (GpuBuffer) this;
    }

    default long size()
    {
        return this.puffer().size;
    }

    default int usage()
    {
        return this instanceof com.mojang.blaze3d.opengl.GlBuffer gl ? gl.bbsNutzung() : 0;
    }

    default boolean isClosed()
    {
        return this instanceof com.mojang.blaze3d.opengl.GlBuffer gl && gl.bbsGeschlossen();
    }

    default GpuBufferSlice slice()
    {
        return new GpuBufferSlice(this.puffer(), 0L, this.size());
    }

    default GpuBufferSlice slice(long offset, long length)
    {
        return this.slice().slice(offset, length);
    }

    default mchorse.bbs_mod.compat.alt.MappedView map(long offset, long length, boolean read, boolean write)
    {
        return Geraet.get().createCommandEncoder().mapBuffer(this.slice(offset, length), read, write);
    }
}
