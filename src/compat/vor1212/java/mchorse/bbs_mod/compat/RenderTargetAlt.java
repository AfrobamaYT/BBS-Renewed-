package mchorse.bbs_mod.compat;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.opengl.GlTextureView;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * vor1212 (ersetzt vor1215): 1.21.11s Textur-Zugriffe an 1.21.4s RenderTarget (Mixin RenderTargetAltMixin). Die
 * Schicht legt GlTexture-Huellen um die GL-Texturen des Ziels, ohne sie zu besitzen; aendert
 * das Ziel Groesse oder Textur (resize), entsteht eine neue Huelle.
 */
public interface RenderTargetAlt
{
    Map<RenderTarget, Huellen> HUELLEN = new WeakHashMap<>();

    private Huellen huellen()
    {
        RenderTarget ziel = (RenderTarget) this;
        Huellen h = HUELLEN.get(ziel);
        int farbe = ziel.getColorTextureId();
        int tiefe = ziel.useDepth ? ziel.getDepthTextureId() : 0;

        if (h == null || h.farbId != farbe || h.tiefeId != tiefe || h.breite != ziel.width || h.hoehe != ziel.height)
        {
            GlTexture f = new GlTexture(farbe, "rendertarget-farbe", TextureFormat.RGBA8, ziel.width, ziel.height, 1);
            GlTexture t = tiefe == 0 ? null : new GlTexture(tiefe, "rendertarget-tiefe", TextureFormat.DEPTH32, ziel.width, ziel.height, 1);

            h = new Huellen(farbe, tiefe, ziel.width, ziel.height, f, new GlTextureView(f, 0, 1), t, t == null ? null : new GlTextureView(t, 0, 1));
            HUELLEN.put(ziel, h);
        }

        return h;
    }

    /** 1.21.4-Formen ohne clearError-Schalter (1.21.1 fragt ihn ab; Mojang nimmt Minecraft.ON_OSX). */
    default void resize(int breite, int hoehe)
    {
        ((RenderTarget) this).resize(breite, hoehe, net.minecraft.client.Minecraft.ON_OSX);
    }

    default void clear()
    {
        ((RenderTarget) this).clear(net.minecraft.client.Minecraft.ON_OSX);
    }

    /** 1.21.4s blitAndBlendToScreen: 1.21.1 blittet mit Mischen, wenn disableBlend falsch ist. */
    default void blitAndBlendToScreen(int breite, int hoehe)
    {
        ((RenderTarget) this).blitToScreen(breite, hoehe, false);
    }

    default GpuTexture getColorTexture() { return this.huellen().farbe; }
    default GpuTextureView getColorTextureView() { return this.huellen().farbSicht; }
    default GpuTexture getDepthTexture() { return this.huellen().tiefe; }
    default GpuTextureView getDepthTextureView() { return this.huellen().tiefenSicht; }

    record Huellen(int farbId, int tiefeId, int breite, int hoehe, GlTexture farbe, GlTextureView farbSicht, GlTexture tiefe, GlTextureView tiefenSicht)
    {}
}
