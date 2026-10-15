package mchorse.bbs_mod.graphics;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.compat.alt.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.textures.GpuTexture;
import org.lwjgl.system.MemoryUtil;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;
import java.util.function.Consumer;

class GpuReadbackRequest implements AutoCloseable
{
    private final CommandEncoder encoder;
    private final GpuFormat format;
    private final GpuReadback.Region region;
    private GpuBuffer buffer;
    private GpuFence fence;
    private GpuReadback.Raw result;
    private RuntimeException failure;
    private Consumer<GpuReadback.Raw> consumer;
    private Consumer<RuntimeException> failureConsumer;
    private boolean cancelled;
    private boolean completed;
    private boolean delivered;

    GpuReadbackRequest(CommandEncoder encoder, GpuFormat format, GpuReadback.Region region, GpuBuffer buffer)
    {
        this.encoder = encoder;
        this.format = format;
        this.region = region;
        this.buffer = buffer;
    }

    /** Returns and transfers the CPU snapshot once ready; null means still pending. */
    public GpuReadback.Raw poll()
    {
        RenderSystem.assertOnRenderThread();
        this.checkUsable();
        if (this.consumer != null) throw new IllegalStateException("Readback belongs to its callback consumer");

        if (!this.completed && this.fence.awaitCompletion(0L)) this.finish();
        return this.take();
    }

    public GpuReadback.Raw await()
    {
        return this.await(10_000_000_000L);
    }

    public GpuReadback.Raw await(long timeoutNanos)
    {
        RenderSystem.assertOnRenderThread();
        this.checkUsable();
        if (this.consumer != null) throw new IllegalStateException("Readback belongs to its callback consumer");

        if (!this.completed)
        {
            GpuReadback.outsidePass(this.encoder);
            /* The fence belongs to the copy's submit. Advance that submit BEFORE waiting. */
            this.encoder.submit();

            if (!this.completed)
            {
                if (!this.fence.awaitCompletion(timeoutNanos))
                {
                    throw new IllegalStateException("GPU readback timed out for " + this.region);
                }

                this.finish();
            }
        }

        return this.take();
    }

    /** Consumer takes ownership. Completion is on the render thread, as in Vanilla Screenshot. */
    public void whenReady(Consumer<GpuReadback.Raw> consumer)
    {
        this.whenReady(consumer, error -> { throw error; });
    }

    public void whenReady(Consumer<GpuReadback.Raw> consumer, Consumer<RuntimeException> failureConsumer)
    {
        RenderSystem.assertOnRenderThread();
        if (this.cancelled || this.delivered) throw new IllegalStateException("Readback is cancelled or already consumed");
        if (this.consumer != null) throw new IllegalStateException("Readback already has a consumer");
        this.consumer = Objects.requireNonNull(consumer);
        this.failureConsumer = Objects.requireNonNull(failureConsumer);
        if (this.completed) this.dispatch();
    }

    private void checkUsable()
    {
        if (this.cancelled || this.delivered) throw new IllegalStateException("Readback is cancelled or already consumed");
        if (this.failure != null) throw this.failure;
    }

    private GpuReadback.Raw take()
    {
        if (this.failure != null) throw this.failure;
        if (!this.completed) return null;
        if (this.consumer != null) throw new IllegalStateException("Readback belongs to its callback consumer");
        GpuReadback.Raw value = this.result;
        this.result = null;
        this.delivered = true;
        return value;
    }

    private void finish()
    {
        if (this.completed) return;
        this.completed = true;

        try
        {
            if (!this.cancelled)
            {
                int bytes = GpuReadback.byteSize(this.format, this.region.width(), this.region.height());
                ByteBuffer snapshot = MemoryUtil.memAlloc(bytes).order(ByteOrder.nativeOrder());

                try
                {
                    try (mchorse.bbs_mod.compat.alt.MappedView mapped = this.buffer.map(0L, bytes, true, false))
                    {
                        snapshot.put(mapped.data().duplicate().clear().limit(bytes)).flip();
                    }

                    this.result = new GpuReadback.Raw(this.format, this.region.width(), this.region.height(), snapshot);
                }
                catch (Throwable error)
                {
                    MemoryUtil.memFree(snapshot);
                    throw error;
                }
            }
        }
        catch (Throwable error)
        {
            this.failure = new IllegalStateException("GPU readback failed for " + this.format + " " + this.region, error);
            System.err.println("[bbs/readback] " + this.failure.getMessage());
            this.failure.printStackTrace();
        }
        finally
        {
            this.releaseGpu();
        }

        this.dispatch();
    }

    private void dispatch()
    {
        if (this.consumer == null || this.cancelled) return;
        if (this.failure != null)
        {
            Consumer<RuntimeException> failed = this.failureConsumer;
            this.consumer = null;
            this.failureConsumer = null;
            this.delivered = true;
            failed.accept(this.failure);
            return;
        }
        Consumer<GpuReadback.Raw> ready = this.consumer;
        this.consumer = null;
        this.failureConsumer = null;
        GpuReadback.Raw value = this.take();
        try { ready.accept(value); }
        catch (RuntimeException | Error error) { value.close(); throw error; }
    }

    private void releaseGpu()
    {
        if (this.fence != null) { this.fence.close(); this.fence = null; }
        if (this.buffer != null) { this.buffer.close(); this.buffer = null; }
    }

    @Override
    public void close()
    {
        RenderSystem.assertOnRenderThread();
        this.cancelled = true;
        this.consumer = null;
        this.failureConsumer = null;

        if (!this.completed && this.fence != null && this.fence.awaitCompletion(0L)) this.finish();
        if (this.result != null) { this.result.close(); this.result = null; }
        /* Pending transfers are disposed by their native completion callback, never early. */
    }
    static GpuReadback.Request create(GpuDevice device, GpuTexture texture, int mip, GpuReadback.Region region)
    {
        RenderSystem.assertOnRenderThread();
        Objects.requireNonNull(texture);
        Objects.requireNonNull(region);
        if (texture.isClosed()) throw new IllegalStateException("Readback texture is closed");
        if ((texture.usage() & GpuTexture.USAGE_COPY_SRC) == 0) throw new IllegalArgumentException("Readback source requires USAGE_COPY_SRC");
        if (mip < 0 || mip >= texture.getMipLevels()) throw new IllegalArgumentException("Invalid readback mip " + mip);
        if (texture.getDepthOrLayers() != 1) throw new IllegalArgumentException("Readback requires a single texture layer");
        if (region.x() + region.width() > texture.getWidth(mip) || region.y() + region.height() > texture.getHeight(mip))
        {
            throw new IllegalArgumentException("Readback region exceeds its actual texture: " + region);
        }

        GpuFormat format = texture.bbsGpuFormat();
        if (format.hasDepthAspect() && format.hasStencilAspect())
        {
            throw new IllegalArgumentException("Mojang 26.3 transfer API cannot select one aspect of depth/stencil " + format);
        }

        CommandEncoder encoder = device.createCommandEncoder();
        GpuReadback.outsidePass(encoder);
        GpuBuffer buffer = device.createBuffer(() -> "bbs-readback", GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST,
            GpuReadback.byteSize(format, region.width(), region.height()));
        GpuReadback.Request result = new GpuReadback.Request(encoder, format, region, buffer);
        GpuReadbackRequest request = result;
        boolean queued = false;

        try
        {
            encoder.copyTextureToBuffer(texture, buffer, 0L, request::finish, mip,
                region.x(), region.y(), region.width(), region.height());
            queued = true;
            GpuFence fence = encoder.createFence();
            if (request.completed) fence.close();
            else request.fence = fence;
            return result;
        }
        catch (Throwable error)
        {
            request.cancelled = true;
            if (!queued) request.releaseGpu();
            throw error;
        }
    }
}
