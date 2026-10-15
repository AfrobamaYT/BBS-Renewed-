package com.mojang.blaze3d.textures;

/* BBS-Shim fuer Minecraft vor 1.21.5: oeffentliche Form der 1.21.11-Fassung, eigene Implementierung
 * (siehe docs/MEHRVERSION.md "vor1215"). */
public abstract class GpuTextureView implements AutoCloseable
{
    private final GpuTexture texture;
    private final int baseMipLevel;
    private final int mipLevels;

    public GpuTextureView(GpuTexture texture, int baseMipLevel, int mipLevels)
    {
        this.texture = texture;
        this.baseMipLevel = baseMipLevel;
        this.mipLevels = mipLevels;
    }

    @Override
    public abstract void close();

    public GpuTexture texture() { return this.texture; }
    public int baseMipLevel() { return this.baseMipLevel; }
    public int mipLevels() { return this.mipLevels; }
    public int getWidth(int mip) { return this.texture.getWidth(this.baseMipLevel + mip); }
    public int getHeight(int mip) { return this.texture.getHeight(this.baseMipLevel + mip); }

    public abstract boolean isClosed();
}
