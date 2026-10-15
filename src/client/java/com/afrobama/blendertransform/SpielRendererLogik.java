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
        if (mchorse.bbs_mod.client.ExportHud.aktiv())
            return BBSRendering.getFramebuffer();
        if (com.blenderbridge.camera.RenderCameraPreview.isRendering())
            return com.blenderbridge.camera.RenderCameraPreview.target();
        return BBSRendering.canReplaceFramebuffer() && BBSRendering.getFramebuffer() != null
            ? BBSRendering.getFramebuffer()
            : null;
    }

    /** 26.3's sky renderer and hand pass keep a fixed target; BBS world passes use a dynamic target. */
    public static RenderTarget himmelsziel(RenderTarget original)
    {
        RenderTarget active = filmpuffer();

        if (active != null)
        {
            return active;
        }

        /* Not the target the sky renderer remembers: LevelRenderer.addSkyPass creates it with
         * mainRenderTarget(), which is the film buffer while the film redirect is on (opening the
         * dashboard right after world load). It kept that buffer for good, and after the film the
         * sky drew with the film's 1452x546 depth beside the pack's window-sized colour: audit
         * #103, measured "Vitrail sky" depth = "bbs-film / Depth". The window target, unredirected, read from GameRenderer's field (a
         * reflective read rather than an accessor mixin, so the fix hotswaps into a running game). */
        RenderTarget fenster = (RenderTarget) com.afrobama.bbstools.client.blender.MixinBruecke
            .feldLesen(Minecraft.getInstance().gameRenderer, "mainRenderTarget");

        return fenster != null ? fenster : original;
    }

    /** The GUI samples this texture after the current world's shaderpack/post passes finished. */
    public static void vorGuiZeichnen()
    {
        if (Minecraft.getInstance().level != null && BBSRendering.isCustomSize())
        {
            BBSRendering.fuellen();
        }

        /* Welt-Abgriff fuer den Hybrid-Render: hier ist die Welt fertig und kein Pass offen. */
        com.mcstudio.see.WeltAbgriffNachPass.vorGui();
    }

    /** The GUI pass is closed here; a redirected Minecraft HUD has also reached the film target. */
    public static void nachGuiZeichnen()
    {
        mchorse.bbs_mod.client.ExportHud.zeichnen();
        BBSModClient.captureFinishedFrame();
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
                Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
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
        if (com.blenderbridge.camera.RenderCameraPreview.isRendering())
        {
            matrizen.rotate(Axis.ZP.rotationDegrees(com.blenderbridge.camera.RenderCameraPreview.roll()));
            return true;
        }
        CameraController regler = BBSModClient.getCameraController();

        if (regler.getCurrent() == null || BBSRendering.isIrisShadowPass())
        {
            return false;
        }

        matrizen.rotate(Axis.ZP.rotationDegrees(regler.getRoll()));

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
        /* The preview camera's second world pass is not the film's frame: it must not switch the
         * film redirect on (models then drew into bbs-film inside the preview's native pass, audit
         * 2026-10-14) nor start the film's and the menu's frame a second time. */
        if (ZweitKamera.aktiv())
        {
            return;
        }

        mchorse.bbs_mod.cubic.render.RigOverlayPass.clear();
        if (BBSModClient.getCameraController().getCurrent() != null)
        {
            //? if <26.3 {
            /* Vor 26.3 gibt es keine Spieler-Extraktion: die Hand des Darstellers gilt fuer
             * den ganzen Weltdurchgang, in dem auch renderItemInHand liegt. */
            TrackerClientClip.beginPlayerStateExtraction();
            //?}
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
        if (ZweitKamera.aktiv())
        {
            return;
        }

        mchorse.bbs_mod.cubic.render.RigOverlayPass.flush();
        TrackerClientClip.endLevelRendering();
        //? if <26.3 {
        TrackerClientClip.endPlayerStateExtraction();
        //?}
        BBSRendering.onWorldRenderEnd();
        Stapelwache.abschnitt("level:ende");
    }

    /** {@code render3dHud} @HEAD — Welt samt Shaderpack-Composite fertig, Tiefe noch die der Welt. */
    public static void nachDerWelt(net.minecraft.client.renderer.state.CameraRenderState kamera)
    {
        mchorse.bbs_mod.film.regie.RegieWelt.zeichnen(kamera);
    }

    /**
     * {@code render} @HEAD — verstecktes HUD ohne laufende Kamera.
     *
     * <p>Das ist der Editor-Fall: kein Clip fuehrt, aber das HUD ist ausgeblendet. Dann muss
     * der Vorlauf trotzdem stattfinden, sonst bleibt der Bildschirm auf dem letzten Bild.</p>
     */
    public static void vorDemBildschirm()
    {
        /* GameRenderer.render HEAD precedes world collection and also runs for menu/UI frames. */
        mchorse.bbs_mod.forms.renderers.FramebufferFormPreparation.beginFrame();
        /* Outside every render pass: textures requested inside one are uploaded here. */
        BBSModClient.getTextures().ladeAufgeschobene();

        if (HudSichtbarkeit.istVersteckt() && BBSModClient.getCameraController().getCurrent() == null)
        {
            BBSRendering.onRenderBeforeScreen();
        }
    }
}
