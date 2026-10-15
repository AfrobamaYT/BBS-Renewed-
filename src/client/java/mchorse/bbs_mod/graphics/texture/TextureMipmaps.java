package mchorse.bbs_mod.graphics.texture;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.opengl.GlTexture;
//? if >=26.1 {
import com.mojang.blaze3d.vulkan.VulkanCommandEncoder;
import com.mojang.blaze3d.vulkan.VulkanConst;
import com.mojang.blaze3d.vulkan.VulkanGpuTexture;
//?}
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.textures.GpuTexture;
import mchorse.bbs_mod.graphics.GpuBackend;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
//? if >=26.1 {
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkFormatProperties;
import org.lwjgl.vulkan.VkImageBlit;

import static org.lwjgl.vulkan.VK10.*;
//?}

/** GPU mip generation on Minecraft's device and submission sequence. */
public final class TextureMipmaps
{
    private TextureMipmaps()
    {}

    public static int levels(int width, int height)
    {
        if (width < 1 || height < 1) throw new IllegalArgumentException("Invalid mip dimensions");
        return 32 - Integer.numberOfLeadingZeros(Math.max(width, height));
    }

    public static void generate(GpuTexture texture)
    {
        RenderSystem.assertOnRenderThread();
        CommandEncoder frontend = (CommandEncoder) RenderSystem.getDevice().createCommandEncoder();

        if (frontend.isInRenderPass())
        {
            throw new IllegalStateException("Close the render pass before generating BBS mipmaps");
        }
        if (texture.isClosed()) throw new IllegalStateException("Cannot generate mipmaps on a closed texture");
        int transfer = GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_COPY_DST;
        if (texture.getDepthOrLayers() != 1 || texture.getFormat().hasStencilAspect()
            || (texture.usage() & transfer) != transfer)
            throw new IllegalArgumentException("BBS mip generation requires one color/depth layer and COPY_SRC|COPY_DST");
        if (texture.getMipLevels() == 1) return;

        if (texture instanceof GlTexture gl)
        {
            int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, gl.glId());
            int baseLevel = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, org.lwjgl.opengl.GL12.GL_TEXTURE_BASE_LEVEL);
            int maximum = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, org.lwjgl.opengl.GL12.GL_TEXTURE_MAX_LEVEL);
            try
            {
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, org.lwjgl.opengl.GL12.GL_TEXTURE_BASE_LEVEL, 0);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, org.lwjgl.opengl.GL12.GL_TEXTURE_MAX_LEVEL, texture.getMipLevels() - 1);
                GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            }
            finally
            {
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, org.lwjgl.opengl.GL12.GL_TEXTURE_BASE_LEVEL, baseLevel);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, org.lwjgl.opengl.GL12.GL_TEXTURE_MAX_LEVEL, maximum);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous);
            }
            return;
        }

        //? if >=26.1 {
        if (!(texture instanceof VulkanGpuTexture image))
        {
            throw new IllegalStateException("Unsupported Minecraft texture backend: " + texture.getClass().getName());
        }

        try (MemoryStack stack = MemoryStack.stackPush())
        {
            boolean depth = !texture.getFormat().hasColorAspect();
            VkFormatProperties properties = VkFormatProperties.calloc(stack);
            vkGetPhysicalDeviceFormatProperties(GpuBackend.vulkanDevice().vkDevice().getPhysicalDevice(),
                VulkanConst.toVk(texture.getFormat()), properties);
            int required = VK_FORMAT_FEATURE_BLIT_SRC_BIT | VK_FORMAT_FEATURE_BLIT_DST_BIT;
            if (!depth) required |= VK_FORMAT_FEATURE_SAMPLED_IMAGE_FILTER_LINEAR_BIT;
            if ((properties.optimalTilingFeatures() & required) != required)
            {
                throw new IllegalStateException("The Vulkan device cannot generate GPU mipmaps for " + texture.getFormat());
            }

            VulkanCommandEncoder encoder = GpuBackend.vulkanEncoder();
            VkCommandBuffer commands = encoder.allocateAndBeginTransientCommandBuffer();
            VkImageBlit.Buffer blit = VkImageBlit.calloc(1, stack);
            int aspect = depth ? VK_IMAGE_ASPECT_DEPTH_BIT : VK_IMAGE_ASPECT_COLOR_BIT;

            /* Mojang keeps its images in GENERAL, also for native copy commands. */
            for (int level = 1; level < texture.getMipLevels(); level++)
            {
                VulkanCommandEncoder.memoryBarrier(commands, stack);
                blit.srcSubresource().aspectMask(aspect).mipLevel(level - 1).baseArrayLayer(0).layerCount(1);
                blit.dstSubresource().aspectMask(aspect).mipLevel(level).baseArrayLayer(0).layerCount(1);
                blit.srcOffsets(0).set(0, 0, 0);
                blit.srcOffsets(1).set(texture.getWidth(level - 1), texture.getHeight(level - 1), 1);
                blit.dstOffsets(0).set(0, 0, 0);
                blit.dstOffsets(1).set(texture.getWidth(level), texture.getHeight(level), 1);
                vkCmdBlitImage(commands, image.vkImage(), VK_IMAGE_LAYOUT_GENERAL,
                    image.vkImage(), VK_IMAGE_LAYOUT_GENERAL, blit, depth ? VK_FILTER_NEAREST : VK_FILTER_LINEAR);
            }

            VulkanCommandEncoder.memoryBarrier(commands, stack);
            int result = vkEndCommandBuffer(commands);
            if (result != VK_SUCCESS) throw new IllegalStateException("vkEndCommandBuffer(mipmaps) failed: " + result);
            encoder.execute(commands);
        }
        //?} else {
        /*throw new IllegalStateException("Unsupported Minecraft texture backend: " + texture.getClass().getName());
        *///?}
    }
}
