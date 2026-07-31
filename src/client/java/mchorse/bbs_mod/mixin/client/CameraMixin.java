package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.controller.CameraController;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.items.GunZoom;
import net.minecraft.client.Camera;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Die BBS-kamera uebernimmt die vanilla-kamera: pose UND sichtfeld.
 *
 * <h2>26.2: warum hier jetzt auch das sichtfeld steht</h2>
 *
 * <p>Bis 1.21.1 lagen die beiden an verschiedenen orten — die pose in {@code Camera.setup(...)},
 * das sichtfeld in {@code GameRenderer.getFov(Camera, float, boolean)}. In 26.2 rechnet die
 * kamera beides selbst, einmal je bild, in dieser reihenfolge
 * ({@code Camera.update(DeltaTracker)}, per javap -c am 26.2-jar gelesen):</p>
 *
 * <pre>
 *   alignWithEntity(δ)                 pose aus der kamera-entitaet   (frueher: setup)
 *   this.fov    = calculateFov(δ)      sichtfeld welt                 (frueher: getFov(.., true))
 *   this.hudFov = calculateHudFov(δ)   sichtfeld hand/HUD             (frueher: getFov(.., false))
 *   prepareCullFrustum(...)            keult gegen POSE + fov
 *   setupPerspective(0.05, depthFar, fov, w, h)
 * </pre>
 *
 * <p>Daraus folgt die einhaengung: die pose MUSS an {@code alignWithEntity} haengen und nicht
 * am ende von {@code update} — sonst waere sie erst nach dem keulen gesetzt, und der
 * sichtkegel wuerde vom SPIELER aus keulen, waehrend das bild von der film-kamera aus
 * gezeichnet wird (fehlende chunks am bildrand).</p>
 */
@Mixin(Camera.class)
public abstract class CameraMixin
{
    @Shadow protected abstract void setRotation(float yaw, float pitch);
    @Shadow protected abstract void setPosition(double x, double y, double z);

    /**
     * Nachfolger von {@code Camera.setup(BlockGetter, Entity, boolean, boolean, float)}.
     *
     * <p>{@code alignWithEntity(float)} ist genau dessen alter rumpf: blickrichtung und
     * position aus der kamera-entitaet, ruecken in der dritten person, drehung im bett. Es
     * wird ausschliesslich aus {@code update} gerufen, einmal je bild, mit demselben
     * teil-tick, den frueher {@code setup} bekam.</p>
     */
    @Inject(method = "alignWithEntity", at = @At(value = "RETURN"))
    public void onUpdate(float tickDelta, CallbackInfo ci)
    {
        CameraController controller = BBSModClient.getCameraController();

        controller.setup(controller.camera, tickDelta);

        if (controller.getCurrent() != null)
        {
            Vector3d position = controller.getPosition();
            float yaw = controller.getYaw();
            float pitch = controller.getPitch();

            this.setPosition(position.x, position.y, position.z);
            this.setRotation(yaw, pitch);
        }
    }

    /**
     * Sichtfeld der WELT — frueher {@code getFov(camera, δ, changingFov = true)}.
     *
     * <p>Der wert landet in {@code Camera.fov} und speist von dort den sichtkegel und die
     * projektion des weltbildes.</p>
     */
    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void onWeltFov(float tickDelta, CallbackInfoReturnable<Float> info)
    {
        Float neu = this.bbsFov(true, info.getReturnValue());

        if (neu != null)
        {
            info.setReturnValue(neu);
        }
    }

    /**
     * Sichtfeld der HAND — frueher {@code getFov(camera, δ, changingFov = false)}.
     *
     * <p>Der wert landet in {@code Camera.hudFov}, wird von {@code extractRenderState} in den
     * {@code CameraRenderState} kopiert und baut in {@code GameRenderer.renderLevel} die
     * eigene projektion, mit der die erste-person-hand gezeichnet wird.</p>
     *
     * <p>⚠️ VANILLA-AENDERUNG, die man sofort sieht: {@code calculateHudFov} nimmt NICHT mehr
     * den FOV-regler des spielers, sondern die konstante {@code Camera.BASE_HUD_FOV} (70) und
     * legt nur noch tod/fluessigkeit darauf. Ohne BBS-kamera ist die hand also immer 70°, egal
     * was im optionsmenue steht. Mit BBS-kamera ist das egal — wir ersetzen den wert ohnehin
     * durch das clip-FOV.</p>
     */
    @Inject(method = "calculateHudFov", at = @At("RETURN"), cancellable = true)
    private void onHandFov(float tickDelta, CallbackInfoReturnable<Float> info)
    {
        Float neu = this.bbsFov(false, info.getReturnValue());

        if (neu != null)
        {
            info.setReturnValue(neu);
        }
    }

    /**
     * Der gemeinsame rumpf beider sichtfeld-injektoren.
     *
     * <p>{@code changingFov} war bis 1.21.1 ein parameter von {@code getFov} und trennte welt
     * (true) von hand (false). In 26.2 macht diese trennung die METHODENWAHL — der wert ist
     * darum je einhaengung konstant. Die bedeutung ist unveraendert, damit die beiden zweige
     * unten dieselben bleiben.</p>
     *
     * <p>⚠️ REIHENFOLGE: {@code setReturnValue} cancelt den callback, und mixin springt danach
     * sofort zurueck — jeder WEITERE RETURN-injektor derselben methode wird uebersprungen,
     * unabhaengig von seiner prioritaet. Gemessen 2026-07-28 an der alten stelle; die
     * mixin-mechanik ist an der neuen dieselbe. Wer hier etwas anhaengen will, das den
     * BBS-wert noch veraendern soll, kann das NICHT ueber einen zweiten injektor tun.
     * Betrifft {@code SprintFOVMixin} (2000) und {@code GameRendererTrackerSprintFovMixin}
     * (3000): beide haengen an {@code calculateFov} und laufen nur, solange dieser handler
     * NICHT ueberschreibt (also ohne aktive BBS-kamera).</p>
     *
     * @return das neue sichtfeld, oder {@code null} wenn vanilla stehenbleiben soll
     */
    private Float bbsFov(boolean changingFov, float original)
    {
        GunZoom gunZoom = BBSModClient.getGunZoom();

        if (gunZoom != null)
        {
            return gunZoom.getFOV(original);
        }

        CameraController controller = BBSModClient.getCameraController();

        if (controller.getCurrent() == null || BBSRendering.isIrisShadowPass())
        {
            return null;
        }

        double fov = controller.getFOV();

        /* camera.fov traegt den sprint-boost, den PositionFovLogik auf angle.fov legt.
         * Fuer die welt ist das gewollt, fuer die hand nicht — sonst streckt sie sich beim
         * rennen mit. Nur den sprint-anteil herausrechnen; das clip-FOV bleibt stehen,
         * damit die hand weiterhin dem shot folgt.
         *
         * Abschaltbar, weil es eine bewusste abweichung vom alten fork ist: wer das alte
         * verhalten will, schaltet "Hand follows sprint FOV" in den editor-settings an. */
        if (!changingFov && !BBSSettings.editorHandSprintFov.get())
        {
            float sprint = com.afrobama.blendertransform.PositionFovLogik.sprintFaktor();

            if (sprint > 1.001F)
            {
                fov /= sprint;
            }
        }

        return (float) fov;
    }
}
