package com.afrobama.blendertransform;

import com.blenderbridge.CameraBridge;
import com.blenderbridge.LiveCameraOverride;

import mchorse.bbs_mod.camera.Camera;

/**
 * Logik der Live-Kamera-Übernahme aus Blender — im Kernel-Paket, damit sie hotswapbar ist.
 *
 * <p>Herausgelöst aus {@code CameraControllerLiveOverrideMixin} (2026-07-29, Aufgabe #56).
 * Nach {@code CameraController.setup} wird die Kamera auf die Werte gesetzt, die die
 * Blender-Seite zuletzt geschickt hat — sofern sie gerade das Sagen hat.</p>
 *
 * <p>⛔ DER ZUSTAND LIEGT IN {@link LiveCameraOverride}, NICHT HIER. Das ist wichtig: der
 * Kernel-Loader ist nur für {@code com.afrobama.blendertransform} und
 * {@code com.afrobama.bbstools.kernel} kind-zuerst. {@code com.blenderbridge} kommt vom
 * Elternlader, es gibt also genau eine Kopie und die Werte, die der Netzwerk-Thread dort
 * hineinschreibt, sind dieselben, die diese Klasse liest. Ein statisches Feld HIER hätte
 * nach einem Kernel-Tausch eine zweite, leere Kopie — der Klassiker.</p>
 *
 * <p>⛔ BESITZ-PRÜFUNG BLEIBT: {@code isActive()} ist das Eigentümer-Modell der Kamera
 * (NOBODY/BBS/BLENDER). Ohne die Abfrage würde diese Stelle BBS' eigene Kamerafahrt jeden
 * Frame überschreiben — genau der Kamera-Klemmer, gegen den das Eigentümer-Modell eingeführt
 * wurde.</p>
 */
public final class LiveCameraOverrideLogik
{
    private LiveCameraOverrideLogik() {}

    /**
     * Kamera überschreiben, wenn Blender gerade der Eigentümer ist.
     *
     * @return {@code true}, wenn geschrieben wurde — nur zur Beobachtung; der Handler
     *         wertet es nicht aus.
     */
    public static boolean uebernehmen(Camera kamera)
    {
        if (kamera == null || !LiveCameraOverride.isActive())
        {
            return false;
        }

        CameraBridge.writeCamera(
            kamera,
            LiveCameraOverride.getX(),
            LiveCameraOverride.getY(),
            LiveCameraOverride.getZ(),
            LiveCameraOverride.getPitch(),
            LiveCameraOverride.getYaw(),
            LiveCameraOverride.getRoll(),
            LiveCameraOverride.getFov()
        );

        return true;
    }
}
