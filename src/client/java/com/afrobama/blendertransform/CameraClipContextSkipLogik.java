package com.afrobama.blendertransform;

import com.afrobama.bbstools.clip.BBSToolsAddCameraClip;

import mchorse.bbs_mod.utils.clips.Clip;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logik der Clip-Auslassung für die Zweitkamera — im Kernel-Paket, damit sie hotswapbar ist.
 *
 * <p>Herausgelöst aus {@code CameraClipContextSkipOurClipMixin} (2026-07-29, Aufgabe #56).</p>
 *
 * <p><b>Wofür:</b> unser eigener Kamera-Clip soll die HAUPTkamera nicht bewegen — er gilt nur
 * für die Alt-Kachel. Also meldet {@code CameraClipContext.apply} für ihn „nicht angewandt"
 * und lässt die Hauptkamera in Ruhe.</p>
 *
 * <p>⛔ WARUM {@code instanceof} HIER SICHER IST: der Kernel-Loader ist nur für
 * {@code com.afrobama.blendertransform} und {@code com.afrobama.bbstools.kernel}
 * kind-zuerst. {@link BBSToolsAddCameraClip} liegt woanders und kommt darum in JEDER
 * Generation vom Elternlader — es gibt genau eine Kopie, und der Typvergleich stimmt. Läge
 * die Klasse in einem der beiden Kernel-Pakete, wäre {@code instanceof} still falsch und man
 * müsste über den Klassennamen vergleichen.</p>
 */
public final class CameraClipContextSkipLogik
{
    private CameraClipContextSkipLogik() {}

    private static final Logger LOG = LoggerFactory.getLogger("bbstools/cam-preview/clip-skip");
    private static boolean gemeldet;

    /** {@code true} = der Handler soll {@code false} zurückgeben, statt den Clip anzuwenden. */
    public static boolean ueberspringen(Clip clip)
    {
        if (!(clip instanceof BBSToolsAddCameraClip))
        {
            return false;
        }

        if (!gemeldet)
        {
            gemeldet = true;
            LOG.info("Hauptkamera-apply fuer BBSToolsAddCameraClip uebersprungen — die Alt-Kamera bleibt auf ihrer Kachel");
        }

        return true;
    }
}
