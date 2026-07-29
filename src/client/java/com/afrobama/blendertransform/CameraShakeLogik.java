package com.afrobama.blendertransform;

import com.afrobama.bbstools.client.fracture.CameraShakeManager;

/**
 * Logik des Kamera-Wackelns — im Kernel-Paket, damit sie hotswapbar ist.
 *
 * <p>Herausgelöst aus {@code CameraShakeMixin} (2026-07-29, Aufgabe #56). Der gewobene
 * Handler ist danach ein Einzeiler; die Reihenfolge (erst Yaw, dann Pitch) und die
 * Aktiv-Prüfung stehen hier und lassen sich live ändern.</p>
 *
 * <p><b>Warum das der richtige Ort ist:</b> {@code Camera.setRotation} läuft zweimal pro
 * Frame — das ist weit entfernt von den Pfaden, an denen ein Brückenaufruf weh tut (pro
 * Knochen pro Replay pro Frame). Der Aufwand fällt hier nicht ins Gewicht, der Gewinn
 * dagegen schon: Wackel-Verhalten will man beim Einstellen sofort sehen, nicht nach einem
 * Boot.</p>
 *
 * <p>⛔ ZEITPUNKT: {@code tickFrame()} (Rauschzeit vorstellen, Amplitude abklingen lassen)
 * darf NUR im Yaw-Zweig laufen. BBS ruft Yaw verlässlich vor Pitch; liefe es in beiden,
 * würde die Amplitude doppelt so schnell abklingen und das Rauschen zwischen den beiden
 * Achsen desselben Frames auseinanderlaufen — die Kamera wackelt dann schief statt rund.</p>
 */
public final class CameraShakeLogik
{
    private CameraShakeLogik() {}

    /** Yaw perturbieren. Stellt zugleich den Frame vor — siehe Klassen-Javadoc. */
    public static float yaw(float yaw)
    {
        if (!CameraShakeManager.isActive())
        {
            return yaw;
        }

        CameraShakeManager.tickFrame();

        return yaw + CameraShakeManager.currentYawDelta();
    }

    /** Pitch perturbieren. Läuft NACH {@link #yaw}, benutzt also denselben Frame-Stand. */
    public static float pitch(float pitch)
    {
        if (!CameraShakeManager.isActive())
        {
            return pitch;
        }

        return pitch + CameraShakeManager.currentPitchDelta();
    }
}
