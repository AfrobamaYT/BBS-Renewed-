package dev.vitrail.render.storage;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.vulkan.VulkanCommandEncoder;
import dev.vitrail.mixin.access.CommandEncoderAccessor;
import dev.vitrail.mixin.access.VulkanCommandEncoderAccessor;
import dev.vitrail.pack.model.BufferObject;
import mchorse.bbs_mod.graphics.GpuBackend;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Owned fragment list; reuses Vitrail's allocation and deferred destruction, never its pack registry. */
public final class BbsEmissionStorage implements AutoCloseable
{
    public static final String NAME = "BbsEmissionFragments";
    public static final int HEADER_WORDS = 8;
    public static final int NODE_WORDS = 12;
    private static final Field BINDINGS = field("bindings", Map.class);
    private static final Field PLACEHOLDER = field("dummy", GpuBuffer.class);
    private static BbsEmissionStorage current;
    private final StorageBuffers storage;
    private final Object device;
    private final long bytes;
    private final int width;
    private final int height;
    private final int capacity;

    private static Field field(String name, Class<?> type)
    {
        try
        {
            Field field = StorageBuffers.class.getDeclaredField(name);
            if (field.getType() != type) throw new IllegalStateException("Vitrail storage ABI changed: " + name);
            field.setAccessible(true);
            return field;
        }
        catch (ReflectiveOperationException error) { throw new IllegalStateException("Vitrail storage ABI changed", error); }
    }

    public static long byteSize(int width, int height, int capacity)
    {
        if (width < 1 || height < 1 || capacity < 1)
            throw new IllegalArgumentException("Emission fragment extent/capacity must be positive");
        long pixels = Math.multiplyExact((long) width, height);
        long words = Math.addExact(HEADER_WORDS, Math.addExact(pixels, Math.multiplyExact((long) capacity, NODE_WORDS)));
        if (words > Integer.MAX_VALUE) throw new IllegalArgumentException("Emission fragment index exceeds signed shader range");
        return Math.multiplyExact(words, Integer.BYTES);
    }

    /** Called outside any pass before recording; the old allocation is released through the GPU queue. */
    public static void prepare(int width, int height, int capacity)
    {
        RenderSystem.assertOnRenderThread();
        if (!GpuBackend.isVulkan()) throw new IllegalStateException("Vitrail emission storage requires Vulkan");
        if (current != null && current.device != RenderSystem.getDevice())
            throw new IllegalStateException("Emission storage was not released before device replacement");
        if (current != null && (current.width != width || current.height != height || current.capacity != capacity)) release();
        if (current == null) current = new BbsEmissionStorage(width, height, capacity);
        current.clear();
    }

    public static boolean named(String name) { return NAME.equals(name); }

    public static StorageBuffers.Bound bound(String name)
    {
        if (!named(name)) return null;
        if (current == null) throw new IllegalStateException("Emission storage is not prepared");
        return current.binding();
    }

    public static void bind(RenderPass pass)
    {
        if (current == null) throw new IllegalStateException("Emission storage is not prepared");
        try { pass.setUniform(NAME, ((GpuBuffer) PLACEHOLDER.get(current.storage)).slice()); }
        catch (IllegalAccessException error) { throw new IllegalStateException("Cannot bind owned emission storage", error); }
    }

    public static void release()
    {
        if (current != null)
        {
            BbsEmissionStorage owned = current;
            current = null;
            owned.close();
        }
    }

    private BbsEmissionStorage(int width, int height, int capacity)
    {
        this.width = width;
        this.height = height;
        this.capacity = capacity;
        this.bytes = byteSize(width, height, capacity);
        this.device = RenderSystem.getDevice();
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            VkPhysicalDeviceProperties properties = VkPhysicalDeviceProperties.calloc(stack);
            VK12.vkGetPhysicalDeviceProperties(GpuBackend.vulkanDevice().vkDevice().getPhysicalDevice(), properties);
            if (this.bytes > Integer.toUnsignedLong(properties.limits().maxStorageBufferRange()))
                throw new IllegalArgumentException("Emission fragment buffer exceeds actual device storage range");
        }
        this.storage = new StorageBuffers(new BufferObject.Reading(List.of(
            new BufferObject(0, this.bytes, false, 1F, 1F, Optional.of(NAME))), List.of()));
        try { this.storage.ensure(width, height); this.binding(); }
        catch (Throwable failure) { this.storage.close(); throw failure; }
    }

    @SuppressWarnings("unchecked")
    private StorageBuffers.Bound binding()
    {
        try
        {
            StorageBuffers.Bound bound = ((Map<Integer, StorageBuffers.Bound>) BINDINGS.get(this.storage)).get(0);
            if (bound == null || bound.buffer() == 0L || bound.range() != this.bytes)
                throw new IllegalStateException("Vitrail did not allocate the required emission storage");
            return bound;
        }
        catch (IllegalAccessException error) { throw new IllegalStateException("Cannot read owned emission allocation", error); }
    }

    private void clear()
    {
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        GpuRecording.endPass(encoder);
        if (!(((CommandEncoderAccessor) encoder).vitrail$backend() instanceof VulkanCommandEncoder vulkan))
            throw new IllegalStateException("Emission storage has no Vulkan recording encoder");
        VkCommandBuffer commands = ((VulkanCommandEncoderAccessor) vulkan).vitrail$commandBuffer();
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            GpuRecording.beforeTransfer(commands, stack);
            VK12.vkCmdFillBuffer(commands, this.binding().buffer(), 0L, this.bytes, 0);
            GpuRecording.betweenTransfers(commands, stack);
            VK12.vkCmdUpdateBuffer(commands, this.binding().buffer(), 0L,
                stack.ints(this.width, this.height, this.capacity, 0, 0, 0, 0, 0));
            GpuRecording.afterTransfer(commands, stack);
        }
    }

    @Override public void close()
    {
        RenderSystem.assertOnRenderThread();
        if (this.device != RenderSystem.getDevice())
            throw new IllegalStateException("Cannot destroy an emission buffer on a different device");
        this.storage.close();
    }
}
