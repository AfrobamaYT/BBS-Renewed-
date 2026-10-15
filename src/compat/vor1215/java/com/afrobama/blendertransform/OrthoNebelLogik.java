package com.afrobama.blendertransform;

import mchorse.bbs_mod.client.BBSRendering;
import net.minecraft.client.renderer.FogParameters;

/**
 * Vor 1.21.5 (ersetzt die gemeinsame Fassung): schiebt im Ortho-Modus den Nebel aus dem Bild,
 * wie die gemeinsame Fassung mit FogData; 1.21.4 hat dafuer nur Anfang und Ende.
 */
public final class OrthoNebelLogik
{
    private static final float START = 1_000_000F;
    private static final float ENDE = 1_001_000F;

    private OrthoNebelLogik() {}

    /** @return die weggeschobenen Parameter, oder {@code null}, wenn Vanillas bleiben */
    public static FogParameters nebelWegschieben(FogParameters daten)
    {
        if (daten == null || !BBSRendering.isOrthoActive())
        {
            return null;
        }

        return new FogParameters(START, ENDE, daten.shape(), daten.red(), daten.green(), daten.blue(), daten.alpha());
    }
}
