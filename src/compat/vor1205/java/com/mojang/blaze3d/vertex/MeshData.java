package com.mojang.blaze3d.vertex;

import java.nio.ByteBuffer;

/**
 * vor1205: 1.21s MeshData. Entweder Huelle um BufferBuilder.RenderedBuffer von 1.20.4 (aus
 * BufferBuilder#build) oder um Rohspeicher aus ByteBufferBuilder#build (Schnellpfade).
 */
public class MeshData implements AutoCloseable
{
    private final BufferBuilder.RenderedBuffer fertig;
    private final ByteBufferBuilder.Result roh;
    private final DrawState zustand;

    public MeshData(BufferBuilder.RenderedBuffer fertig)
    {
        BufferBuilder.DrawState alt = fertig.drawState();

        this.fertig = fertig;
        this.roh = null;
        this.zustand = new DrawState(alt.format(), alt.vertexCount(), alt.indexCount(), alt.mode(), alt.indexType());
    }

    public MeshData(ByteBufferBuilder.Result roh, DrawState zustand)
    {
        this.fertig = null;
        this.roh = roh;
        this.zustand = zustand;
    }

    /** Fuer BufferUploader von 1.20.4; nur fuer Netze aus BufferBuilder#build. */
    public BufferBuilder.RenderedBuffer alt()
    {
        if (this.fertig == null)
        {
            throw new IllegalStateException("MeshData aus Rohspeicher hat keinen RenderedBuffer");
        }

        return this.fertig;
    }

    public ByteBuffer vertexBuffer()
    {
        return this.fertig != null ? this.fertig.vertexBuffer() : this.roh.byteBuffer();
    }

    /** Wie ab 1.21: null, wenn die Folge-Indizes gelten. */
    public ByteBuffer indexBuffer()
    {
        return this.fertig == null || this.fertig.drawState().sequentialIndex() ? null : this.fertig.indexBuffer();
    }

    public DrawState drawState()
    {
        return this.zustand;
    }

    @Override
    public void close()
    {
        if (this.fertig != null) this.fertig.release();
        else this.roh.close();
    }

    public record DrawState(VertexFormat format, int vertexCount, int indexCount, VertexFormat.Mode mode, VertexFormat.IndexType indexType)
    {
        public VertexFormat.Mode primitiveTopology()
        {
            return this.mode;
        }
    }
}
