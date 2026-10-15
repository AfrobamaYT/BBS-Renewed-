package com.mojang.blaze3d.opengl;

import com.mojang.blaze3d.textures.GpuTextureView;

/** BBS-Shim fuer Minecraft vor 1.21.5: Sicht auf eine GlTexture (Mip-Bereich). */
public class GlTextureView extends GpuTextureView
{
    private boolean geschlossen;

    public GlTextureView(GlTexture texture, int baseMipLevel, int mipLevels)
    {
        super(texture, baseMipLevel, mipLevels);
    }

    @Override
    public GlTexture texture()
    {
        return (GlTexture) super.texture();
    }

    public int glId()
    {
        return this.texture().id;
    }

    @Override
    public void close()
    {
        this.geschlossen = true;
    }

    @Override
    public boolean isClosed()
    {
        return this.geschlossen || this.texture().isClosed();
    }
}
