package mchorse.bbs_mod.compat.alt;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.compat.Geraet;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * 26.2's per-frame transient upload memory, for the versions before 26.1: each upload gets its
 * own buffer, closed when the frame is flipped (RenderSystemAltMixin). OpenGL keeps a buffer
 * alive until the draws already issued with it are done, so closing at the flip is safe.
 */
public final class TransientMemory
{
    public static final TransientMemory INSTANZ = new TransientMemory();

    private final List<GpuBuffer> diesesBild = new ArrayList<>();

    private TransientMemory()
    {}

    public GpuBufferSlice uploadGpu(ByteBuffer daten, long ausrichtung, int nutzung)
    {
        RenderSystem.assertOnRenderThread();

        GpuBuffer puffer = Geraet.get().createBuffer(() -> "bbs-transient", nutzung, daten);

        this.diesesBild.add(puffer);

        return puffer.slice();
    }

    /**
     * 26.2's mapped transient allocation, which 26.2 also allows inside a render pass (BBS writes
     * its model uniform block there). Mojang's mapBuffer before 26.1 refuses inside a pass, so
     * the caller writes into CPU memory and closing the view uploads it with glBufferSubData,
     * which OpenGL allows at any time. The buffer lives until the frame is flipped.
     */
    public MappedView allocateGpuMapped(int groesse, long ausrichtung, int nutzung)
    {
        RenderSystem.assertOnRenderThread();

        GpuBuffer puffer = Geraet.get().createBuffer(() -> "bbs-transient-mapped", nutzung | mchorse.bbs_mod.compat.gpu.PufferNutzung.USAGE_COPY_DST, groesse);
        java.nio.ByteBuffer daten = org.lwjgl.system.MemoryUtil.memCalloc(groesse);

        this.diesesBild.add(puffer);

        return new MappedView(daten, puffer.slice(), () ->
        {
            org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL31.GL_COPY_WRITE_BUFFER, ((com.mojang.blaze3d.opengl.GlBuffer) puffer).handle);
            org.lwjgl.opengl.GL15.glBufferSubData(org.lwjgl.opengl.GL31.GL_COPY_WRITE_BUFFER, 0L, daten);
            org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL31.GL_COPY_WRITE_BUFFER, 0);
            org.lwjgl.system.MemoryUtil.memFree(daten);
        });
    }

    /** Called when Minecraft flips the frame. */
    public void bildEnde()
    {
        for (GpuBuffer puffer : this.diesesBild)
        {
            puffer.close();
        }

        this.diesesBild.clear();
    }
}
