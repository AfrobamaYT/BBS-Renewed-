package com.afrobama.blendertransform;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSRendering;

/**
 * Logik hinter den Mixins, die WELT-EIGENSCHAFTEN fuer den Film uebersteuern —
 * {@code ClientWorldPropertiesMixin} (Tageszeit) und
 * {@code BlockEntityRenderDispatcherMixin} (Chroma-Himmel).
 *
 * <p>Beide beantworten dieselbe Frage: gilt gerade der Wert der Welt, oder der des Films? Sie
 * gehoeren deshalb in eine Klasse — wer den einen anfasst, prueft ueblicherweise auch den
 * anderen.</p>
 *
 * <p>⚠️ {@code null} HEISST DURCHWEG "NICHT UEBERSTEUERN". Ein Handler, der das mit "Wert ist
 * 0" verwechselt, macht aus einer Welt ohne Zeit-Clip eine Welt, die auf Mitternacht
 * festhaengt.</p>
 */
public final class WeltUeberschreibungLogik
{
    private WeltUeberschreibungLogik() {}

    /**
     * {@code Level.getDefaultClockTime}
     *
     * @return die uebersteuerte Tageszeit, oder {@code null} fuer die der Welt
     */
    public static Long tageszeit()
    {
        return BBSRendering.getTimeOfDay();
    }

    /**
     * {@code BlockEntityRenderDispatcher.tryExtractRenderState}
     *
     * @return {@code true}, wenn Blockentities NICHT gezeichnet werden sollen — bei aktivem
     *         Chroma-Himmel ohne Gelaende wuerden Truhen und Schilder sonst vor der
     *         Farbflaeche stehen bleiben
     */
    public static boolean blockentitiesUnterdruecken()
    {
        return BBSSettings.chromaSkyEnabled.get() && !BBSSettings.chromaSkyTerrain.get();
    }
}
