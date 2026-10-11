package com.afrobama.blendertransform;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.clips.misc.TrackerClientClip;
import mchorse.bbs_mod.camera.controller.CameraController;
import mchorse.bbs_mod.camera.controller.CameraWorkCameraController;
import mchorse.bbs_mod.camera.controller.ICameraController;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.HudSichtbarkeit;
import mchorse.bbs_mod.graphics.Stapelwache;
import net.minecraft.client.Minecraft;

/**
 * Logik hinter {@code GameRendererMixin} — wohin gezeichnet wird und was Vanilla dabei
 * NICHT tun darf, solange eine BBS-Kamera fuehrt.
 *
 * <p>Sieben Handler, eine Frage: laeuft ein Kameraclip? Dann uebernimmt BBS Bildpuffer,
 * Kopfwackeln, Trefferneigung, Hand und die Klammern um das Weltbild.</p>
 */
public final class SpielRendererLogik
{
    private SpielRendererLogik() {}

    /**
     * {@code mainRenderTarget} @HEAD — beim Aufnehmen zeichnet alles in BBS' Puffer.
     *
     * <p>⚠️ BEIDE PRUEFUNGEN NOETIG. {@code canReplaceFramebuffer()} sagt "wir duerfen",
     * {@code getFramebuffer() != null} sagt "er existiert schon". Der Puffer entsteht erst
     * beim ersten Bild einer Aufnahme; ohne die zweite Pruefung liefert diese Stelle in genau
     * diesem Bild {@code null} an alles, was gerade zeichnet.</p>
     *
     * @return der Ersatzpuffer, oder {@code null} fuer Vanillas eigenen
     */
    public static RenderTarget filmpuffer()
    {
        return BBSRendering.canReplaceFramebuffer() && BBSRendering.getFramebuffer() != null
            ? BBSRendering.getFramebuffer()
            : null;
    }

    /**
     * {@code bobView} @HEAD — Kopfwackeln.
     *
     * <p>⚠️ ES WIRD NICHT NUR ABGESCHALTET, SONDERN ERSETZT. Solange die Hand sichtbar ist,
     * legt BBS sein eigenes Wackeln auf; erst danach faellt Vanillas Rumpf weg. Wer nur
     * abbricht, nimmt der Handkamera die Bewegung.</p>
     *
     * @return {@code true} = Vanillas Wackeln wird uebersprungen
     */
    public static boolean kopfwackeln(PoseStack matrizen)
    {
        if (BBSModClient.getCameraController().getCurrent() == null)
        {
            return false;
        }

        if (TrackerClientClip.shouldRenderHand())
        {
            TrackerClientClip.applyBob(matrizen,
                Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
        }

        return true;
    }

    /**
     * {@code bobHurt} @HEAD — die Trefferneigung wird zur Kamerarollung.
     *
     * <p>⚠️ NICHT IM IRIS-SCHATTENWURF. Der laeuft mit derselben Matrix durch; eine Rollung
     * dort verdreht die Schatten gegen das Bild.</p>
     */
    public static boolean trefferneigung(PoseStack matrizen)
    {
        {
            return true;
        }
        CameraController regler = BBSModClient.getCameraController();

        if (regler.getCurrent() == null || BBSRendering.isIrisShadowPass())
        {
            return false;
        }

        matrizen.mulPose(Axis.ZP.rotationDegrees(regler.getRoll()));

        return true;
    }

    /**
     * {@code renderItemInHand} @HEAD.
     *
     * <p>⚠️ NUR UNTER DER ARBEITSKAMERA. Im Abspielen ({@code PlayCameraController}) bleibt
     * die Hand unangetastet — dort entscheidet der Clip selbst.</p>
     *
     * @return {@code true} = Hand wird gar nicht gezeichnet
     */
    public static boolean handAnfang()
    {
        ICameraController aktuell = BBSModClient.getCameraController().getCurrent();

        if (!(aktuell instanceof CameraWorkCameraController))
        {
            return false;
        }

        if (TrackerClientClip.shouldRenderHand())
        {
            TrackerClientClip.beginHandRendering();

            return false;
        }

        return true;
    }

    /** {@code renderItemInHand} @RETURN — Gegenstueck zu {@link #handAnfang}. */
    public static void handEnde()
    {
        TrackerClientClip.endHandRendering();
    }

    /** {@code renderLevel} @HEAD. */
    public static void weltAnfang()
    {
        mchorse.bbs_mod.cubic.render.RigOverlayPass.clear();
        if (BBSModClient.getCameraController().getCurrent() != null)
        {
            TrackerClientClip.beginLevelRendering();
        }

        BBSRendering.onWorldRenderBegin();
        if (BBSRendering.isIrisShadersEnabled())
        {
            mchorse.bbs_mod.utils.iris.IrisFrameTarget.sync();
        }
        Stapelwache.abschnitt("level:begin");
    }

    /**
     * {@code renderLevel} @RETURN.
     *
     * <p>⚠️ {@code endLevelRendering} laeuft OHNE die Kamera-Pruefung, die der Anfang hat —
     * absichtlich: endete der Clip mitten im Bild, muss trotzdem geschlossen werden.</p>
     */
    public static void weltEnde()
    {
        mchorse.bbs_mod.cubic.render.RigOverlayPass.flush();
        TrackerClientClip.endLevelRendering();

        if (BBSModClient.getVideoRecorder().isRecording() && BBSRendering.canRender)
        {
            BBSModClient.getMinecraftSoundCapture().captureFrame();
            BBSModClient.getVideoRecorder().recordFrame();
        }

        BBSRendering.onWorldRenderEnd();
        mchorse.bbs_mod.client.ExportFrameCallbacks.completeFrame();
        Stapelwache.abschnitt("level:ende");
    }

    /**
     * {@code render} @HEAD — verstecktes HUD ohne laufende Kamera.
     *
     * <p>Das ist der Editor-Fall: kein Clip fuehrt, aber das HUD ist ausgeblendet. Dann muss
     * der Vorlauf trotzdem stattfinden, sonst bleibt der Bildschirm auf dem letzten Bild.</p>
     */
    public static void vorDemBildschirm()
    {
        if (HudSichtbarkeit.istVersteckt() && BBSModClient.getCameraController().getCurrent() == null)
        {
            BBSRendering.onRenderBeforeScreen();
        }
    }
}
