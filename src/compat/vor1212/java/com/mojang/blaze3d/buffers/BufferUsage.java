package com.mojang.blaze3d.buffers;

import org.lwjgl.opengl.GL15;

/** BBS-Shim fuer Minecraft vor 1.21.2: 1.21.4s Puffer-Nutzungsarten. */
public enum BufferUsage
{
    DYNAMIC_WRITE(GL15.GL_DYNAMIC_DRAW, false, true), STATIC_WRITE(GL15.GL_STATIC_DRAW, false, true), STREAM_WRITE(GL15.GL_STREAM_DRAW, false, true),
    STATIC_READ(GL15.GL_STATIC_READ, true, false), DYNAMIC_READ(GL15.GL_DYNAMIC_READ, true, false), STREAM_READ(GL15.GL_STREAM_READ, true, false),
    DYNAMIC_COPY(GL15.GL_DYNAMIC_COPY, false, false), STATIC_COPY(GL15.GL_STATIC_COPY, false, false), STREAM_COPY(GL15.GL_STREAM_COPY, false, false);

    final int id;
    final boolean readable;
    final boolean writable;

    BufferUsage(int id, boolean readable, boolean writable)
    {
        this.id = id;
        this.readable = readable;
        this.writable = writable;
    }
}
