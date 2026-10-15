package mchorse.bbs_mod.compat.alt;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import mchorse.bbs_mod.compat.vertex.BauerAlt;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;

/** vor1205: dieselben Einzelaufrufe auf der API von 1.20.4 (Farbe als Gleitkomma, alte Puffer). */
public final class AltApi
{
    private AltApi()
    {}

    public static BufferBuilder bauer(ByteBufferBuilder speicher, VertexFormat.Mode art, VertexFormat format)
    {
        return BauerAlt.neu(speicher, art, format);
    }

    public static MultiBufferSource.BufferSource quelle(ByteBufferBuilder speicher)
    {
        return MultiBufferSource.immediate(speicher.bauer());
    }

    public static void modell(Model modell, PoseStack pose, VertexConsumer ecken, int licht, int ueberlagerung, int farbe)
    {
        modell.renderToBuffer(pose, ecken, licht, ueberlagerung, (farbe >> 16 & 255) / 255F, (farbe >> 8 & 255) / 255F,
            (farbe & 255) / 255F, (farbe >>> 24) / 255F);
    }

    public static void teil(ModelPart teil, PoseStack pose, VertexConsumer ecken, int licht, int ueberlagerung, int farbe)
    {
        teil.render(pose, ecken, licht, ueberlagerung, (farbe >> 16 & 255) / 255F, (farbe >> 8 & 255) / 255F,
            (farbe & 255) / 255F, (farbe >>> 24) / 255F);
    }

    public static void weltlicht()
    {
        Lighting.setupLevel(RenderSystem.getModelViewMatrix());
    }

    /** Menue-Unschaerfe kam mit 1.20.5. */
    public static int menueUnschaerfe()
    {
        return 0;
    }

    /** 26.3s blitAndBlendToTexture ins Hauptziel: Hauptziel binden, gemischt hineinblitten. */
    public static void mischen(com.mojang.blaze3d.pipeline.RenderTarget quelle, com.mojang.blaze3d.pipeline.RenderTarget haupt)
    {
        haupt.bindWrite(true);
        quelle.blitAndBlendToScreen(haupt.width, haupt.height);
    }

    /** PoseStack.Pose#copy fehlt vor 1.20.5. */
    public static PoseStack.Pose kopie(PoseStack.Pose pose)
    {
        PoseStack stapel = new PoseStack();

        stapel.last().pose().set(pose.pose());
        stapel.last().normal().set(pose.normal());

        return stapel.last();
    }
}
