package com.afrobama.bbstools.client.perf;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.compat.alt.GpuQueryPool;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.GpuDevice;
import mchorse.bbs_mod.graphics.GpuBackend;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkQueueFamilyProperties;

import java.nio.IntBuffer;
import java.util.List;
import java.util.ArrayList;

/** Actual GPU query ticks, queue precision and device period; no CPU timing fallback. */
public final class NativeGpuTimer implements AutoCloseable
{
    public record Sample(long sequence, long microseconds) {}

    private static final List<NativeGpuTimer> LIVE = new ArrayList<>();
    private final GpuDevice device;
    private final GpuQueryPool pool;
    private final TimestampPairs pairs;
    private boolean closed;

    public NativeGpuTimer(int pairCount)
    {
        RenderSystem.assertOnRenderThread();
        if (pairCount < 1 || pairCount > 64) throw new IllegalArgumentException("GPU timer pair count must be 1..64");
        this.device = RenderSystem.getDevice();
        int bits = validBits();
        float period = this.device.getDeviceInfo().timestampPeriod();
        TimestampPairs.validateClock(bits, period);
        int queryCount = Math.multiplyExact(pairCount, 2);
        //? if >=26.1 {
        /*this.pool = GpuBackend.isOpenGl() ? this.device.createTimestampQueryPool(queryCount)
            : new NativeVulkanQueryPool(GpuBackend.vulkanDevice(), queryCount);
        *///?} else {
        this.pool = this.device.createTimestampQueryPool(queryCount);
        //?}
        this.pairs = new TimestampPairs(this.pool, pairCount, bits, period);
        LIVE.add(this);
    }

    /** Called by END_EXTRACTION before terrain opens its native pass. */
    public static void prepareAll()
    {
        RenderSystem.assertOnRenderThread();
        for (NativeGpuTimer timer : List.copyOf(LIVE)) timer.prepare();
    }

    private static int validBits()
    {
        if (GpuBackend.isOpenGl())
            return org.lwjgl.opengl.GL15C.glGetQueryi(org.lwjgl.opengl.GL33C.GL_TIMESTAMP, org.lwjgl.opengl.GL15C.GL_QUERY_COUNTER_BITS);
        //? if >=26.1 {
        /*com.mojang.blaze3d.vulkan.VulkanDevice device = GpuBackend.vulkanDevice();
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            IntBuffer count = stack.callocInt(1);
            VK10.vkGetPhysicalDeviceQueueFamilyProperties(device.vkDevice().getPhysicalDevice(), count, null);
            VkQueueFamilyProperties.Buffer families = VkQueueFamilyProperties.calloc(count.get(0), stack);
            VK10.vkGetPhysicalDeviceQueueFamilyProperties(device.vkDevice().getPhysicalDevice(), count, families);
            int family = device.graphicsQueue().queueFamilyIndex();
            if (family < 0 || family >= families.remaining()) throw new IllegalStateException("Actual graphics queue family is invalid");
            return families.get(family).timestampValidBits();
        }
        *///?} else {
        throw new IllegalStateException("Before Minecraft 26.1 there is only OpenGL");
        //?}
    }

    private void requireOpen()
    {
        RenderSystem.assertOnRenderThread();
        if (this.closed) throw new IllegalStateException("Native GPU timer is closed");
        if (RenderSystem.getDevice() != this.device) throw new IllegalStateException("Native GPU timer belongs to another device");
    }

    public List<Sample> collect()
    {
        this.requireOpen();
        return this.pairs.collect();
    }

    /** A timer constructed inside a pass cannot begin until an actual pre-pass preparation. */
    public void prepare()
    {
        this.requireOpen();
        NativeTimestampWriter.prepare(this.device, this.pool, this.pairs);
    }

    public boolean begin()
    {
        this.requireOpen();
        return this.pairs.begin(index -> NativeTimestampWriter.write(this.device, this.pool, index));
    }

    public void end()
    {
        this.requireOpen();
        this.pairs.end(index -> NativeTimestampWriter.write(this.device, this.pool, index),
            () -> NativeTimestampWriter.captureFence(this.device));
    }

    public boolean begin(RenderPass pass)
    {
        this.requireOpen();
        java.util.Objects.requireNonNull(pass, "Timestamp render pass");
        return this.pairs.begin(index -> NativeTimestampWriter.write(this.device, this.pool, index, pass));
    }

    public void end(RenderPass pass)
    {
        this.requireOpen();
        java.util.Objects.requireNonNull(pass, "Timestamp render pass");
        this.pairs.end(index -> NativeTimestampWriter.write(this.device, this.pool, index, pass),
            () -> NativeTimestampWriter.captureFence(this.device));
    }

    public static long elapsedTicks(long first, long last, int bits)
    {
        return TimestampPairs.elapsedTicks(first, last, bits);
    }

    @Override
    public void close()
    {
        if (!this.closed)
        {
            this.pairs.close();
            this.pool.close();
            LIVE.remove(this);
            this.closed = true;
        }
    }
}
