package mchorse.bbs_mod.graphics;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.backend.vulkan.VulkanCommandEncoder;
import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import com.mojang.renderpearl.frontend.FrontendCommandEncoder;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL31;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;

import java.lang.reflect.Field;

/** Access to the actual Minecraft device; never creates another graphics device. */
public final class GpuBackend
{
    private GpuBackend()
    {}

    private static Object backend()
    {
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();

        if (!(encoder instanceof FrontendCommandEncoder frontend))
        {
            throw new IllegalStateException("BBS requires Minecraft's RenderPearl command encoder");
        }

        return frontend.backend();
    }

    public static boolean isOpenGl()
    {
        /* Mojang's concrete GL encoder is package-private in 26.3. */
        return backend().getClass().getName().equals("com.mojang.renderpearl.backend.opengl.GlCommandEncoder");
    }

    public static boolean isVulkan()
    {
        return backend() instanceof VulkanCommandEncoder;
    }

    public static VulkanCommandEncoder vulkanEncoder()
    {
        if (backend() instanceof VulkanCommandEncoder encoder)
        {
            return encoder;
        }

        throw new IllegalStateException("The active BBS graphics backend is not Vulkan");
    }

    /** Minecraft owns this device and all command ordering associated with it. */
    public static VulkanDevice vulkanDevice()
    {
        try
        {
            Field field = VulkanCommandEncoder.class.getDeclaredField("device");
            field.setAccessible(true);

            return (VulkanDevice) field.get(vulkanEncoder());
        }
        catch (ReflectiveOperationException error)
        {
            throw new IllegalStateException("Minecraft's Vulkan encoder device contract changed", error);
        }
    }

    /** RenderPearl DeviceLimits does not expose the uniform-buffer range. */
    public static int maxUniformBufferRange()
    {
        RenderSystem.assertOnRenderThread();

        if (isOpenGl())
        {
            return GL11.glGetInteger(GL31.GL_MAX_UNIFORM_BLOCK_SIZE);
        }

        try (MemoryStack stack = MemoryStack.stackPush())
        {
            VkPhysicalDeviceProperties properties = VkPhysicalDeviceProperties.calloc(stack);
            VK10.vkGetPhysicalDeviceProperties(vulkanDevice().vkDevice().getPhysicalDevice(), properties);

            return properties.limits().maxUniformBufferRange();
        }
    }
}
