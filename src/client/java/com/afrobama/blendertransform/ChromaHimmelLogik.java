package com.afrobama.blendertransform;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSRendering;
//? if >=1.21.5 {
import net.minecraft.client.renderer.state.level.SkyRenderState;
//?}
import org.joml.Vector4f;

/**
 * Chroma-Himmel: die Farbe, mit der Himmel und Nebel ueberschrieben werden.
 *
 * <p>⛔ 26.2 HAT KEIN {@code renderSky} MEHR, an dem der alte Haken den Farbpuffer loeschte.
 * Himmel- und Nebelfarbe sind Umgebungsattribute geworden; gesetzt wird deshalb dort, wo sie
 * ins Bild gehen: {@code SkyRenderState.skyColor} und die Nebelfarbe des {@code FogRenderer}.
 *
 * <p>⭐ BEIDE, NICHT NUR DER HIMMEL. Der alte 1.21er Haken faerbte nur den Himmel; ferne
 * Landschaft blendete weiter in die Vanilla-Nebelfarbe, und am Horizont blieb ein Saum
 * stehen, den man nicht wegkeyen konnte. Mit dem Nebel in derselben Farbe faellt er weg.
 */
public final class ChromaHimmelLogik
{
    private ChromaHimmelLogik() {}

    //? if >=1.21.5 {
    public static void himmel(SkyRenderState state)
    {
        Integer color = farbeOderNull();

        if (color != null)
        {
            //? if >=26.3 {
            state.skyColor = net.minecraft.util.ARGB.vector3fFromRGB24(color);
            //?} else {
            /*state.skyColor = color;
            *///?}
        }
    }
    //?}

    public static void nebel(Vector4f color)
    {
        Integer chroma = farbeOderNull();

        if (chroma != null)
        {
            color.set(kanal(chroma, 16), kanal(chroma, 8), kanal(chroma, 0), 1F);
        }
    }

    /**
     * @return die Chroma-Farbe als ARGB, oder {@code null}, wenn der Chroma-Himmel aus ist
     *         (⚠️ {@code null} heisst "nicht uebersteuern", nicht "schwarz")
     */
    public static Integer farbeOderNull()
    {
        if (!BBSSettings.chromaSkyEnabled.get())
        {
            return null;
        }

        /* Die Kurve schlaegt die Einstellung: ein Film kann die Farbe ueber die Zeit fahren. */
        Integer ausKurve = BBSRendering.getChromaSkyColorArgb();
        int argb = ausKurve != null ? ausKurve : BBSSettings.chromaSkyColor.get();

        /* ⛔ ALPHA WEG, IMMER OPAK. Der Farbwaehler speichert ARGB, und die Voreinstellung
         * ist Colors.A75 = 0xbb000000 — mit durchgereichtem Alpha waere der Himmel je nach
         * Wert halbdurchsichtig. Der Upstream macht dasselbe: glClearColor(r, g, b, 1F). */
        return 0xFF000000 | (argb & 0xFFFFFF);
    }

    /**
     * Gelaende und Wolken weglassen? Das ist der Schalter "terrain" im Chroma-Bereich:
     * aus heisst, dass nur noch Figuren und Formen vor der Farbflaeche stehen.
     *
     * <p>⛔ WOLKEN GEHOEREN DAZU. Sie haengen nicht am Gelaende, standen aber ohne das hier
     * weiter vor der Chroma-Flaeche ("obwohl render terrain off ist seh ich die wolken und
     * das gesamte terrain"). Der 1.21er Fork hatte dafuer einen eigenen Schalter
     * (chromaSkyClouds); 26.2 hat ihn nicht, also gilt hier der Gelaende-Schalter fuer beide.</p>
     */
    public static boolean gelaendeVerbergen()
    {
        return BBSSettings.chromaSkyEnabled.get() && !BBSSettings.chromaSkyTerrain.get();
    }

    /** Farbkanal 0..1 fuer den Nebel. {@code schiebung}: 16 = rot, 8 = gruen, 0 = blau. */
    public static float kanal(int argb, int schiebung)
    {
        return ((argb >> schiebung) & 0xFF) / 255F;
    }
}
