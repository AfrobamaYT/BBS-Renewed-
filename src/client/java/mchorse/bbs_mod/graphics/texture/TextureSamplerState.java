package mchorse.bbs_mod.graphics.texture;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.backend.opengl.GlTexture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.util.HashMap;
import java.util.Map;

/** Stable sampler configurations survive extraction until the owning texture is deleted. */
final class TextureSamplerState
{
    private int wrapS = GL11.GL_REPEAT;
    private int wrapT = GL11.GL_REPEAT;
    private int min = GL11.GL_NEAREST;
    private int mag = GL11.GL_NEAREST;
    private int maxLevel = 1000;
    private int anisotropy = 1;
    private final Map<Key, GpuSampler> samplers = new HashMap<>();

    record Key(int s, int t, int min, int mag, int anisotropy, int lod) {}

    int get(int parameter)
    {
        return switch (parameter)
        {
            case GL11.GL_TEXTURE_WRAP_S -> this.wrapS;
            case GL11.GL_TEXTURE_WRAP_T -> this.wrapT;
            case GL11.GL_TEXTURE_MIN_FILTER -> this.min;
            case GL11.GL_TEXTURE_MAG_FILTER -> this.mag;
            case GL12.GL_TEXTURE_MAX_LEVEL -> this.maxLevel;
            case 0x84FE -> this.anisotropy;
            default -> throw new IllegalArgumentException("Unsupported BBS texture parameter: " + parameter);
        };
    }

    void set(int parameter, int value)
    {
        switch (parameter)
        {
            case GL11.GL_TEXTURE_WRAP_S -> { TextureNativeSampler.nativeAddress(value); this.wrapS = value; }
            case GL11.GL_TEXTURE_WRAP_T -> { TextureNativeSampler.nativeAddress(value); this.wrapT = value; }
            case GL11.GL_TEXTURE_MIN_FILTER -> { filter(value); this.min = value; }
            case GL11.GL_TEXTURE_MAG_FILTER ->
            {
                if (value != GL11.GL_NEAREST && value != GL11.GL_LINEAR)
                    throw new IllegalArgumentException("Magnification does not accept a mip filter");
                this.mag = value;
            }
            case GL12.GL_TEXTURE_MAX_LEVEL ->
            {
                if (value < 0) throw new IllegalArgumentException("Negative maximum mip level");
                this.maxLevel = value;
            }
            case 0x84FE ->
            {
                validateAnisotropy(value, RenderSystem.getDevice().getDeviceInfo().limits().maxAnisotropy());
                this.anisotropy = value;
            }
            default -> throw new IllegalArgumentException("Unsupported BBS texture parameter: " + parameter);
        }
    }

    static void validateAnisotropy(int value, int maximum)
    {
        if (value < 1 || value > maximum)
            throw new IllegalArgumentException("Anisotropy must be between 1 and " + maximum);
    }

    Key key(boolean mipmap, int levels)
    {
        if (levels < 1) throw new IllegalArgumentException("Sampler needs an allocated mip level");
        int lod = mipmap && mipmapped(this.min) ? Math.min(this.maxLevel, levels - 1) : 0;
        return new Key(this.wrapS, this.wrapT, this.min, this.mag, this.anisotropy, lod);
    }

    GpuSampler sampler(boolean mipmap, int levels)
    {
        RenderSystem.assertOnRenderThread();
        Key key = this.key(mipmap, levels);
        return this.samplers.computeIfAbsent(key, TextureNativeSampler::create);
    }

    void applyLegacy(GpuTexture texture)
    {
        if (!(texture instanceof GlTexture gl)) return;
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, gl.glId());
        try
        {
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, this.wrapS);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, this.wrapT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, this.min);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, this.mag);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, Math.min(this.maxLevel, texture.getMipLevels() - 1));
            if (RenderSystem.getDevice().getDeviceInfo().limits().maxAnisotropy() > 1)
                GL11.glTexParameterf(GL11.GL_TEXTURE_2D, 0x84FE, this.anisotropy);
        }
        finally
        {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous);
        }
    }

    void close()
    {
        for (GpuSampler sampler : this.samplers.values()) sampler.close();
        this.samplers.clear();
    }

    static boolean mipmapped(int mode)
    {
        filter(mode);
        return mode != GL11.GL_NEAREST && mode != GL11.GL_LINEAR;
    }

    static FilterMode filter(int mode)
    {
        return switch (mode)
        {
            case GL11.GL_NEAREST, GL11.GL_NEAREST_MIPMAP_NEAREST, GL11.GL_NEAREST_MIPMAP_LINEAR -> FilterMode.NEAREST;
            case GL11.GL_LINEAR, GL11.GL_LINEAR_MIPMAP_NEAREST, GL11.GL_LINEAR_MIPMAP_LINEAR -> FilterMode.LINEAR;
            default -> throw new IllegalArgumentException("Unknown texture filter: " + mode);
        };
    }
}
