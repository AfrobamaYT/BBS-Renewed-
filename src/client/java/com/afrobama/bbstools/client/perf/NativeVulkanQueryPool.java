package com.afrobama.bbstools.client.perf;

import mchorse.bbs_mod.compat.alt.GpuQueryPool;
import com.mojang.blaze3d.vulkan.Destroyable;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkQueryPoolCreateInfo;

import java.nio.LongBuffer;
import java.util.OptionalLong;

import static org.lwjgl.vulkan.VK10.*;

/** No host reset: initialization and reuse are recorded by the actual engine command encoder. */
final class NativeVulkanQueryPool implements GpuQueryPool, Destroyable
{
    private final VulkanDevice owner;
    private final int count;
    private final long handle;
    private boolean closed;

    NativeVulkanQueryPool(VulkanDevice owner, int count)
    {
        this.owner = owner;
        this.count = count;
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            VkQueryPoolCreateInfo info = VkQueryPoolCreateInfo.calloc(stack).sType$Default()
                .queryType(VK_QUERY_TYPE_TIMESTAMP).queryCount(count);
            LongBuffer result = stack.callocLong(1);
            int code = vkCreateQueryPool(owner.vkDevice(), info, null, result);
            if (code != VK_SUCCESS) throw new IllegalStateException("Cannot create native timestamp pool: " + code);
            this.handle = result.get(0);
        }
    }

    long handle()
    {
        if (this.closed) throw new IllegalStateException("Native timestamp pool is closed");
        return this.handle;
    }

    @Override public int size() { return this.count; }

    @Override public OptionalLong getValue(int index) { return this.getValues(index, 1)[0]; }

    @Override
    public OptionalLong[] getValues(int index, int count)
    {
        if (index < 0 || count < 1 || index > this.count - count) throw new IndexOutOfBoundsException("Native query range");
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            LongBuffer result = stack.callocLong(count * 2);
            int code = vkGetQueryPoolResults(this.owner.vkDevice(), this.handle(), index, count, result, 16L,
                VK_QUERY_RESULT_64_BIT | VK_QUERY_RESULT_WITH_AVAILABILITY_BIT);
            if (code != VK_SUCCESS && code != VK_NOT_READY) throw new IllegalStateException("Cannot read native timestamps: " + code);
            OptionalLong[] values = new OptionalLong[count];
            for (int i = 0; i < count; i++)
                values[i] = result.get(i * 2 + 1) == 0L ? OptionalLong.empty() : OptionalLong.of(result.get(i * 2));
            return values;
        }
    }

    @Override
    public void close()
    {
        if (this.closed) return;
        this.closed = true;
        this.owner.createCommandEncoder().queueForDestroy(this);
    }

    @Override public void destroy() { vkDestroyQueryPool(this.owner.vkDevice(), this.handle, null); }
}
