package mchorse.bbs_mod.graphics;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Function;

/**
 * Before 26.1 (replaces the shared Blockmodell, build.gradle nurAb261): one block state drawn
 * on its own the way 1.21.11's own renderSingleBlock does it -- the block model of the
 * dispatcher, the item render type of ItemBlockRenderTypes, tint from BlockColors index 0, all
 * quads through ModelBlockRenderer.renderModel. Like the shared version it submits the quads
 * itself through submitCustomGeometry, so a BBS form's colour (CustomVertexConsumerProvider)
 * reaches the vertices, and raises the light to the block's own emission as 26.2's
 * BlockModelResolver does.
 */
public final class Blockmodell
{
    /** The block of the last {@link #ebeneFuer} call, read by {@link #viereckeAusZustand}. */
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

        BlockStateModel modell = Minecraft.getInstance().getBlockRenderer().getBlockModel(block);
        int farbe = Minecraft.getInstance().getBlockColors().getColor(block, null, null, 0);
        int hell = hell(block, licht);
        Function<VertexConsumer, VertexConsumer> umfaerbung = anbieter == null ? (v) -> v : anbieter.umfaerbungEinfrieren();

        sammler.submitCustomGeometry(stapel, ItemBlockRenderTypes.getRenderType(block), (pose, verbraucher) ->
            zeichnen(modell, farbe, pose, umfaerbung.apply(verbraucher), hell, overlay));
    }

    public static RenderType ebeneFuer(BlockState block)
    {
        if (block == null || !RenderSystem.isOnRenderThread())
        {
            return null;
        }

        vorschau = block;

        return ItemBlockRenderTypes.getRenderType(block);
    }

    public static boolean vierecke(BlockState block, PoseStack.Pose pose, VertexConsumer verbraucher, int licht, int overlay)
    {
        if (block == null || !RenderSystem.isOnRenderThread() || block.getRenderShape() == RenderShape.INVISIBLE)
        {
            return false;
        }

        BlockStateModel modell = Minecraft.getInstance().getBlockRenderer().getBlockModel(block);

        zeichnen(modell, Minecraft.getInstance().getBlockColors().getColor(block, null, null, 0), pose, verbraucher, hell(block, licht), overlay);

        return true;
    }

    public static boolean viereckeAusZustand(PoseStack.Pose pose, VertexConsumer verbraucher, int licht, int overlay)
    {
        return vierecke(vorschau, pose, verbraucher, licht, overlay);
    }

    private static void zeichnen(BlockStateModel modell, int farbe, PoseStack.Pose pose, VertexConsumer verbraucher, int licht, int overlay)
    {
        ModelBlockRenderer.renderModel(pose, verbraucher, modell,
            (farbe >> 16 & 0xFF) / 255F, (farbe >> 8 & 0xFF) / 255F, (farbe & 0xFF) / 255F, licht, overlay);
    }

    private static int hell(BlockState block, int licht)
    {
        int eigen = block.emissiveRendering(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO) ? LightTexture.FULL_BRIGHT : LightTexture.pack(block.getLightEmission(), 0);

        return LightTexture.pack(Math.max(LightTexture.block(licht), LightTexture.block(eigen)), Math.max(LightTexture.sky(licht), LightTexture.sky(eigen)));
    }
}
