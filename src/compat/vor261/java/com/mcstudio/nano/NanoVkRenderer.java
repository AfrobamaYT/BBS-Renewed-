package com.mcstudio.nano;

import com.mojang.blaze3d.textures.GpuTextureView;

/**
 * Before 26.1 Minecraft has only OpenGL, so NanoVG never takes the Vulkan path. Replaces the
 * shared NanoVkRenderer (excluded there, build.gradle nurAb261); NanoKontext asks
 * {@link #active()} and then uses NanoVGGL3.
 */
final class NanoVkRenderer implements AutoCloseable
{
    static boolean active()
    {
        return false;
    }

    NanoVkRenderer()
    {
        throw new IllegalStateException("NanoVG has no Vulkan path before Minecraft 26.1");
    }

    long context()
    {
        throw new IllegalStateException("NanoVG has no Vulkan path before Minecraft 26.1");
    }

    void begin(GpuTextureView target, int width, int height)
    {
        throw new IllegalStateException("NanoVG has no Vulkan path before Minecraft 26.1");
    }

    void end()
    {
        throw new IllegalStateException("NanoVG has no Vulkan path before Minecraft 26.1");
    }

    void cancel()
    {
        throw new IllegalStateException("NanoVG has no Vulkan path before Minecraft 26.1");
    }

    @Override
    public void close()
    {}
}
