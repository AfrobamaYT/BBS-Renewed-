package com.mojang.blaze3d.opengl;

import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL33;

import java.util.OptionalDouble;

/** BBS-Shim fuer Minecraft vor 1.21.5: GL-Sampler-Objekt (GL 3.3), an eine Einheit gebunden. */
public class GlSampler extends GpuSampler
{
    public final int id;
    private final AddressMode u, v;
    private final FilterMode min, mag;
    private final int anisotropie;
    private final OptionalDouble maxLod;
    private boolean geschlossen;

    public GlSampler(AddressMode u, AddressMode v, FilterMode min, FilterMode mag, int anisotropie, OptionalDouble maxLod)
    {
        this.u = u;
        this.v = v;
        this.min = min;
        this.mag = mag;
        this.anisotropie = anisotropie;
        this.maxLod = maxLod;
        this.id = GL33.glGenSamplers();

        GL33.glSamplerParameteri(this.id, GL11.GL_TEXTURE_WRAP_S, wrap(u));
        GL33.glSamplerParameteri(this.id, GL11.GL_TEXTURE_WRAP_T, wrap(v));

        /* Wie Mojang ab 1.21.5: immer Mip-Filter, die Mip-Stufen begrenzt maxLod. */
        GL33.glSamplerParameteri(this.id, GL11.GL_TEXTURE_MIN_FILTER, min == FilterMode.LINEAR ? GL11.GL_LINEAR_MIPMAP_LINEAR : GL11.GL_NEAREST_MIPMAP_LINEAR);
        GL33.glSamplerParameteri(this.id, GL11.GL_TEXTURE_MAG_FILTER, mag == FilterMode.LINEAR ? GL11.GL_LINEAR : GL11.GL_NEAREST);

        if (maxLod.isPresent())
        {
            GL33.glSamplerParameterf(this.id, GL12.GL_TEXTURE_MAX_LOD, (float) maxLod.getAsDouble());
        }

        if (anisotropie > 1)
        {
            GL33.glSamplerParameterf(this.id, EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT, anisotropie);
        }
    }

    public int getId()
    {
        return this.id;
    }

    private static int wrap(AddressMode mode)
    {
        return mode == AddressMode.REPEAT ? GL11.GL_REPEAT : GL12.GL_CLAMP_TO_EDGE;
    }

    @Override public AddressMode getAddressModeU() { return this.u; }
    @Override public AddressMode getAddressModeV() { return this.v; }
    @Override public FilterMode getMinFilter() { return this.min; }
    @Override public FilterMode getMagFilter() { return this.mag; }
    @Override public int getMaxAnisotropy() { return this.anisotropie; }
    @Override public OptionalDouble getMaxLod() { return this.maxLod; }

    @Override
    public void close()
    {
        if (!this.geschlossen)
        {
            this.geschlossen = true;
            GL33.glDeleteSamplers(this.id);
        }
    }
}
