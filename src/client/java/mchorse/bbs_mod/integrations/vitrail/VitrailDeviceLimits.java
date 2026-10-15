package mchorse.bbs_mod.integrations.vitrail;

import mchorse.bbs_mod.graphics.GpuBackend;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkFormatProperties;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;

/** Read the physical device Minecraft already owns; no assumed texel-buffer limits. */
public record VitrailDeviceLimits(long texelAlignment, long maximumTexels,
    long uniformAlignment, long maximumUniformBytes)
{
    public static VitrailDeviceLimits read()
    {
        //? if >=26.1 {
        /*try (MemoryStack stack = MemoryStack.stackPush())
        {
            var physical = GpuBackend.vulkanDevice().vkDevice().getPhysicalDevice();
            var properties = VkPhysicalDeviceProperties.calloc(stack);
            VK10.vkGetPhysicalDeviceProperties(physical, properties);
            var limits = properties.limits();
            for (int format : new int[] {VK10.VK_FORMAT_R32_SFLOAT, VK10.VK_FORMAT_R32_UINT})
            {
                var supported = VkFormatProperties.calloc(stack);
                VK10.vkGetPhysicalDeviceFormatProperties(physical, format, supported);
                if ((supported.bufferFeatures() & VK10.VK_FORMAT_FEATURE_UNIFORM_TEXEL_BUFFER_BIT) == 0)
                    throw new IllegalStateException("BBS Vitrail device cannot fetch native R32 texel buffers: " + format);
            }
            return new VitrailDeviceLimits(limits.minTexelBufferOffsetAlignment(),
                Integer.toUnsignedLong(limits.maxTexelBufferElements()), limits.minUniformBufferOffsetAlignment(),
                Integer.toUnsignedLong(limits.maxUniformBufferRange()));
        }
        *///?} else {
        throw new IllegalStateException("Vitrail runs on Vulkan, which Minecraft has only from 26.1 on");
        //?}
    }
}
