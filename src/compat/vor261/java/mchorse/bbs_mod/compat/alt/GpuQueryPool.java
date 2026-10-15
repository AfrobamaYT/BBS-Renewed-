package mchorse.bbs_mod.compat.alt;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

import java.util.OptionalLong;

/**
 * 26.2's timestamp query pool on OpenGL query objects, for the versions before 26.1 (26.2's GL
 * backend does the same: glQueryCounter with GL_TIMESTAMP, results in nanoseconds).
 */
public final class GpuQueryPool implements AutoCloseable
{
    private final int[] ids;
    private boolean closed;

    public GpuQueryPool(int size)
    {
        RenderSystem.assertOnRenderThread();
        this.ids = new int[size];
        GL15.glGenQueries(this.ids);
    }

    public int size()
    {
        return this.ids.length;
    }

    /** Writes the GPU time at this point of the command stream into slot {@code index}. */
    public void stempeln(int index)
    {
        if (this.closed) throw new IllegalStateException("Query pool is closed");

        GL33.glQueryCounter(this.ids[index], GL33.GL_TIMESTAMP);
    }

    public OptionalLong getValue(int index)
    {
        if (this.closed) throw new IllegalStateException("Query pool is closed");

        if (GL15.glGetQueryObjecti(this.ids[index], GL15.GL_QUERY_RESULT_AVAILABLE) == 0) return OptionalLong.empty();

        return OptionalLong.of(GL33.glGetQueryObjecti64(this.ids[index], GL15.GL_QUERY_RESULT));
    }

    public OptionalLong[] getValues(int first, int count)
    {
        OptionalLong[] werte = new OptionalLong[count];

        for (int i = 0; i < count; i++) werte[i] = this.getValue(first + i);

        return werte;
    }

    @Override
    public void close()
    {
        if (!this.closed)
        {
            GL15.glDeleteQueries(this.ids);
            this.closed = true;
        }
    }
}
