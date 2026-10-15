package net.minecraft.client.renderer.block.model;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/** BBS-Shim fuer Minecraft vor 1.21.5: das Modell eines Blockzustands (1.21.5+), siehe BlockModelPart. */
public interface BlockStateModel
{
    void collectParts(RandomSource random, List<BlockModelPart> parts);

    default List<BlockModelPart> collectParts(RandomSource random)
    {
        List<BlockModelPart> teile = new ArrayList<>();

        this.collectParts(random, teile);

        return teile;
    }

    TextureAtlasSprite particleIcon();
}
