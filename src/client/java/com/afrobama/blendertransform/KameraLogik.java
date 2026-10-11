package com.afrobama.blendertransform;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.controller.CameraController;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.items.GunZoom;

/**
 * Logik hinter {@code CameraMixin}: wo die Kamera steht und wie weit sie schaut.
 *
 * <p>Beides haengt am selben {@link CameraController} und wird im selben Bild gelesen —
 * Stellung bei {@code alignWithEntity} @RETURN, Blickwinkel bei {@code calculateFov} und
 * {@code calculateHudFov}.</p>
 *
 * <p>⚠️ SETZEN BLEIBT IM MIXIN. {@code setPosition}/{@code setRotation} sind
 * {@code @Shadow}-Methoden der Vanilla-Kamera; dieser Kernel kennt sie nicht. Deshalb gibt
 * {@link #stellung} die Werte ZURUECK, statt sie zu setzen.</p>
 */
public final class KameraLogik
{
    private KameraLogik() {}

    /**
     * {@code Camera.alignWithEntity} @RETURN.
     *
     * <p>⛔ {@code setup} LAEUFT IMMER, auch wenn danach nichts gesetzt wird. Es ist kein
     * Abfragen, sondern der Schritt, der den Regler fuer dieses Bild fortschreibt — wer ihn
     * hinter die {@code getCurrent()}-Pruefung schiebt, laesst die Kamera stehen, sobald
     * gerade kein Clip laeuft, und der naechste Clip startet aus einem alten Zustand.</p>
     *
     * @return {@code {x, y, z, gier, neigung}} — oder {@code null}, wenn Vanilla bleiben soll
     */
    public static double[] stellung(float teilTick)
    {
        if (mchorse.bbs_mod.client.Erweiterungen.vorschauRendert())
            return mchorse.bbs_mod.client.Erweiterungen.vorschau.stellung();
        CameraController regler = BBSModClient.getCameraController();

        regler.setup(regler.camera, teilTick);

        if (regler.getCurrent() == null)
        {
            return null;
        }

        var pos = regler.getPosition();

        return new double[] {pos.x, pos.y, pos.z, regler.getYaw(), regler.getPitch()};
    }

    /**
     * {@code calculateFov} / {@code calculateHudFov} @RETURN.
     *
     * <p>⚠️ ZWEI AUFRUFER, EIN UNTERSCHIED. {@code weltFov=false} ist die HAND — und nur dort
     * wird der Sprint-Anteil wieder herausgerechnet, wenn der Nutzer das abgeschaltet hat.
     * Ohne diese Trennung zoomt beim Sprinten auch die Waffe in der Hand mit.</p>
     *
     * <p>⚠️ Der Schattenwurf von Iris bekommt NIE einen eigenen Blickwinkel
     * ({@code isIrisShadowPass}) — sonst wandern die Schatten mit der Filmkamera statt mit
     * der Sonne.</p>
     *
     * @return der neue Wert, oder {@code null} wenn Vanillas gelten soll
     */
    public static Float blickwinkel(boolean weltFov, float vanilla)
    {
        if (mchorse.bbs_mod.client.Erweiterungen.vorschauRendert())
            return mchorse.bbs_mod.client.Erweiterungen.vorschau.blickwinkel();
        GunZoom zoom = BBSModClient.getGunZoom();

        if (zoom != null)
        {
            return zoom.getFOV(vanilla);
        }

        CameraController regler = BBSModClient.getCameraController();

        if (regler.getCurrent() == null || BBSRendering.isIrisShadowPass())
        {
            return null;
        }

        double fov = regler.getFOV();

        if (!weltFov && !BBSSettings.editorHandSprintFov.get())
        {
            float sprint = PositionFovLogik.sprintFaktor();

            if (sprint > 1.001F)
            {
                fov /= sprint;
            }
        }

        return (float) fov;
    }
}
