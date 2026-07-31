package mchorse.bbs_mod.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.clips.misc.TrackerClientClip;
import mchorse.bbs_mod.camera.controller.CameraController;
import mchorse.bbs_mod.camera.controller.ICameraController;
import mchorse.bbs_mod.camera.controller.CameraWorkCameraController;
import mchorse.bbs_mod.camera.controller.PlayCameraController;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.items.GunZoom;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
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
     * This injection cancels bobbing when camera controller takes over
     */
    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    public void onBob(PoseStack matrices, float tickDelta, CallbackInfo ci)
    {
        if (BBSModClient.getCameraController().getCurrent() != null)
        {
            if (TrackerClientClip.shouldRenderHand())
            {
                TrackerClientClip.applyBob(matrices, tickDelta);
            }

            ci.cancel();
        }
    }

    /**
     * This injection replaces the camera FOV when camera controller takes over
     *
     * <p>changingFov ist vanillas trennung zwischen welt und erster-person-hand:
     * renderWorld ruft getFov mit true, renderHand mit false, und getFov ueberspringt bei false
     * den ganzen multiplikator-block. Im bytecode von 1.21.1 nachgelesen. Deshalb waechst im
     * echten spiel beim rennen die welt, die hand aber nicht.</p>
     *
     * <p>Die korrektur MUSS hier stehen, nicht in einem spaeteren injektor: setReturnValue
     * cancelt den callback, und mixin springt danach sofort zurueck — jeder weitere
     * RETURN-injektor wird uebersprungen, unabhaengig von seiner prioritaet. Gemessen
     * 2026-07-28: nach diesem handler steht cir.isCancelled() == true. Zwei anlaeufe ueber
     * eigene mixins (prioritaet 3000 bzw. @ModifyReturnValue mit 4000) blieben deshalb
     * wirkungslos, obwohl ihre logik isoliert das richtige ergebnis lieferte.</p>
     */
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    public void onGetFov(Camera camera, float tickDelta, boolean changingFov,
        CallbackInfoReturnable<Double> info)
    {
        GunZoom gunZoom = BBSModClient.getGunZoom();

        if (gunZoom != null)
        {
            info.setReturnValue((double) gunZoom.getFOV(info.getReturnValue().floatValue()));

            return;
        }

        CameraController controller = BBSModClient.getCameraController();

        if (controller.getCurrent() != null && !BBSRendering.isIrisShadowPass())
        {
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

            info.setReturnValue(fov);
        }
    }

    /**
     * This injection replaces the camera roll when camera controller takes over
     */
    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    public void onTiltViewWhenHurt(PoseStack matrices, float tickDelta, CallbackInfo info)
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
    }

    /**
     * These two injections substitute an orthographic projection when the film
     * editor's orbit camera asks for one (see BBSRendering#getOrthoProjection).
     * The frustum culling matrix gets a loose lower bound on the frame size so
     * culling stays conservative when zoomed all the way in; the same bound
     * pushes its near plane back, so the frustum never culls a section the
     * render would still have drawn.
     */
    @ModifyArg(
        method = "renderLevel",
        at = @At(
            value = "INVOKE",
            /* MERGE fsgh/2.5 (1.20.4 -> 1.21.1): setupFrustum nimmt keinen MatrixStack mehr,
             * sondern (Vec3, Matrix4f positionMatrix, Matrix4f projectionMatrix) — per javap
             * gegen die 1.21.1-jar geprueft. Die projektion bleibt argument 2. Mit dem alten
             * deskriptor fand mixin 0 ziele und der client startete gar nicht erst. */
            target = "Lnet/minecraft/client/renderer/LevelRenderer;prepareCullFrustum(Lnet/minecraft/world/phys/Vec3;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V"
        ),
        index = 2
    )
    private Matrix4f onSetupFrustumProjection(Matrix4f projection)
    {
        return BBSRendering.getOrthoProjection((GameRenderer) (Object) this, projection, 20F);
    }

    @ModifyArg(
        method = "renderLevel",
        at = @At(
            value = "INVOKE",
            /* MERGE fsgh/2.5 (1.20.4 -> 1.21.1): WorldRenderer.render nimmt statt
             * (MatrixStack, tickDelta, limitTime, blockOutline, ...) jetzt einen
             * RenderTickCounter und gibt ZWEI matrizen mit (position, projektion).
             * Die projektion ist weiterhin das LETZTE argument — damit wandert sie von
             * index 7 auf 6. Signatur per javap gegen die 1.21.1-jar geprueft. */
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V"
        ),
        index = 6
    )
    private Matrix4f onRenderProjection(Matrix4f projection)
    {
        Matrix4f ortho = BBSRendering.getOrthoProjection((GameRenderer) (Object) this, projection, 0F);

        if (ortho != projection)
        {
            mchorse.bbs_mod.graphics.Projektion.setzen(ortho, com.mojang.blaze3d.ProjectionType.ORTHOGRAPHIC);
        }

        return ortho;
    }

    @Inject(at = @At("RETURN"), method = "renderLevel")
    private void onWorldRenderEnd(CallbackInfo callbackInfo)
    {
        TrackerClientClip.endLevelRendering();
        BBSRendering.onWorldRenderEnd();
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