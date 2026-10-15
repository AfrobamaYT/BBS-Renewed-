package mchorse.bbs_mod.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mchorse.bbs_mod.compat.gpu.GebackenesBlockmodell;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * ModelBlockRenderer.tesselateBlock mit Modellteilen (1.21.5+) vor 1.21.5: jeder Teil kommt aus
 * GebackenesBlockmodell und wird mit 1.21.4s Signatur und BlockState.getSeed gezeichnet, wie
 * Vanilla es fuer die Welt tut. Injiziert in ModelBlockRenderer.
 */
public interface ModelBlockRendererAlt
{
    default void tesselateBlock(BlockAndTintGetter welt, List<BlockModelPart> teile, BlockState zustand, BlockPos ort,
        PoseStack pose, VertexConsumer verbraucher, boolean verdecken, int overlay)
    {
        for (BlockModelPart teil : teile)
        {
            if (!(teil instanceof GebackenesBlockmodell.Teil gebacken))
            {
                throw new IllegalArgumentException("Modellteil ohne BakedModel: " + teil.getClass().getName());
            }

            ((ModelBlockRenderer) this).tesselateBlock(welt, gebacken.quelle().modell(), zustand, ort, pose, verbraucher,
                verdecken, RandomSource.create(), zustand.getSeed(ort), overlay);
        }
    }
}
