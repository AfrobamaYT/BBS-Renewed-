package com.afrobama.bbstools.client.perf;

import mchorse.bbs_mod.compat.alt.GpuQueryPool;
import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPassBackend;
import com.mojang.blaze3d.vulkan.VulkanCommandEncoder;
import com.mojang.blaze3d.vulkan.VulkanRenderPass;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import mchorse.bbs_mod.graphics.GpuBackend;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.lang.reflect.Field;

import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.KHRSynchronization2.vkCmdWriteTimestamp2KHR;
import static org.lwjgl.vulkan.VK13.VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT;

/** Vulkan's engine writeTimestamp host-resets queries; this records only actual GPU commands. */
final class NativeTimestampWriter
{
    private NativeTimestampWriter() {}

    private static final class NativePass
    {
        private static final Field CURRENT = field(VulkanCommandEncoder.class, "currentRenderPass", VulkanRenderPass.class);
        private static final Field COMMANDS = field(VulkanRenderPass.class, "commandBuffer", VkCommandBuffer.class);
        private static final Field BACKEND = field(RenderPass.class, "backend", RenderPassBackend.class);
        private static final Field CLOSED = field(RenderPass.class, "isClosed", boolean.class);

        private static Field field(Class<?> owner, String name, Class<?> type)
        {
            try
            {
                Field field = owner.getDeclaredField(name);
                if (field.getType() != type) throw new IllegalStateException("Native timestamp field changed: " + owner + "." + name);
                field.setAccessible(true);
                return field;
            }
            catch (ReflectiveOperationException error)
            {
                throw new IllegalStateException("Actual native timestamp pass contract changed", error);
            }
        }

        private static Object read(Field field, Object owner)
        {
            try { return field.get(owner); }
            catch (IllegalAccessException error) { throw new IllegalStateException("Cannot read actual native timestamp pass", error); }
        }

        private static VulkanRenderPass current(VulkanCommandEncoder encoder, boolean inPass)
        {
            Object value = read(CURRENT, encoder);
            if (inPass != (value instanceof VulkanRenderPass))
                throw new IllegalStateException("Frontend/native timestamp open-pass state disagrees");
            return (VulkanRenderPass) value;
        }

        private static VkCommandBuffer commands(VulkanRenderPass pass, RenderPass explicit)
        {
            if (explicit != null)
            {
                explicit = NativeTimestampPass.real(explicit);
                if (!(explicit instanceof RenderPass) || Boolean.TRUE.equals(read(CLOSED, explicit))
                    || read(BACKEND, explicit) != pass)
                    throw new IllegalStateException("Timestamp RenderPass is not the actual open native pass");
            }
            Object commands = read(COMMANDS, pass);
            if (!(commands instanceof VkCommandBuffer buffer)) throw new IllegalStateException("Actual timestamp command buffer is absent");
            return buffer;
        }
    }

    private static final class GlPass
    {
        private static final Field ENCODER = encoderField();

        private static Field encoderField()
        {
            try
            {
                Class<?> pass = Class.forName("com.mojang.blaze3d.opengl.GlRenderPass");
                Class<?> encoder = Class.forName("com.mojang.blaze3d.opengl.GlCommandEncoder");
                return NativePass.field(pass, "encoder", encoder);
            }
            catch (ClassNotFoundException error) { throw new IllegalStateException("Actual GL pass contract changed", error); }
        }

        private static void verify(CommandEncoder encoder, RenderPass explicit)
        {
            if (!GpuBackend.isOpenGl() || !encoder.backend().getClass().getName().equals("com.mojang.blaze3d.opengl.GlCommandEncoder"))
                throw new IllegalStateException("Unsupported actual timestamp backend");
            if (explicit == null) return;
            explicit = NativeTimestampPass.real(explicit);
            if (!(explicit instanceof RenderPass) || !encoder.isInRenderPass()
                || Boolean.TRUE.equals(NativePass.read(NativePass.CLOSED, explicit)))
                throw new IllegalStateException("Timestamp GL pass is not open");
            Object backend = NativePass.read(NativePass.BACKEND, explicit);
            if (!ENCODER.getDeclaringClass().isInstance(backend) || NativePass.read(ENCODER, backend) != encoder.backend())
                throw new IllegalStateException("Timestamp GL pass belongs to another encoder");
        }
    }

    private static CommandEncoder frontend(GpuDevice device)
    {
        if (!(device.createCommandEncoder() instanceof CommandEncoder encoder))
            throw new IllegalStateException("GPU timestamps require Minecraft's actual RenderPearl frontend");
        return encoder;
    }

    static void prepare(GpuDevice device, GpuQueryPool pool, TimestampPairs pairs)
    {
        CommandEncoder frontend = frontend(device);
        if (frontend.backend() instanceof VulkanCommandEncoder encoder)
        {
            NativePass.current(encoder, frontend.isInRenderPass());
            if (frontend.isInRenderPass()) throw new IllegalStateException("GPU query reset requires an actual pre-pass phase");
            if (!(pool instanceof NativeVulkanQueryPool nativePool)) throw new IllegalStateException("Native Vulkan timestamp pool required");
            VkCommandBuffer[] commands = new VkCommandBuffer[1];
            pairs.prepare(index ->
            {
                if (commands[0] == null) commands[0] = encoder.allocateAndBeginTransientCommandBuffer();
                vkCmdResetQueryPool(commands[0], nativePool.handle(), index, 2);
            });
            if (commands[0] != null) finish(encoder, commands[0]);
        }
        else
        {
            GlPass.verify(frontend, null);
            if (frontend.isInRenderPass()) throw new IllegalStateException("GPU query preparation requires a closed render pass");
            /* GL queryCounter overwrites its prior timestamp; no separate native reset exists. */
            pairs.prepare(index -> {});
        }
    }

    static void write(GpuDevice device, GpuQueryPool pool, int index)
    {
        write(device, pool, index, null);
    }

    static void write(GpuDevice device, GpuQueryPool pool, int index, RenderPass explicit)
    {
        CommandEncoder frontend = frontend(device);
        if (frontend.backend() instanceof VulkanCommandEncoder encoder)
        {
            if (!(pool instanceof NativeVulkanQueryPool nativePool)) throw new IllegalStateException("Native Vulkan timestamp pool required");
            VulkanRenderPass pass = NativePass.current(encoder, frontend.isInRenderPass());
            if (pass != null)
                vkCmdWriteTimestamp2KHR(NativePass.commands(pass, explicit), VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT, nativePool.handle(), index);
            else
            {
                if (explicit != null) throw new IllegalStateException("Explicit timestamp pass is not open");
                VkCommandBuffer commands = encoder.allocateAndBeginTransientCommandBuffer();
                vkCmdWriteTimestamp2KHR(commands, VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT, nativePool.handle(), index);
                finish(encoder, commands);
            }
        }
        else
        {
            GlPass.verify(frontend, explicit);
            frontend.backend().writeTimestamp(pool, index);
        }
    }

    static GpuFence captureFence(GpuDevice device)
    {
        CommandEncoder frontend = frontend(device);
        if (frontend.backend() instanceof VulkanCommandEncoder encoder)
            NativePass.current(encoder, frontend.isInRenderPass());
        else GlPass.verify(frontend, null);
        /* Both measured native constructors only capture the current submit index, even inside a pass. */
        return frontend.backend().createFence();
    }

    private static void finish(VulkanCommandEncoder encoder, VkCommandBuffer commands)
    {
        int code = vkEndCommandBuffer(commands);
        if (code != VK_SUCCESS) throw new IllegalStateException("Cannot finish native timestamp commands: " + code);
        encoder.execute(commands);
    }
}
