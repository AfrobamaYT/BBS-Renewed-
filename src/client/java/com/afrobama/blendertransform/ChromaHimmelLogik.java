package com.afrobama.blendertransform;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSRendering;

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

        return ausKurve != null ? ausKurve : BBSSettings.chromaSkyColor.get();
    }

    /** Farbkanal 0..1 fuer den Nebel. {@code schiebung}: 16 = rot, 8 = gruen, 0 = blau. */
    public static float kanal(int argb, int schiebung)
    {
        return ((argb >> schiebung) & 0xFF) / 255F;
    }
}
