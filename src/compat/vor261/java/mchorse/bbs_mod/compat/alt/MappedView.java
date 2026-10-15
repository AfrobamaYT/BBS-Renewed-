package mchorse.bbs_mod.compat.alt;

import com.mojang.blaze3d.buffers.GpuBufferSlice;

import java.nio.ByteBuffer;

/**
 * 26.2's GpuBufferSlice.MappedView (data plus the mapped slice), either over the older
 * GpuBuffer.MappedView or over CPU memory that closing uploads (TransientMemory).
 */
public final class MappedView implements AutoCloseable
{
    private final ByteBuffer daten;
    private final GpuBufferSlice slice;
    private final Runnable schliessen;

    public MappedView(ByteBuffer daten, GpuBufferSlice slice, Runnable schliessen)
    {
        this.daten = daten;
        this.slice = slice;
        this.schliessen = schliessen;
    }

    public ByteBuffer data()
    {
        return this.daten;
    }

    public GpuBufferSlice slice()
    {
        return this.slice;
    }

    @Override
    public void close()
    {
        this.schliessen.run();
    }
}
