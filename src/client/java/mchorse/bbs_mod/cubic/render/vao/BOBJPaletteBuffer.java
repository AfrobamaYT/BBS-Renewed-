package mchorse.bbs_mod.cubic.render.vao;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.frontend.FrontendCommandEncoder;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.function.Supplier;

/** Pose-owned palette storage. Every slice resolves to real native GPU memory before binding. */
final class BOBJPaletteBuffer implements GpuBuffer
{
    private static final int BYTES = BOBJGpuSkin.MAX_MATRICES * 12 * Float.BYTES;
    private static final int USAGE = GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_COPY_SRC;
    private final Supplier<String> label;
    private GpuBuffer persistent;
    private float[] rows;
    private boolean dirty;
    private boolean closed;

    static GpuBuffer write(float[] rows, GpuBuffer reuse, Supplier<String> label)
    {
        BOBJPaletteBuffer palette = reuse instanceof BOBJPaletteBuffer previous ? previous : new BOBJPaletteBuffer(reuse, label);

        if (palette.closed)
        {
            throw new IllegalStateException("Skin palette is closed");
        }

        if (rows.length * Float.BYTES > BYTES)
        {
            throw new IllegalArgumentException("Skin palette exceeds its native uniform block");
        }

        palette.rows = rows.clone();
        palette.dirty = true;
        palette.uploadWhenAllowed();
        return palette;
    }

    private BOBJPaletteBuffer(GpuBuffer persistent, Supplier<String> label)
    {
        this.persistent = persistent;
        this.label = label;
    }

    private boolean inPass()
    {
        return RenderSystem.getDevice().createCommandEncoder() instanceof FrontendCommandEncoder encoder
            && encoder.isInRenderPass();
    }

    private void fill(ByteBuffer buffer)
    {
        MemoryUtil.memSet(buffer, 0);
        buffer.order(ByteOrder.nativeOrder()).asFloatBuffer().put(this.rows);
    }

    private void uploadWhenAllowed()
    {
        RenderSystem.assertOnRenderThread();

        if (!this.dirty || this.persistent != null && this.inPass())
        {
            return;
        }

        ByteBuffer buffer = MemoryUtil.memAlloc(BYTES);

        try
        {
            this.fill(buffer);
            /* Resource initialization is the original native createBuffer path. Rewrites
             * only happen outside a render pass; active-pass draws use mapped slices. */
            if (this.persistent == null)
            {
                this.persistent = RenderSystem.getDevice().createBuffer(this.label, USAGE, buffer);
            }
            else
            {
                RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.persistent.slice(), buffer);
            }

            this.dirty = false;
        }
        finally
        {
            MemoryUtil.memFree(buffer);
        }
    }

    @Override
    public GpuBufferSlice slice()
    {
        if (this.isClosed())
        {
            throw new IllegalStateException("Skin palette is closed");
        }

        this.uploadWhenAllowed();

        if (!this.dirty)
        {
            return this.persistent.slice();
        }

        long alignment = Math.max(16, RenderSystem.getDevice().getDeviceInfo().limits().minUniformOffsetAlignment());

        /* FrontendRenderPass.setUniform(GpuBuffer) delegates to slice(). Like
         * ModelUniformBlock, use Minecraft's mapped transient allocation: each draw
         * owns a distinct native range, including non-zero offsets, until submission. */
        try (GpuBufferSlice.MappedView mapped = RenderSystem.getDevice().createCommandEncoder()
            .transientMemory().allocateGpuMapped(BYTES, alignment, USAGE))
        {
            this.fill(mapped.data());
            return mapped.slice();
        }
    }

    @Override
    public GpuBufferSlice slice(long offset, long length)
    {
        return this.slice().slice(offset, length);
    }

    @Override
    public GpuBufferSlice.MappedView map(long offset, long length, boolean read, boolean write)
    {
        return this.slice(offset, length).map(read, write);
    }

    @Override
    public long size()
    {
        return BYTES;
    }

    @Override
    public int usage()
    {
        return USAGE;
    }

    @Override
    public boolean isClosed()
    {
        return this.closed || this.persistent != null && this.persistent.isClosed();
    }

    @Override
    public void close()
    {
        if (this.closed)
        {
            return;
        }

        this.closed = true;
        this.rows = null;

        if (this.persistent != null)
        {
            this.persistent.close();
        }
    }
}
