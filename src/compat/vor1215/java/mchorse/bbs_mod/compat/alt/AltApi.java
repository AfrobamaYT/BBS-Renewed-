package mchorse.bbs_mod.compat.alt;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;

/** Versionsabhaengige Einzelaufrufe der Nachruestung vor 1.21.5 (vor1205 ersetzt diese Klasse). */
public final class AltApi
{
    private AltApi()
    {}

    public static BufferBuilder bauer(ByteBufferBuilder speicher, VertexFormat.Mode art, VertexFormat format)
    {
        return new BufferBuilder(speicher, art, format);
    }

    public static MultiBufferSource.BufferSource quelle(ByteBufferBuilder speicher)
    {
        return MultiBufferSource.immediate(speicher);
    }

    public static void modell(Model modell, PoseStack pose, VertexConsumer ecken, int licht, int ueberlagerung, int farbe)
    {
        modell.renderToBuffer(pose, ecken, licht, ueberlagerung, farbe);
    }

    public static void teil(ModelPart teil, PoseStack pose, VertexConsumer ecken, int licht, int ueberlagerung, int farbe)
    {
        teil.render(pose, ecken, licht, ueberlagerung, farbe);
    }

    public static void weltlicht()
    {
        Lighting.setupLevel();
    }

    public static int menueUnschaerfe()
    {
        return Minecraft.getInstance().options.getMenuBackgroundBlurriness();
    }

    /** 26.3s blitAndBlendToTexture ins Hauptziel: Hauptziel binden, gemischt hineinblitten. */
    public static void mischen(com.mojang.blaze3d.pipeline.RenderTarget quelle, com.mojang.blaze3d.pipeline.RenderTarget haupt)
    {
        haupt.bindWrite(true);
        quelle.blitAndBlendToScreen(haupt.width, haupt.height);
    }
}
