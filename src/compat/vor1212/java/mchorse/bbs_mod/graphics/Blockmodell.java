package mchorse.bbs_mod.graphics;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Function;

/**
 * Vor 1.21.2 (ersetzt die vor1215-Fassung; Ebene mit Abschneide-Schalter wie 1.21.1): ein Blockzustand fuer sich, wie die vor261-Fassung,
 * aber mit 1.21.4s BakedModel und ModelBlockRenderer.renderModel(..., BlockState, BakedModel, ...).
 */
public final class Blockmodell
{
    /** Der Block des letzten {@link #ebeneFuer}-Aufrufs, gelesen von {@link #viereckeAusZustand}. */
    private static BlockState vorschau;

    private Blockmodell()
    {}

    public static void einreichen(BlockState block, PoseStack stapel, SubmitNodeCollector sammler, int licht, int overlay)
    {
        einreichen(block, stapel, sammler, null, licht, overlay);
    }

    public static void einreichen(BlockState block, PoseStack stapel, CustomVertexConsumerProvider anbieter, int licht, int overlay)
    {
        einreichen(block, stapel, anbieter.sammler(), anbieter, licht, overlay);
    }

    private static void einreichen(BlockState block, PoseStack stapel, SubmitNodeCollector sammler, CustomVertexConsumerProvider anbieter, int licht, int overlay)
    {
        if (!RenderSystem.isOnRenderThread())
        {
            throw new IllegalStateException("Blockmodell.einreichen laeuft nur im Renderthread");
        }

        if (block.getRenderShape() == RenderShape.INVISIBLE)
        {
            return;
        }

        int farbe = Minecraft.getInstance().getBlockColors().getColor(block, null, null, 0);
        int hell = hell(block, licht);
        Function<VertexConsumer, VertexConsumer> umfaerbung = anbieter == null ? (v) -> v : anbieter.umfaerbungEinfrieren();

        sammler.submitCustomGeometry(stapel, ItemBlockRenderTypes.getRenderType(block, true), (pose, verbraucher) ->
            zeichnen(block, farbe, pose, umfaerbung.apply(verbraucher), hell, overlay));
    }

    public static RenderType ebeneFuer(BlockState block)
    {
        if (block == null || !RenderSystem.isOnRenderThread())
        {
            return null;
        }

        vorschau = block;

        return ItemBlockRenderTypes.getRenderType(block, true);
    }

    public static boolean vierecke(BlockState block, PoseStack.Pose pose, VertexConsumer verbraucher, int licht, int overlay)
    {
        if (block == null || !RenderSystem.isOnRenderThread() || block.getRenderShape() == RenderShape.INVISIBLE)
        {
            return false;
        }

        zeichnen(block, Minecraft.getInstance().getBlockColors().getColor(block, null, null, 0), pose, verbraucher, hell(block, licht), overlay);

        return true;
    }

    public static boolean viereckeAusZustand(PoseStack.Pose pose, VertexConsumer verbraucher, int licht, int overlay)
    {
        return vierecke(vorschau, pose, verbraucher, licht, overlay);
    }

    private static void zeichnen(BlockState block, int farbe, PoseStack.Pose pose, VertexConsumer verbraucher, int licht, int overlay)
    {
        BakedModel modell = Minecraft.getInstance().getBlockRenderer().getBlockModel(block);

        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(pose, verbraucher, block, modell,
            (farbe >> 16 & 0xFF) / 255F, (farbe >> 8 & 0xFF) / 255F, (farbe & 0xFF) / 255F, licht, overlay);
    }

    private static int hell(BlockState block, int licht)
    {
        int eigen = block.emissiveRendering(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO) ? LightTexture.FULL_BRIGHT : LightTexture.pack(block.getLightEmission(), 0);

        return LightTexture.pack(Math.max(LightTexture.block(licht), LightTexture.block(eigen)), Math.max(LightTexture.sky(licht), LightTexture.sky(eigen)));
    }
}
