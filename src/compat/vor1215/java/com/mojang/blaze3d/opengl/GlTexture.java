package com.mojang.blaze3d.opengl;

import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import mchorse.bbs_mod.compat.alt.GpuFormat;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;

import java.nio.ByteBuffer;

/**
 * BBS-Shim fuer Minecraft vor 1.21.5: GL-Textur der eigenen Schicht. Entweder selbst angelegt
 * (besitzt die GL-Textur) oder um eine vorhandene 1.21.4-Textur gelegt (AbstractTexture,
 * RenderTarget), dann gibt close() sie nicht frei.
 */
public class GlTexture extends GpuTexture
{
    public final int id;
    private final boolean besitzt;
    private boolean geschlossen;

    public GlTexture(int usage, String label, TextureFormat format, int width, int height, int depthOrLayers, int mipLevels)
    {
        super(usage, label, format, width, height, depthOrLayers, mipLevels);

        this.id = GlStateManager._genTexture();
        this.besitzt = true;

        GpuFormat gl = GpuFormat.von(format);
        boolean wuerfel = (usage & USAGE_CUBEMAP_COMPATIBLE) != 0;
        int ziel = wuerfel ? GL13.GL_TEXTURE_CUBE_MAP : GL11.GL_TEXTURE_2D;

        GL11.glBindTexture(ziel, this.id);

        for (int mip = 0; mip < mipLevels; mip++)
        {
            int w = Math.max(1, width >> mip);
            int h = Math.max(1, height >> mip);

            if (wuerfel)
            {
                for (int seite = 0; seite < 6; seite++)
                {
                    GL11.glTexImage2D(GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X + seite, mip, gl.glInternal, w, h, 0, gl.glExternal, gl.glType, (ByteBuffer) null);
                }
            }
            else
            {
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, mip, gl.glInternal, w, h, 0, gl.glExternal, gl.glType, (ByteBuffer) null);
            }
        }

        GL11.glTexParameteri(ziel, GL12.GL_TEXTURE_MAX_LEVEL, mipLevels - 1);
        GL11.glTexParameteri(ziel, GL12.GL_TEXTURE_BASE_LEVEL, 0);

        if (format.hasDepthAspect())
        {
            GL11.glTexParameteri(ziel, 34892, 0);
        }

        GL11.glBindTexture(ziel, 0);
        GlStateManager._bindTexture(0);
    }

    /** Um eine vorhandene GL-Textur gelegt, ohne sie zu besitzen. */
    public GlTexture(int id, String label, TextureFormat format, int width, int height, int mipLevels)
    {
        super(USAGE_TEXTURE_BINDING | USAGE_COPY_SRC | USAGE_COPY_DST | USAGE_RENDER_ATTACHMENT, label, format, width, height, 1, mipLevels);

        this.id = id;
        this.besitzt = false;
    }

    public int glId()
    {
        return this.id;
    }

    @Override
    public void close()
    {
        if (!this.geschlossen)
        {
            this.geschlossen = true;

            if (this.besitzt)
            {
                ((GlCommandEncoder) GlDevice.instanz().createCommandEncoder()).texturWeg(this);
                GlStateManager._deleteTexture(this.id);
            }
        }
    }

    @Override
    public boolean isClosed()
    {
        return this.geschlossen;
    }
}
