package com.mojang.blaze3d.buffers;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL31;

/** BBS-Shim fuer Minecraft vor 1.21.2: 1.21.4s Puffer-Bindeziele. */
public enum BufferType
{
    VERTICES(GL15.GL_ARRAY_BUFFER), INDICES(GL15.GL_ELEMENT_ARRAY_BUFFER), PIXEL_PACK(GL21.GL_PIXEL_PACK_BUFFER),
    COPY_READ(GL31.GL_COPY_READ_BUFFER), COPY_WRITE(GL31.GL_COPY_WRITE_BUFFER), PIXEL_UNPACK(GL21.GL_PIXEL_UNPACK_BUFFER),
    UNIFORM(GL31.GL_UNIFORM_BUFFER);

    final int id;

    BufferType(int id)
    {
        this.id = id;
    }
}
