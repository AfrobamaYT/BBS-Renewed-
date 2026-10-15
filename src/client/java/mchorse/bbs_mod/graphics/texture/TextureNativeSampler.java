package mchorse.bbs_mod.graphics.texture;

import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.backend.opengl.GlSampler;
//? if >=26.1 {
import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import com.mojang.renderpearl.backend.vulkan.VulkanGpuSampler;
//?}
import mchorse.bbs_mod.graphics.GpuBackend;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL33;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkSamplerCreateInfo;

import java.nio.LongBuffer;
import java.util.OptionalDouble;

import static org.lwjgl.vulkan.VK10.*;

/** Concrete native samplers preserve modes absent from RenderPearl's public enums. */
final class TextureNativeSampler
{
    private TextureNativeSampler() {}

    static GpuSampler create(TextureSamplerState.Key key)
    {
        if (GpuBackend.isOpenGl()) return new Gl(key);
        //? if >=26.1 {
        return new Vk(GpuBackend.vulkanDevice(), key);
        //?} else {
        /*throw new IllegalStateException("Before Minecraft 26.1 there is only OpenGL");
        *///?}
    }

    static int mipMode(int min)
    {
        TextureSamplerState.filter(min);
        return min == GL11.GL_NEAREST_MIPMAP_LINEAR || min == GL11.GL_LINEAR_MIPMAP_LINEAR
            ? VK_SAMPLER_MIPMAP_MODE_LINEAR : VK_SAMPLER_MIPMAP_MODE_NEAREST;
    }

    static float nativeMaxLod(TextureSamplerState.Key key)
    {
        /* Zero selects MAG in Vulkan. Positive .25 selects MIN while nearest mip remains zero. */
        return key.lod() == 0 ? 0.25F : key.lod();
    }

    static int nativeMipMode(TextureSamplerState.Key key)
    {
        /* A logical maximum level zero must never blend level one, including trilinear MIN. */
        return key.lod() == 0 ? VK_SAMPLER_MIPMAP_MODE_NEAREST : mipMode(key.min());
    }

    static int nativeAddress(int wrap)
    {
        return switch (wrap)
        {
            case GL11.GL_REPEAT -> VK_SAMPLER_ADDRESS_MODE_REPEAT;
            case GL12.GL_CLAMP_TO_EDGE -> VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
            case GL13.GL_CLAMP_TO_BORDER -> VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_BORDER;
            case GL14.GL_MIRRORED_REPEAT -> VK_SAMPLER_ADDRESS_MODE_MIRRORED_REPEAT;
            default -> throw new IllegalArgumentException("Unknown texture wrap mode: " + wrap);
        };
    }

    private static AddressMode representable(int wrap)
    {
        if (wrap == GL11.GL_REPEAT) return AddressMode.REPEAT;
        if (wrap == GL12.GL_CLAMP_TO_EDGE) return AddressMode.CLAMP_TO_EDGE;
        throw new IllegalStateException("RenderPearl's AddressMode cannot represent native BBS mode " + wrap);
    }

    private static final class Gl extends GlSampler
    {
        private final TextureSamplerState.Key key;

        Gl(TextureSamplerState.Key key)
        {
            super(AddressMode.REPEAT, AddressMode.REPEAT, TextureSamplerState.filter(key.min()),
                TextureSamplerState.filter(key.mag()), key.anisotropy(), OptionalDouble.empty());
            this.key = key;
            /* GL texture MAX_LEVEL bounds mips. Sampler MAX_LOD=0 would incorrectly select MAG. */
            GL33.glSamplerParameteri(this.getId(), GL11.GL_TEXTURE_WRAP_S, key.s());
            GL33.glSamplerParameteri(this.getId(), GL11.GL_TEXTURE_WRAP_T, key.t());
            GL33.glSamplerParameteri(this.getId(), GL11.GL_TEXTURE_MIN_FILTER, key.min());
        }

        @Override public AddressMode getAddressModeU() { return representable(this.key.s()); }
        @Override public AddressMode getAddressModeV() { return representable(this.key.t()); }
    }

    //? if >=26.1 {
    private static final class Vk extends VulkanGpuSampler
    {
        private final VulkanDevice owner;
        private final TextureSamplerState.Key key;
        private final long handle;
        private boolean closed;
        private boolean destroyed;

        Vk(VulkanDevice owner, TextureSamplerState.Key key)
        {
            super(owner, AddressMode.REPEAT, AddressMode.REPEAT, TextureSamplerState.filter(key.min()),
                TextureSamplerState.filter(key.mag()), key.anisotropy(), OptionalDouble.of(key.lod()));
            this.owner = owner;
            this.key = key;
            /* The parent's unused handle has never been submitted. */
            super.destroy();
            try (MemoryStack stack = MemoryStack.stackPush())
            {
                VkSamplerCreateInfo info = VkSamplerCreateInfo.calloc(stack).sType$Default()
                    .addressModeU(nativeAddress(key.s())).addressModeV(nativeAddress(key.t()))
                    .addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .minFilter(TextureSamplerState.filter(key.min()) == FilterMode.LINEAR ? VK_FILTER_LINEAR : VK_FILTER_NEAREST)
                    .magFilter(TextureSamplerState.filter(key.mag()) == FilterMode.LINEAR ? VK_FILTER_LINEAR : VK_FILTER_NEAREST)
                    .mipmapMode(nativeMipMode(key)).minLod(0).maxLod(nativeMaxLod(key))
                    .anisotropyEnable(key.anisotropy() > 1).maxAnisotropy(key.anisotropy())
                    .borderColor(VK_BORDER_COLOR_FLOAT_TRANSPARENT_BLACK);
                LongBuffer nativeHandle = stack.mallocLong(1);
                int result = vkCreateSampler(owner.vkDevice(), info, null, nativeHandle);
                if (result != VK_SUCCESS) throw new IllegalStateException("vkCreateSampler(BBS) failed: " + result);
                this.handle = nativeHandle.get(0);
            }
        }

        @Override public AddressMode getAddressModeU() { return representable(this.key.s()); }
        @Override public AddressMode getAddressModeV() { return representable(this.key.t()); }
        @Override public long vkSampler() { return this.handle; }
        //? if >=26.3 {
        @Override
        //?}
        public boolean isClosed() { return this.closed; }
        @Override public void close()
        {
            if (!this.closed)
            {
                this.closed = true;
                this.owner.createCommandEncoder().queueForDestroy(this);
            }
        }
        @Override public void destroy()
        {
            if (!this.destroyed)
            {
                vkDestroySampler(this.owner.vkDevice(), this.handle, null);
                this.destroyed = true;
            }
        }
    }
    //?}
}
