package com.mojang.blaze3d.buffers;

import mchorse.bbs_mod.compat.GpuBufferAlt;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL31;

import java.nio.ByteBuffer;

/**
 * BBS-Shim fuer Minecraft vor 1.21.2: 1.21.4s GpuBuffer (GL-Name in handle), auf dem die
 * eigene GL-Schicht (GlBuffer) aufbaut. Angelegt und beschrieben ueber COPY_WRITE, damit kein
 * Element-Puffer eines gebundenen VAO verstellt wird; GpuBufferAlt gehoert direkt dazu.
 */
public class GpuBuffer implements AutoCloseable, GpuBufferAlt
{
    private final BufferType type;
    private final BufferUsage usage;
    private boolean closed;
    public final int handle;
    public int size;
    /* Fremder GL-Puffer (z. B. Mojangs Indexpuffer): nur huellen, nie loeschen. */
    private final boolean fremd;

    public GpuBuffer(BufferType type, BufferUsage usage, int size)
    {
        this(type, usage, size, GL15.glGenBuffers(), false);
    }

    private GpuBuffer(BufferType type, BufferUsage usage, int size, int handle, boolean fremd)
    {
        this.type = type;
        this.usage = usage;
        this.size = size;
        this.handle = handle;
        this.fremd = fremd;
    }

    public static GpuBuffer fremd(BufferType type, int handle, int size)
    {
        return new GpuBuffer(type, BufferUsage.STATIC_WRITE, size, handle, true);
    }

    public GpuBuffer(BufferType type, BufferUsage usage, ByteBuffer data)
    {
        this(type, usage, data.remaining());
        this.write(data, 0);
    }

    public void resize(int size)
    {
        if (this.closed) throw new IllegalStateException("Puffer ist geschlossen");

        this.size = size;
        GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, this.handle);
        GL15.glBufferData(GL31.GL_COPY_WRITE_BUFFER, size, this.usage.id);
        GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, 0);
    }

    public void write(ByteBuffer data, int offset)
    {
        if (this.closed) throw new IllegalStateException("Puffer ist geschlossen");

        GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, this.handle);

        if (offset == 0 && data.remaining() >= this.size)
        {
            this.size = data.remaining();
            GL15.glBufferData(GL31.GL_COPY_WRITE_BUFFER, data, this.usage.id);
        }
        else
        {
            GL15.glBufferSubData(GL31.GL_COPY_WRITE_BUFFER, offset, data);
        }

        GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, 0);
    }

    public void bind()
    {
        GL15.glBindBuffer(this.type.id, this.handle);
    }

    @Override
    public void close()
    {
        if (!this.closed)
        {
            this.closed = true;

            if (!this.fremd)
            {
                GL15.glDeleteBuffers(this.handle);
            }
        }
    }
}
