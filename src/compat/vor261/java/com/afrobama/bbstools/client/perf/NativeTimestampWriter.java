package com.afrobama.bbstools.client.perf;

import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import mchorse.bbs_mod.compat.CommandEncoderAlt;
import mchorse.bbs_mod.compat.alt.GpuQueryPool;

/**
 * Before 26.1 there is only OpenGL: this replaces the shared NativeTimestampWriter (excluded
 * there, build.gradle nurAb261) with its OpenGL half. GL's queryCounter overwrites its prior
 * timestamp, so preparing needs no reset; it is ordered by the GL command stream, inside a
 * pass or not.
 */
final class NativeTimestampWriter
{
    private NativeTimestampWriter()
    {}

    static void prepare(GpuDevice device, GpuQueryPool pool, TimestampPairs pairs)
    {
        if (((CommandEncoderAlt) device.createCommandEncoder()).isInRenderPass())
        {
            throw new IllegalStateException("GPU query preparation requires a closed render pass");
        }

        pairs.prepare(index -> {});
    }

    static void write(GpuDevice device, GpuQueryPool pool, int index)
    {
        pool.stempeln(index);
    }

    static void write(GpuDevice device, GpuQueryPool pool, int index, RenderPass explicit)
    {
        if (!((CommandEncoderAlt) device.createCommandEncoder()).isInRenderPass()) throw new IllegalStateException("Timestamp GL pass is not open");

        pool.stempeln(index);
    }

    static GpuFence captureFence(GpuDevice device)
    {
        return device.createCommandEncoder().createFence();
    }
}
