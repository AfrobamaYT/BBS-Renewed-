package com.mcstudio.nano;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.compat.alt.GpuFormat;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vulkan.VulkanConst;
import com.mojang.blaze3d.vulkan.VulkanGpuSampler;
import com.mojang.blaze3d.vulkan.VulkanGpuTexture;
import com.mojang.blaze3d.vulkan.VulkanGpuTextureView;
import org.lwjgl.nanovg.NanoVG;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkFormatProperties;
import org.lwjgl.vulkan.VkImageBlit;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;

/** NanoVG atlas and icon uploads follow Minecraft's command ordering and retirement. */
final class NanoVkImages implements AutoCloseable
{
    private final NanoVkRenderer renderer;
    private final Map<Integer, Image> images = new HashMap<>();

    NanoVkImages(NanoVkRenderer renderer)
    {
        this.renderer = renderer;
    }

    /** Called by JNI while the NanoVG core creates its atlas or decodes an image. */
    long[] create(int id, int type, int width, int height, int flags, long pixels)
    {
        boolean rgba = type == 2;
        int components = rgba ? 4 : 1;
        boolean mipmaps = (flags & NanoVG.NVG_IMAGE_GENERATE_MIPMAPS) != 0;
        int levels = mipmaps ? 32 - Integer.numberOfLeadingZeros(Math.max(width, height)) : 1;
        GpuFormat format = rgba ? GpuFormat.RGBA8_UNORM : GpuFormat.R8_UNORM;
        int usage = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_TEXTURE_BINDING;
        GpuTexture texture = mchorse.bbs_mod.compat.Geraet.get().createTexture("bbs-nano-image-" + id,
            usage, format, width, height, 1, levels);
        GpuTextureView view = mchorse.bbs_mod.compat.Geraet.get().createTextureView(texture);
        FilterMode filter = (flags & NanoVG.NVG_IMAGE_NEAREST) != 0 ? FilterMode.NEAREST : FilterMode.LINEAR;
        AddressMode u = (flags & NanoVG.NVG_IMAGE_REPEATX) != 0 ? AddressMode.REPEAT : AddressMode.CLAMP_TO_EDGE;
        AddressMode v = (flags & NanoVG.NVG_IMAGE_REPEATY) != 0 ? AddressMode.REPEAT : AddressMode.CLAMP_TO_EDGE;
        GpuSampler sampler = mchorse.bbs_mod.compat.Geraet.get().createSampler(u, v, filter, filter,
            1, OptionalDouble.of(levels - 1));
        Image image = new Image(texture, view, sampler, components);
        Image previous = this.images.put(id, image);

        if (previous != null)
        {
            previous.close();
        }

        int bytes = Math.multiplyExact(Math.multiplyExact(width, height), components);
        ByteBuffer zero = pixels == 0L ? MemoryUtil.memCalloc(bytes) : null;

        try
        {
            ByteBuffer source = pixels == 0L ? zero : MemoryUtil.memByteBuffer(pixels, bytes);
            mchorse.bbs_mod.compat.Geraet.get().createCommandEncoder().writeToTexture(texture, source,
                0, 0, 0, 0, width, height);
            if (!(texture instanceof VulkanGpuTexture nativeTexture)
                || !(view instanceof VulkanGpuTextureView nativeView)
                || !(sampler instanceof VulkanGpuSampler nativeSampler))
            {
                throw new IllegalStateException("NanoVG received unexpected GPU resource classes: "
                    + texture.getClass().getName() + ", " + view.getClass().getName() + ", " + sampler.getClass().getName());
            }

            this.mipmaps(image);

            return new long[]
            {
                nativeTexture.vkImage(),
                nativeView.vkImageView(),
                nativeSampler.vkSampler()
            };
        }
        catch (RuntimeException error)
        {
            this.images.remove(id);
            image.close();
            throw error;
        }
        finally
        {
            if (zero != null)
            {
                MemoryUtil.memFree(zero);
            }
        }
    }

    /** NanoVG passes the full atlas pointer, while x/y/w/h name its changed rectangle. */
    void update(int id, int x, int y, int width, int height, long pixels)
    {
        Image image = this.required(id);
        int rowBytes = Math.multiplyExact(width, image.components());
        int sourceRow = Math.multiplyExact(image.texture().getWidth(0), image.components());
        ByteBuffer region = MemoryUtil.memAlloc(Math.multiplyExact(rowBytes, height));

        try
        {
            long target = MemoryUtil.memAddress(region);

            for (int row = 0; row < height; row++)
            {
                long source = pixels + (long) (y + row) * sourceRow + (long) x * image.components();
                MemoryUtil.memCopy(source, target + (long) row * rowBytes, rowBytes);
            }

            CommandEncoder encoder = mchorse.bbs_mod.compat.Geraet.get().createCommandEncoder();
            encoder.writeToTexture(image.texture(), region, 0, 0, x, y, width, height);
            this.mipmaps(image);
        }
        finally
        {
            MemoryUtil.memFree(region);
        }
    }

    void delete(int id)
    {
        Image image = this.images.remove(id);

        if (image != null)
        {
            image.close();
        }
    }

    private Image required(int id)
    {
        Image image = this.images.get(id);

        if (image == null)
        {
            throw new IllegalStateException("NanoVG Vulkan image does not exist: " + id);
        }

        return image;
    }

    /** Vulkan's GPU blit retains the requested NanoVG mipmaps without CPU rendering. */
    private void mipmaps(Image image)
    {
        GpuTexture texture = image.texture();

        if (texture.getMipLevels() == 1)
        {
            return;
        }

        try (MemoryStack stack = MemoryStack.stackPush())
        {
            VkFormatProperties properties = VkFormatProperties.calloc(stack);
            VK12.vkGetPhysicalDeviceFormatProperties(this.renderer.device().vkDevice().getPhysicalDevice(),
                VulkanConst.toVk(texture.bbsGpuFormat()), properties);
            int features = VK12.VK_FORMAT_FEATURE_BLIT_SRC_BIT | VK12.VK_FORMAT_FEATURE_BLIT_DST_BIT
                | VK12.VK_FORMAT_FEATURE_SAMPLED_IMAGE_FILTER_LINEAR_BIT;

            if ((properties.optimalTilingFeatures() & features) != features)
            {
                throw new IllegalStateException("GPU cannot generate linear NanoVG mipmaps for " + texture.bbsGpuFormat());
            }

            VkCommandBuffer command = this.renderer.encoder().allocateAndBeginTransientCommandBuffer();
            com.mojang.blaze3d.vulkan.VulkanCommandEncoder.memoryBarrier(command, stack);
            long handle = ((VulkanGpuTexture) texture).vkImage();
            VkImageBlit.Buffer blit = VkImageBlit.calloc(1, stack);

            for (int level = 1; level < texture.getMipLevels(); level++)
            {
                blit.srcSubresource().set(VK12.VK_IMAGE_ASPECT_COLOR_BIT, level - 1, 0, 1);
                blit.dstSubresource().set(VK12.VK_IMAGE_ASPECT_COLOR_BIT, level, 0, 1);
                blit.srcOffsets(0).set(0, 0, 0);
                blit.srcOffsets(1).set(texture.getWidth(level - 1), texture.getHeight(level - 1), 1);
                blit.dstOffsets(0).set(0, 0, 0);
                blit.dstOffsets(1).set(texture.getWidth(level), texture.getHeight(level), 1);
                VK12.vkCmdBlitImage(command, handle, VK12.VK_IMAGE_LAYOUT_GENERAL,
                    handle, VK12.VK_IMAGE_LAYOUT_GENERAL, blit, VK12.VK_FILTER_LINEAR);
                com.mojang.blaze3d.vulkan.VulkanCommandEncoder.memoryBarrier(command, stack);
            }

            int result = VK12.vkEndCommandBuffer(command);

            if (result != VK12.VK_SUCCESS)
            {
                throw new IllegalStateException("NanoVG mipmap command failed: " + result);
            }

            this.renderer.encoder().execute(command);
        }
    }

    @Override
    public void close()
    {
        this.images.values().forEach(Image::close);
        this.images.clear();
    }

    private record Image(GpuTexture texture, GpuTextureView view, GpuSampler sampler, int components)
    {
        void close()
        {
            this.view.close();
            this.texture.close();
            this.sampler.close();
        }
    }
}
