package net.minecraft.client.gui.render;

import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;

/* BBS-Shim fuer Minecraft vor 1.21.6: oeffentliche Form der 1.21.11-Fassung, eigene Implementierung
 * (siehe docs/MEHRVERSION.md "vor1215"). */
public record TextureSetup(GpuTextureView texure0, GpuTextureView texure1, GpuTextureView texure2,
    GpuSampler sampler0, GpuSampler sampler1, GpuSampler sampler2)
{
    private static final TextureSetup LEER = new TextureSetup(null, null, null, null, null, null);

    public static TextureSetup singleTexture(GpuTextureView view, GpuSampler sampler)
    {
        return new TextureSetup(view, null, null, sampler, null, null);
    }

    public static TextureSetup singleTextureWithLightmap(GpuTextureView view, GpuSampler sampler)
    {
        return new TextureSetup(view, null, Minecraft.getInstance().gameRenderer.lightmap(), sampler, null,
            mchorse.bbs_mod.compat.gpu.Vorgaben.samplerCache().getClampToEdge(com.mojang.blaze3d.textures.FilterMode.LINEAR));
    }

    public static TextureSetup doubleTexture(GpuTextureView view0, GpuSampler sampler0, GpuTextureView view1, GpuSampler sampler1)
    {
        return new TextureSetup(view0, view1, null, sampler0, sampler1, null);
    }

    public static TextureSetup noTexture()
    {
        return LEER;
    }

    public int getSortKey()
    {
        return this.hashCode();
    }

    public static void updateSortKeySeed()
    {}
}
