package com.afrobama.blendertransform;

import mchorse.bbs_mod.client.BBSRendering;
import net.minecraft.client.renderer.fog.FogData;

/**
 * Logik hinter {@code BackgroundRendererMixin}, herausgeloest damit sie hotswapbar ist.
 *
 * <p>Schiebt den Nebel bei orthografischer Kamera so weit weg, dass er nichts mehr verdeckt.
 * Eine Ortho-Projektion hat keine echte Tiefenstaffelung — der Nebel wuerde die ganze Szene
 * gleichmaessig einfaerben statt Entferntes auszublenden.</p>
 *
 * <p>⚠️ NUR BEI AKTIVER ORTHO-KAMERA. Ohne diese Abfrage waere der Nebel im ganzen Spiel
 * abgeschaltet; die Werte hier sind kein "Nebel aus", sondern "Nebel jenseits jeder
 * Sichtweite" (1 000 000 Bloecke).</p>
 */
public final class OrthoNebelLogik
{
    private OrthoNebelLogik() {}

    private static final float START = 1_000_000F;
    private static final float ENDE = 1_001_000F;

    /**
     * @param daten die von Vanilla zurueckgegebenen Nebeldaten; wird an Ort und Stelle
     *              veraendert, deshalb kein Rueckgabewert
     */
    public static void nebelWegschieben(FogData daten)
    {
        if (daten == null || !BBSRendering.isOrthoActive())
        {
            return;
        }

        daten.environmentalStart = START;
        daten.environmentalEnd = ENDE;
        daten.renderDistanceStart = START;
        daten.renderDistanceEnd = ENDE;
        daten.skyEnd = ENDE;
        daten.cloudEnd = ENDE;
    }
}
