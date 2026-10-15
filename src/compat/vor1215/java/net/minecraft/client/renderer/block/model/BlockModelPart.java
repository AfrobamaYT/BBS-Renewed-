package net.minecraft.client.renderer.block.model;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;

import java.util.List;

/**
 * BBS-Shim fuer Minecraft vor 1.21.5: ein gewaehlter Teil eines Blockmodells (1.21.5+).
 * Umsetzung: mchorse.bbs_mod.compat.gpu.GebackenesBlockmodell ueber 1.21.4s BakedModel.
 */
public interface BlockModelPart
{
    List<BakedQuad> getQuads(Direction direction);

    boolean useAmbientOcclusion();

    TextureAtlasSprite particleIcon();
}
