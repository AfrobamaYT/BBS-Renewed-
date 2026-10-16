package mchorse.bbs_mod.forms.renderers.utils;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.renderer.texture.DynamicTexture;

import java.util.HashMap;
import java.util.Map;

/**
 * A form's color overlay: fills it with a color instead of multiplying, from BBS FS f95662b30
 * (Wemppy4/bbs-fs, MIT). The model shader already mixes its overlay sampler over the texture
 * ({@code mix(overlay.rgb, color.rgb, overlay.a)}); here that sampler is a palette whose first
 * 16×16 texels are vanilla's overlay (the hurt flash keeps working) and whose rows below hold one
 * texel per overlay color in use. A form with an overlay then only swaps its packed overlay
 * coordinates, which every draw path already carries, the deferred translucent one included.
 * Texel: RGB = color, A = 1 − strength.
 */
public final class FormUeberlagerung
{
    private static final int BREITE = 256, ZEILEN = 16;

    private static DynamicTexture palette;
    /** Uploads wait for the client tick: a write while a render pass is open throws and leaves the pass open (2026-10-15) */
    private static boolean schmutzig;
    private static final Map<Integer, Integer> PLAETZE = new HashMap<>();

    private FormUeberlagerung()
    {}

    /** The packed overlay a form draws with: its own color when it has one, else what it was given */
    public static int overlay(Form form, int overlay)
    {
        return overlay(form.overlay.get(), overlay);
    }

    /** A bone's overlay over its form's: both collapse into one texel (OverlayBlend) */
    public static int knochen(Form form, Color knochen, int overlay)
    {
        if (knochen.a <= 0.002F) return overlay;

        Color color = form == null ? new Color(1F, 1F, 1F, 0F) : form.overlay.get().copy();

        mchorse.bbs_mod.utils.colors.OverlayBlend.stack(color, knochen);

        return overlay(color, overlay);
    }

    public static int overlay(Color color, int overlay)
    {
        if (color.a <= 0.002F)
        {
            return overlay;
        }

        int texel = ((int) ((1F - Math.min(color.a, 1F)) * 255F) << 24) | (color.getRGBColor() & 0xFFFFFF);
        Integer platz = PLAETZE.get(texel);

        if (platz == null)
        {
            if (PLAETZE.size() >= BREITE * ZEILEN) PLAETZE.clear();

            platz = PLAETZE.size();
            PLAETZE.put(texel, platz);
            bild().getPixels().setPixel(platz % BREITE, 16 + platz / BREITE, texel);
            schmutzig = true;
        }

        return (platz % BREITE) | ((16 + platz / BREITE) << 16);
    }

    public static GpuTextureView view()
    {
        return bild().getTextureView();
    }

    private static DynamicTexture bild()
    {
        if (palette == null)
        {
            palette = new DynamicTexture("BBS overlay palette", BREITE, 16 + ZEILEN, false);

            NativeImage pixel = palette.getPixels();

            /* Vanilla's OverlayTexture, texel for texel: red for the hurt rows, white fading in alpha below */
            for (int v = 0; v < 16; v++) for (int u = 0; u < 16; u++)
            {
                pixel.setPixel(u, v, v < 8 ? 0xB2FF0000 : ((int) ((1F - u / 15F * 0.75F) * 255F) << 24) | 0xFFFFFF);
            }

            schmutzig = true;
        }

        return palette;
    }

    /** Client tick (BBSModClient START_CLIENT_TICK): creates the palette before any draw and uploads changes outside render passes */
    public static void hochladen()
    {
        bild();

        if (schmutzig)
        {
            schmutzig = false;
            palette.upload();
        }
    }
}
