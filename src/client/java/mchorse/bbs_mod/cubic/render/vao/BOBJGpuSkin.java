package mchorse.bbs_mod.cubic.render.vao;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.bobj.BOBJHaut;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL31C;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Static skin influences plus one small matrix UBO per evaluated pose. */
public final class BOBJGpuSkin implements AutoCloseable
{
    static volatile boolean ENABLED = true;
    static final int MAX_MATRICES = 1024;
    private static final int BLOCK_BYTES = MAX_MATRICES * 3 * 4 * Float.BYTES;

    private final GpuBuffer indices0;
    private final GpuBuffer indices1;
    private final GpuBuffer weights0;
    private final GpuBuffer weights1;

    static boolean supported(BOBJHaut.Palette palette)
    {
        return palette.matrixCount() <= MAX_MATRICES
            && GL11C.glGetInteger(GL31C.GL_MAX_UNIFORM_BLOCK_SIZE) >= BLOCK_BYTES;
    }

    BOBJGpuSkin(BOBJHaut.Palette palette, int vertices)
    {
        int[] indices = new int[vertices * 8];
        float[] weights = new float[vertices * 8];
        palette.vertexInfluences(indices, weights);
        this.indices0 = integerBuffer("bobj-joints-0", indices, 0, vertices);
        this.indices1 = integerBuffer("bobj-joints-1", indices, 4, vertices);
        this.weights0 = floatBuffer("bobj-weights-0", weights, 0, vertices);
        this.weights1 = floatBuffer("bobj-weights-1", weights, 4, vertices);
    }

    private static GpuBuffer integerBuffer(String name, int[] source, int half, int vertices)
    {
        ByteBuffer data = MemoryUtil.memAlloc(vertices * 4 * Integer.BYTES).order(ByteOrder.nativeOrder());

        try
        {
            for (int vertex = 0; vertex < vertices; vertex++)
            {
                for (int i = 0; i < 4; i++)
                {
                    data.putInt(source[vertex * 8 + half + i]);
                }
            }

            data.flip();
            return RenderSystem.getDevice().createBuffer(() -> name, GpuBuffer.USAGE_VERTEX, data);
        }
        finally
        {
            MemoryUtil.memFree(data);
        }
    }

    private static GpuBuffer floatBuffer(String name, float[] source, int half, int vertices)
    {
        ByteBuffer data = MemoryUtil.memAlloc(vertices * 4 * Float.BYTES).order(ByteOrder.nativeOrder());

        try
        {
            for (int vertex = 0; vertex < vertices; vertex++)
            {
                for (int i = 0; i < 4; i++)
                {
                    data.putFloat(source[vertex * 8 + half + i]);
                }
            }

            data.flip();
            return RenderSystem.getDevice().createBuffer(() -> name, GpuBuffer.USAGE_VERTEX, data);
        }
        finally
        {
            MemoryUtil.memFree(data);
        }
    }

    GpuBuffer writePalette(BOBJHaut.Palette palette, GpuBuffer reuse)
    {
        ByteBuffer rows = MemoryUtil.memCalloc(BLOCK_BYTES).order(ByteOrder.nativeOrder());

        try
        {
            rows.asFloatBuffer().put(palette.matrixRows());

            if (reuse == null)
            {
                return RenderSystem.getDevice().createBuffer(() -> "bobj-skin-matrices",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, rows);
            }

            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(reuse.slice(), rows);
            return reuse;
        }
        finally
        {
            MemoryUtil.memFree(rows);
        }
    }

    void bind(RenderPass pass)
    {
        pass.setVertexBuffer(ModelVAOPipeline.BINDUNG_JOINTS0, this.indices0.slice());
        pass.setVertexBuffer(ModelVAOPipeline.BINDUNG_WEIGHTS0, this.weights0.slice());
        pass.setVertexBuffer(ModelVAOPipeline.BINDUNG_JOINTS1, this.indices1.slice());
        pass.setVertexBuffer(ModelVAOPipeline.BINDUNG_WEIGHTS1, this.weights1.slice());
    }

    @Override
    public void close()
    {
        this.indices0.close();
        this.indices1.close();
        this.weights0.close();
        this.weights1.close();
    }
}
