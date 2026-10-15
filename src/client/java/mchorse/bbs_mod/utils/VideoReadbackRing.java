package mchorse.bbs_mod.utils;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.textures.GpuTexture;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Future;

/**
 * Raw RGBA export readback through a fixed ring of mapped buffers, as BBS 1.21.1 did with its
 * PBOs: no buffer allocation and no CPU copy per frame. The pipe write to ffmpeg runs on its own
 * thread straight from the mapped memory, so the render thread draws the next frame meanwhile.
 *
 * Measured 2026-10-14 (film c1, 4K60, same machine load): 26.3 exported at 15 fps through the
 * generic GpuReadback path (new 33 MB buffer and one more copy per frame), 1.21.1 at 35 fps; with
 * the ring alone 25 fps, and 38 % of the render thread then sat blocked in the pipe write.
 *
 * A class of its own so VideoRecorder keeps its fields and swaps into a running game.
 */
public final class VideoReadbackRing
{
    private static final int SLOTS = 4;
    private static final long WAIT_NANOS = 10_000_000_000L;
    private static final Map<VideoRecorder, VideoReadbackRing> RINGS = Collections.synchronizedMap(new WeakHashMap<>());

    private final GpuBuffer[] buffers = new GpuBuffer[SLOTS];
    private final GpuFence[] fences = new GpuFence[SLOTS];
    private final CommandEncoder[] encoders = new CommandEncoder[SLOTS];
    private final mchorse.bbs_mod.compat.alt.MappedView[] views = new mchorse.bbs_mod.compat.alt.MappedView[SLOTS];
    private final Future<?>[] writes = new Future<?>[SLOTS];
    /* Slots in issue order, from the copy until their pipe write is done. */
    private final Deque<Integer> queued = new ArrayDeque<>();
    private final VideoPipeWriter writer = new VideoPipeWriter();
    private final long bytes;
    private int next;

    public static VideoReadbackRing get(VideoRecorder recorder)
    {
        return RINGS.get(recorder);
    }

    /** Only for RGBA8 textures whose bytes go to ffmpeg unchanged (see VideoRohRgba). */
    public static VideoReadbackRing start(VideoRecorder recorder, GpuTexture texture, int width, int height)
    {
        close(recorder);

        VideoReadbackRing ring = new VideoReadbackRing(mchorse.bbs_mod.compat.Geraet.get(), (long) width * height * 4L);

        RINGS.put(recorder, ring);

        return ring;
    }

    public static void close(VideoRecorder recorder)
    {
        VideoReadbackRing ring = RINGS.remove(recorder);

        if (ring != null)
        {
            ring.release();
        }
    }

    private VideoReadbackRing(GpuDevice device, long bytes)
    {
        this.bytes = bytes;

        for (int i = 0; i < SLOTS; i++)
        {
            this.buffers[i] = device.createBuffer(() -> "bbs-video-readback", mchorse.bbs_mod.compat.gpu.PufferNutzung.USAGE_MAP_READ | mchorse.bbs_mod.compat.gpu.PufferNutzung.USAGE_COPY_DST, bytes);
        }
    }

    /**
     * Queue a copy of the texture into the next slot, waiting only when the whole ring is still
     * busy. Returns the number of frames fully written to the channel.
     */
    public int record(GpuTexture texture, int width, int height, WritableByteChannel channel) throws IOException
    {
        RenderSystem.assertOnRenderThread();

        int written = this.pump(channel);

        while (this.queued.size() >= SLOTS)
        {
            written += this.finishOldest(channel);
        }

        int slot = this.next;
        CommandEncoder encoder = mchorse.bbs_mod.compat.Geraet.get().createCommandEncoder();

        this.next = (this.next + 1) % SLOTS;
        encoder.copyTextureToBuffer(texture, this.buffers[slot], 0L, () -> {}, 0, 0, 0, width, height);
        this.fences[slot] = encoder.createFence();
        this.encoders[slot] = encoder;
        this.queued.addLast(slot);

        return written;
    }

    /** Hand finished copies to the pipe thread; with wait, finish every queued frame. */
    public int drain(boolean wait, WritableByteChannel channel) throws IOException
    {
        int written = this.pump(channel);

        while (wait && !this.queued.isEmpty())
        {
            written += this.finishOldest(channel);
        }

        return written;
    }

    /** Non-blocking pass in issue order: start writes for finished copies, free written slots. */
    private int pump(WritableByteChannel channel)
    {
        int written = 0;

        for (int slot : this.queued)
        {
            if (this.fences[slot] == null)
            {
                continue;
            }

            if (!this.fences[slot].awaitCompletion(0L))
            {
                /* Writes leave in issue order: a later copy waits for this one. */
                break;
            }

            this.startWrite(slot, channel);
        }

        while (!this.queued.isEmpty())
        {
            int slot = this.queued.peekFirst();

            if (this.writes[slot] == null || !this.writes[slot].isDone())
            {
                break;
            }

            written += this.closeWrite(slot);
        }

        return written;
    }

    /** Blocking: the oldest slot's copy, then its pipe write. */
    private int finishOldest(WritableByteChannel channel) throws IOException
    {
        int slot = this.queued.peekFirst();

        if (this.fences[slot] != null)
        {
            if (!this.fences[slot].awaitCompletion(0L))
            {
                /* The fence belongs to the copy's submit. Advance that submit BEFORE waiting. */
                this.encoders[slot].submit();

                if (!this.fences[slot].awaitCompletion(WAIT_NANOS))
                {
                    throw new IllegalStateException("Video readback timed out");
                }
            }

            this.startWrite(slot, channel);
        }

        VideoPipeWriter.await(this.writes[slot], WAIT_NANOS);

        return this.closeWrite(slot) + this.pump(channel);
    }

    private void startWrite(int slot, WritableByteChannel channel)
    {
        this.fences[slot].close();
        this.fences[slot] = null;
        this.encoders[slot] = null;

        mchorse.bbs_mod.compat.alt.MappedView view = this.buffers[slot].map(0L, this.bytes, true, false);
        ByteBuffer data = view.data().duplicate().clear().limit((int) this.bytes);

        this.views[slot] = view;
        this.writes[slot] = this.writer.write(data, channel);
    }

    /** A done write: unmap on the render thread, free the slot, report a failed write loudly. */
    private int closeWrite(int slot)
    {
        Future<?> write = this.writes[slot];

        this.writes[slot] = null;
        this.views[slot].close();
        this.views[slot] = null;
        this.queued.removeFirstOccurrence(slot);
        VideoPipeWriter.await(write, WAIT_NANOS);

        return 1;
    }

    private void release()
    {
        this.writer.close();

        for (int i = 0; i < SLOTS; i++)
        {
            if (this.fences[i] != null)
            {
                /* A copy still in flight owns its buffer until the GPU is done with it. */
                this.fences[i].awaitCompletion(WAIT_NANOS);
                this.fences[i].close();
                this.fences[i] = null;
            }

            if (this.views[i] != null)
            {
                this.views[i].close();
                this.views[i] = null;
            }

            if (this.buffers[i] != null)
            {
                this.buffers[i].close();
                this.buffers[i] = null;
            }
        }

        this.queued.clear();
    }
}
