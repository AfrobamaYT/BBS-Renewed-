package com.mojang.blaze3d.buffers;

/* BBS-Shim fuer Minecraft vor 1.21.5: diese Klasse gibt es dort nicht; sie bildet die oeffentliche
 * Form der 1.21.11-Fassung nach (eigene Implementierung, siehe docs/MEHRVERSION.md "vor1215"). */
public record GpuBufferSlice(GpuBuffer buffer, long offset, long length)
{
    public GpuBufferSlice slice(long offset, long length)
    {
        if (offset < 0 || length < 0 || offset + length > this.length)
        {
            throw new IllegalArgumentException("Ausschnitt " + offset + "+" + length + " liegt ausserhalb von " + this.length);
        }

        return new GpuBufferSlice(this.buffer, this.offset + offset, length);
    }
}
