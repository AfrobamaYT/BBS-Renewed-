package mchorse.bbs_mod.mixin.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.clips.misc.TrackerClientClip;
import mchorse.bbs_mod.camera.controller.CameraController;
import mchorse.bbs_mod.camera.controller.ICameraController;
import mchorse.bbs_mod.camera.controller.CameraWorkCameraController;
import mchorse.bbs_mod.camera.controller.PlayCameraController;
import mchorse.bbs_mod.client.BBSRendering;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin
{
    /**
     * ⭐ HIER lenkt BBS das Rendern in seinen eigenen Rahmenpuffer um — der Nachfolger des
     * ueberschriebenen {@code Minecraft.mainRenderTarget}-Feldes aus 1.21.1.
     *
     * <h2>Warum nicht ueber die Textursicht-Umleitung (das war der Fehler)</h2>
     *
     * <p>Der Port hatte dafuer {@code RenderSystem.outputColorTextureOverride} genommen, weil
     * das Feld {@code Minecraft.mainRenderTarget} verschwunden war und jenes wie der Nachfolger
     * aussah. Es ist keiner, und das laesst sich im Bytecode zeigen:
     *
     * <ul>
     *   <li>{@code LevelRenderer.render} holt sein Ziel ueber {@code GameRenderer.mainRenderTarget()}
     *       und meldet es mit {@code FrameGraphBuilder.importExternal("main", ...)} beim Bildgraphen
     *       an. DA entscheidet sich, wohin die Welt geht.</li>
     *   <li>Dieselbe Methode SETZT die beiden Override-Felder anschliessend selbst und raeumt sie
     *       danach wieder ab. Es ist ein kurzlebiger Schalter INNERHALB eines Durchgangs, kein
     *       globaler Umlenker — wer ihn vorher setzt, dessen Wert ist weg, sobald Vanilla ihn
     *       ueberschreibt.</li>
     * </ul>
     *
     * <p>Gemessen, nicht geschlossen: mit gesetzten Overrides blieb BBS' Puffer ueber den ganzen
     * Weltdurchgang schwarz (Helligkeit 0 an beiden Enden), waehrend Mojangs Hauptziel gefuellt
     * war. Genau deshalb blieb das Vorschaufeld des Film-Editors schwarz (Aufgabe 91).
     *
     * <p>⚠️ DIE BEDINGUNG MUSS DIESELBE SEIN WIE IN {@code WindowMixin}. {@code GameRenderer.render}
     * vergleicht die Masse des Ziels mit {@code windowRenderState} und RESIZED das Ziel bei
     * Abweichung. Solange beide an {@code canReplaceFramebuffer()} haengen, meldet das Fenster
     * genau dann die Videogroesse, wenn hier auch BBS' Puffer herausgegeben wird — sonst
     * schrumpfte Vanilla den Puffer auf Fenstergroesse und die Aufnahme haette die falsche
     * Aufloesung.
     */
    @Inject(method = "mainRenderTarget", at = @At("HEAD"), cancellable = true)
    private void bbs$umlenkenAufFilmpuffer(CallbackInfoReturnable<RenderTarget> info)
    {
        if (BBSRendering.canReplaceFramebuffer() && BBSRendering.getFramebuffer() != null)
        {
            info.setReturnValue(BBSRendering.getFramebuffer());
        }
    }

    /**
     * This injection cancels bobbing when camera controller takes over
     */
    /* ⚠️ 26.2: (CameraRenderState, PoseStack) statt (PoseStack, float). Der Teiltick wird
     * nicht mehr gereicht -- das Wackeln haengt jetzt am Kamera-Zustand, nicht mehr an einem
     * durchgereichten Zeitwert. Wir holen ihn dort, wo Vanilla ihn auch herholt. */
    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    public void onBob(CameraRenderState kamera, PoseStack matrices, CallbackInfo ci)
    {
        float tickDelta = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);

        if (BBSModClient.getCameraController().getCurrent() != null)
        {
            if (TrackerClientClip.shouldRenderHand())
            {
                TrackerClientClip.applyBob(matrices, tickDelta);
            }

            ci.cancel();
        }
    }

    /* ⚠️ DAS SICHTFELD WIRD NICHT MEHR HIER UEBERSCHRIEBEN (26.2).
     *
     * `GameRenderer.getFov(Camera, float, boolean)` gibt es nicht mehr. Das sichtfeld
     * rechnet jetzt die KAMERA selbst, einmal je bild in `Camera.update(DeltaTracker)`:
     *
     *     alignWithEntity(δ)                 <- pose (frueher Camera.setup)
     *     this.fov    = calculateFov(δ)      <- welt   (frueher getFov(.., true))
     *     this.hudFov = calculateHudFov(δ)   <- hand   (frueher getFov(.., false))
     *     prepareCullFrustum(..) / setupPerspective(.., fov, ..)
     *
     * Der BBS-ueberschreiber (gun-zoom, clip-FOV, sprint-abzug fuer die hand) sitzt darum
     * jetzt in {@link CameraMixin} — dieselbe zielklasse wie die kamerapose. Aus dem
     * booleschen `changingFov` sind zwei getrennte methoden geworden; der schalter
     * "Hand follows sprint FOV" wirkt entsprechend auf `calculateHudFov`.
     *
     * Gemessen am 26.2-jar (javap -c auf net.minecraft.client.Camera), nicht angenommen. */

    /**
     * This injection replaces the camera roll when camera controller takes over
     */
    /* ⚠️ 26.2: wie bobView -- (CameraRenderState, PoseStack). Der Teiltick wird hier gar
     * nicht gebraucht, der Parameter faellt einfach weg. */
    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    public void onTiltViewWhenHurt(CameraRenderState kamera, PoseStack matrices, CallbackInfo info)
    {
        CameraController controller = BBSModClient.getCameraController();

        if (controller.getCurrent() != null && !BBSRendering.isIrisShadowPass())
        {
            matrices.mulPose(Axis.ZP.rotationDegrees(controller.getRoll()));

            info.cancel();
        }
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    public void onRenderHand(CallbackInfo info)
    {
        ICameraController current = BBSModClient.getCameraController().getCurrent();

        /* CameraWorkCameraController statt PlayCameraController: der editor-viewport
         * laeuft ueber RunnerCameraController, beide erben von der basisklasse. */
        if (current instanceof CameraWorkCameraController)
        {
            if (TrackerClientClip.shouldRenderHand())
            {
                TrackerClientClip.beginHandRendering();

                return;
            }

            info.cancel();
        }
    }

    @Inject(method = "renderItemInHand", at = @At("RETURN"))
    public void onRenderHandEnd(CallbackInfo info)
    {
        if (TrackerClientClip.shouldRenderHand()
            && BBSModClient.getCameraController().getCurrent() != null)
        {
            TrackerClientClip.endHandRendering();
        }
    }

    @Inject(at = @At("HEAD"), method = "renderLevel")
    private void onWorldRenderBegin(CallbackInfo callbackInfo)
    {
        if (BBSModClient.getCameraController().getCurrent() != null)
        {
            TrackerClientClip.beginLevelRendering();
        }

        BBSRendering.onWorldRenderBegin();

        /* Brotkrume fuer den naechsten "Pose stack not empty": vanillas checkPoseStack
         * nennt keinen BBS-Rahmen, weil es den Ungleichstand nur als letztes bemerkt.
         * So steht im Bericht wenigstens, WAS zuletzt lief. */
        mchorse.bbs_mod.graphics.Stapelwache.abschnitt("level:begin");
    }

    /**
     * These two injections substitute an orthographic projection when the film
     * editor's orbit camera asks for one (see BBSRendering#getOrthoProjection).
     * The frustum culling matrix gets a loose lower bound on the frame size so
     * culling stays conservative when zoomed all the way in; the same bound
     * pushes its near plane back, so the frustum never culls a section the
     * render would still have drawn.
     */
    /* ⛔ HERAUSGENOMMEN 2026-07-31 -- die orthografische Editor-Kamera ist voruebergehend aus.
     *
     * Hier standen zwei @ModifyArg, die eine ORTHOGRAFISCHE Projektion unterschoben, wenn die
     * Umlaufkamera des Film-Editors eine wollte: einmal am prepareCullFrustum-Aufruf (damit das
     * Keulen zur gezeichneten Ansicht passt), einmal am renderLevel-Aufruf.
     *
     * Beide Ziele sind weg. Die Aufrufe sind von LevelRenderer nach Camera gewandert, und --
     * das ist der eigentliche Punkt -- setupPerspective nimmt gar keine Matrix mehr entgegen,
     * sondern (zNear, zFar, fov, breite, hoehe). Es gibt kein Matrix-Argument mehr, das man
     * unterschieben koennte. Der Trick selbst ist also nicht mehr uebersetzbar, nicht nur der
     * Deskriptor.
     *
     * ⭐ DER RUECKWEG IST BESSER ALS DAS ORIGINAL. 26.2 hat eine erstklassige orthografische
     * Projektion: net.minecraft.client.renderer.Projection.setupOrtho(...) neben
     * setupPerspective(...), und Camera haelt so ein Projection-Objekt als Feld. Statt eine
     * fertige Matrix zu ersetzen, sagt man dem Projektionsobjekt, dass es orthografisch sein
     * soll -- damit stimmen Keulen, nahe/ferne Ebene und Projektion automatisch ueberein,
     * waehrend der alte Weg sie an ZWEI Stellen getrennt unterschieben musste und dabei (siehe
     * die alten Kommentare) eine lose untere Schranke brauchte, damit das Keulen nicht zu viel
     * wegschnitt.
     *
     * ZU TUN: in CameraMixin ein @Shadow auf das Projection-Feld, Injektor am RETURN von
     * setupPerspective, und bei aktiver Umlaufkamera setupOrtho aufrufen. Die Parameterfolge
     * von setupOrtho ist NICHT geraten nachzuschlagen, sondern an einem Vanilla-Aufrufer
     * abzulesen. Bis dahin: der Film-Editor zeichnet perspektivisch statt orthografisch. */


    @Inject(at = @At("RETURN"), method = "renderLevel")
    private void onWorldRenderEnd(CallbackInfo callbackInfo)
    {
        TrackerClientClip.endLevelRendering();
        BBSRendering.onWorldRenderEnd();

        mchorse.bbs_mod.graphics.Stapelwache.abschnitt("level:ende");
    }

    @Inject(method = "render", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Options;hideGui:Z", opcode = Opcodes.GETFIELD, ordinal = 0), require = 0)
    private void onBeforeHudRendering(DeltaTracker tickCounter, boolean tick, CallbackInfo info)
    {
        ICameraController current = BBSModClient.getCameraController().getCurrent();

        if (mchorse.bbs_mod.client.HudSichtbarkeit.istVersteckt() && current == null)
        {
            BBSRendering.onRenderBeforeScreen();
        }
    }
}