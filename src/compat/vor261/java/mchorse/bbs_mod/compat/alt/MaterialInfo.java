package mchorse.bbs_mod.compat.alt;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/** The part of 26.2's BakedQuad.MaterialInfo BBS reads; before 26.1 the quad carries these itself. */
public record MaterialInfo(TextureAtlasSprite sprite, int tintIndex, boolean shade, int lightEmission)
{
    public boolean isTinted()
    {
        return this.tintIndex != -1;
    }
}
