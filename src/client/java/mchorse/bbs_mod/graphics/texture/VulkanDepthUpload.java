package mchorse.bbs_mod.graphics.texture;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.vulkan.VulkanCommandEncoder;
import com.mojang.blaze3d.vulkan.VulkanGpuBuffer;
import com.mojang.blaze3d.vulkan.VulkanGpuTexture;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkBufferImageCopy;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.nio.ByteBuffer;

import static org.lwjgl.vulkan.VK10.*;

/** Mojang's ByteBuffer texture write uses COLOR unconditionally; this writes D32's DEPTH aspect. */
final class VulkanDepthUpload
{
    private VulkanDepthUpload() {}

    static void write(VulkanCommandEncoder encoder, VulkanGpuTexture texture, int level,
        int width, int height, ByteBuffer bytes)
    {
        /* The engine owns staging memory and its submission/fence lifetime. */
        GpuBufferSlice staging = encoder.transientMemory().uploadStaging(bytes, 4L, GpuBuffer.USAGE_COPY_SRC);
        if (!(staging.buffer() instanceof VulkanGpuBuffer buffer))
            throw new IllegalStateException("Vulkan depth staging is not a Vulkan GPU buffer");
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            VkCommandBuffer commands = encoder.allocateAndBeginTransientCommandBuffer();
            VulkanCommandEncoder.memoryBarrier(commands, stack);
            VkBufferImageCopy.Buffer region = VkBufferImageCopy.calloc(1, stack);
            region.bufferOffset(staging.offset()).bufferRowLength(width).bufferImageHeight(height);
            region.imageSubresource().aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT).mipLevel(level).baseArrayLayer(0).layerCount(1);
            region.imageOffset().set(0, 0, 0);
            region.imageExtent().set(width, height, 1);
            vkCmdCopyBufferToImage(commands, buffer.vkBuffer(), texture.vkImage(), VK_IMAGE_LAYOUT_GENERAL, region);
            VulkanCommandEncoder.memoryBarrier(commands, stack);
            int result = vkEndCommandBuffer(commands);
            if (result != VK_SUCCESS) throw new IllegalStateException("vkEndCommandBuffer(depth upload) failed: " + result);
            /* execute flushes object-init and earlier normal commands before this transfer. */
            encoder.execute(commands);
        }
    }
}
