package mchorse.bbs_mod.compat.gpu;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * BlockStateModel von 1.21.5+ ueber 1.21.4s BakedModel. collectParts liefert genau einen Teil;
 * die Variantenwahl gewichteter Modelle passiert in 1.21.4 erst in getQuads, darum zieht der
 * Teil einen Startwert aus dem Zufall und saet ihn je Richtung neu, wie 1.21.4s
 * ModelBlockRenderer je Seite random.setSeed(seed) ruft. Die Variante entspricht damit nicht
 * zwingend der in der Welt (die nimmt BlockState.getSeed); ModelBlockRendererAlt nimmt diesen.
 */
public record GebackenesBlockmodell(BakedModel modell, BlockState zustand) implements BlockStateModel
{
    @Override
    public void collectParts(RandomSource random, List<BlockModelPart> parts)
    {
        parts.add(new Teil(this, random.nextLong()));
    }

    @Override
    public TextureAtlasSprite particleIcon()
    {
        return this.modell.getParticleIcon();
    }

    public record Teil(GebackenesBlockmodell quelle, long saat) implements BlockModelPart
    {
        @Override
        public List<BakedQuad> getQuads(Direction direction)
        {
            return this.quelle.modell.getQuads(this.quelle.zustand, direction, RandomSource.create(this.saat));
        }

        @Override
        public boolean useAmbientOcclusion()
        {
            return this.quelle.modell.useAmbientOcclusion();
        }

        @Override
        public TextureAtlasSprite particleIcon()
        {
            return this.quelle.modell.getParticleIcon();
        }
    }
}
