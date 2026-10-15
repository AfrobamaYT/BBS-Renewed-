package com.mcstudio.nano;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.compat.alt.GpuFormat;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vulkan.VulkanCommandEncoder;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanGpuTextureView;
import com.mojang.blaze3d.systems.CommandEncoder;
import org.lwjgl.nanovg.NanoVG;
import org.lwjgl.vulkan.VK;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.lang.reflect.Field;

/** Records the complete NanoVG renderer into Minecraft's Vulkan submission. */
final class NanoVkRenderer implements AutoCloseable
{
    private final VulkanCommandEncoder encoder;
    private final VulkanDevice device;
    private final NanoVkImages images;
    private final long context;
    private VkCommandBuffer command;
    private boolean closed;

    static boolean active()
    {
        return mchorse.bbs_mod.compat.Geraet.get().createCommandEncoder() instanceof CommandEncoder frontend
            && frontend.backend() instanceof VulkanCommandEncoder;
    }

    NanoVkRenderer()
    {
        this.encoder = (VulkanCommandEncoder) ((CommandEncoder)
            mchorse.bbs_mod.compat.Geraet.get().createCommandEncoder()).backend();

        try
        {
            Field owningDevice = VulkanCommandEncoder.class.getDeclaredField("device");
            owningDevice.setAccessible(true);
            this.device = (VulkanDevice) owningDevice.get(this.encoder);
        }
        catch (ReflectiveOperationException error)
        {
            throw new IllegalStateException("Minecraft Vulkan encoder no longer exposes its owning device", error);
        }

        this.images = new NanoVkImages(this);
        NanoVkNative.load();
        long created;

        try
        {
            long instanceProc = VK.getFunctionProvider().getFunctionAddress("vkGetInstanceProcAddr");
            long deviceProc = VK10.vkGetInstanceProcAddr(this.device.instance().vkInstance(), "vkGetDeviceProcAddr");
            created = NanoVkNative.create(this.images, this.device.instance().vkInstance().address(),
                this.device.vkDevice().getPhysicalDevice().address(),
                this.device.vkDevice().address(), instanceProc, deviceProc, NanoVkNative.core("nvgCreateInternal"),
                NanoVkNative.core("nvgDeleteInternal"), NanoVkNative.core("nvgInternalParams"),
                VK12.VK_FORMAT_R8G8B8A8_UNORM);

            if (created == 0L)
            {
                throw new IllegalStateException("Cannot create complete NanoVG Vulkan context");
            }
        }
        catch (RuntimeException | Error error)
        {
            this.images.close();
            throw error;
        }

        this.context = created;
    }

    long context()
    {
        return this.context;
    }

    VulkanDevice device()
    {
        return this.device;
    }

    VulkanCommandEncoder encoder()
    {
        return this.encoder;
    }

    void begin(GpuTextureView target, int width, int height)
    {
        if (this.closed || this.command != null || target.texture().bbsGpuFormat() != GpuFormat.RGBA8_UNORM
            || !(target instanceof VulkanGpuTextureView nativeView))
        {
            throw new IllegalStateException("Invalid NanoVG Vulkan target or overlapping frame");
        }

        this.command = this.encoder.allocateAndBeginTransientCommandBuffer();
        NanoVkNative.prepare(this.context, this.command.address(),
            nativeView.vkImageView(), width, height);
        NanoVG.nvgBeginFrame(this.context, width, height, 1F);
    }

    void end()
    {
        if (this.command == null)
        {
            throw new IllegalStateException("NanoVG Vulkan frame was not begun");
        }

        NanoVG.nvgEndFrame(this.context);
        long recording = NanoVkNative.takeRecording(this.context);
        VkCommandBuffer completed = this.command;
        this.command = null;

        if (recording == 0L)
        {
            throw new IllegalStateException("NanoVG Vulkan frame did not produce a recording");
        }

        int result = VK12.vkEndCommandBuffer(completed);

        if (result != VK12.VK_SUCCESS)
        {
            NanoVkNative.releaseRecording(this.context, recording);
            throw new IllegalStateException("NanoVG Vulkan command buffer failed: " + result);
        }

        try
        {
            this.encoder.execute(completed);
        }
        catch (RuntimeException error)
        {
            NanoVkNative.releaseRecording(this.context, recording);
            throw error;
        }

        this.encoder.queueForDestroy(() -> NanoVkNative.releaseRecording(this.context, recording));
    }

    void cancel()
    {
        if (this.command != null)
        {
            NanoVG.nvgCancelFrame(this.context);
            long recording = NanoVkNative.takeRecording(this.context);
            VK12.vkEndCommandBuffer(this.command);
            this.command = null;

            if (recording != 0L)
            {
                NanoVkNative.releaseRecording(this.context, recording);
            }
        }
    }

    @Override
    public void close()
    {
        if (this.closed)
        {
            return;
        }

        this.cancel();
        this.closed = true;
        /* Queue engine-owned image retirement before the native destruction callback,
         * so that callback never appends resources while the destruction queue drains. */
        this.images.close();
        this.encoder.queueForDestroy(() -> NanoVkNative.destroy(this.context));
    }
}
