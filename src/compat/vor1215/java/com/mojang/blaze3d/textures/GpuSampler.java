package com.mojang.blaze3d.textures;

import java.util.OptionalDouble;

/* BBS-Shim fuer Minecraft vor 1.21.5: oeffentliche Form der 1.21.11-Fassung, eigene Implementierung
 * (siehe docs/MEHRVERSION.md "vor1215"). */
public abstract class GpuSampler implements AutoCloseable
{
    public abstract AddressMode getAddressModeU();
    public abstract AddressMode getAddressModeV();
    public abstract FilterMode getMinFilter();
    public abstract FilterMode getMagFilter();
    public abstract int getMaxAnisotropy();
    public abstract OptionalDouble getMaxLod();

    @Override
    public abstract void close();
}
