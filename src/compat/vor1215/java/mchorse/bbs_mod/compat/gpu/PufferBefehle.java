package mchorse.bbs_mod.compat.gpu;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.opengl.GlStateManager;
import mchorse.bbs_mod.compat.alt.MappedView;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;

import java.nio.ByteBuffer;

/** Puffer schreiben, abbilden und kopieren fuer die Schicht vor 1.21.5. */
public final class PufferBefehle
{
    private PufferBefehle()
    {}

    public static void schreiben(GpuBufferSlice target, ByteBuffer data)
    {
        GlStateManager._glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, target.buffer().handle);
        GL15.glBufferSubData(GL31.GL_COPY_WRITE_BUFFER, target.offset(), data);
        GlStateManager._glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, 0);
    }

    public static MappedView abbilden(GpuBufferSlice slice, boolean read, boolean write)
    {
        int ziel = read ? GL31.GL_COPY_READ_BUFFER : GL31.GL_COPY_WRITE_BUFFER;

        GlStateManager._glBindBuffer(ziel, slice.buffer().handle);

        ByteBuffer daten = GL30.glMapBufferRange(ziel, slice.offset(), slice.length(), (read ? GL30.GL_MAP_READ_BIT : 0) | (write ? GL30.GL_MAP_WRITE_BIT : 0));

        if (daten == null) throw new IllegalStateException("Puffer laesst sich nicht abbilden (GL-Fehler " + GL11.glGetError() + ")");

        return new MappedView(daten, slice, () ->
        {
            GlStateManager._glBindBuffer(ziel, slice.buffer().handle);
            GL15.glUnmapBuffer(ziel);
            GlStateManager._glBindBuffer(ziel, 0);
        });
    }

    public static void kopieren(GpuBufferSlice source, GpuBufferSlice target)
    {
        if (source.length() != target.length()) throw new IllegalArgumentException("Quelle und Ziel verschieden lang");

        GlStateManager._glBindBuffer(GL31.GL_COPY_READ_BUFFER, source.buffer().handle);
        GlStateManager._glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, target.buffer().handle);
        GL31.glCopyBufferSubData(GL31.GL_COPY_READ_BUFFER, GL31.GL_COPY_WRITE_BUFFER, source.offset(), target.offset(), source.length());
        GlStateManager._glBindBuffer(GL31.GL_COPY_READ_BUFFER, 0);
        GlStateManager._glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, 0);
    }
}
