package com.mojang.blaze3d.textures;

import mchorse.bbs_mod.compat.GpuTextureAlt;

/* BBS-Shim fuer Minecraft vor 1.21.5: oeffentliche Form der 1.21.11-Fassung, eigene Implementierung
 * (siehe docs/MEHRVERSION.md "vor1215"). */
public abstract class GpuTexture implements AutoCloseable, GpuTextureAlt
{
    public static final int USAGE_COPY_DST = 1;
    public static final int USAGE_COPY_SRC = 2;
    public static final int USAGE_TEXTURE_BINDING = 4;
    public static final int USAGE_RENDER_ATTACHMENT = 8;
    public static final int USAGE_CUBEMAP_COMPATIBLE = 16;

    private final int usage;
    private final String label;
    private final TextureFormat format;
    private final int width;
    private final int height;
    private final int depthOrLayers;
    private final int mipLevels;

    public GpuTexture(int usage, String label, TextureFormat format, int width, int height, int depthOrLayers, int mipLevels)
    {
        this.usage = usage;
        this.label = label;
        this.format = format;
        this.width = width;
        this.height = height;
        this.depthOrLayers = depthOrLayers;
        this.mipLevels = mipLevels;
    }

    public int getWidth(int mip) { return Math.max(1, this.width >> mip); }
    public int getHeight(int mip) { return Math.max(1, this.height >> mip); }
    public int getDepthOrLayers() { return this.depthOrLayers; }
    public int getMipLevels() { return this.mipLevels; }
    public TextureFormat getFormat() { return this.format; }
    public int usage() { return this.usage; }
    public String getLabel() { return this.label; }

    @Override
    public abstract void close();

    public abstract boolean isClosed();
}
