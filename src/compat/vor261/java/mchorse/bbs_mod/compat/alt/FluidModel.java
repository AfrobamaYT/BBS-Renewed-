package mchorse.bbs_mod.compat.alt;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * 26.2's FluidModel (still, flowing, optional overlay sprite and tint), for the versions before
 * 26.1, where Fabric's FluidRenderHandler holds them ({@link FluidStateModelSet}).
 */
public record FluidModel(Material stillMaterial, Material flowingMaterial, Material overlayMaterial, BlockTintSource tintSource)
{
    public record Material(TextureAtlasSprite sprite)
    {}
}
