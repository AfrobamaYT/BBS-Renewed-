package com.mojang.blaze3d.textures;

/* BBS-Shim fuer Minecraft vor 1.21.5: diese Klasse gibt es dort nicht; sie bildet die oeffentliche
 * Form der 1.21.11-Fassung nach (eigene Implementierung, siehe docs/MEHRVERSION.md "vor1215"). */
public enum TextureFormat
{
    RGBA8(4), RED8(1), RED8I(1), DEPTH32(4);

    private final int pixelSize;

    TextureFormat(int pixelSize)
    {
        this.pixelSize = pixelSize;
    }

    public int pixelSize()
    {
        return this.pixelSize;
    }

    public boolean hasColorAspect()
    {
        return this != DEPTH32;
    }

    public boolean hasDepthAspect()
    {
        return this == DEPTH32;
    }
}
