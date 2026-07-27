package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.controller.CameraController;
import mchorse.bbs_mod.camera.controller.ICameraController;
import mchorse.bbs_mod.camera.controller.PlayCameraController;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.items.GunZoom;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin
{
    /**
     * This injection cancels bobbing when camera controller takes over
     */
    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    public void onBob(CallbackInfo ci)
    {
        if (BBSModClient.getCameraController().getCurrent() != null)
        {
            ci.cancel();
        }
    }

    /**
     * This injection replaces the camera FOV when camera controller takes over
     */
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    public void onGetFov(CallbackInfoReturnable<Double> info)
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
            info.setReturnValue(controller.getFOV());
        }
    }

    /**
     * This injection replaces the camera roll when camera controller takes over
     */
    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
    public void onTiltViewWhenHurt(MatrixStack matrices, float tickDelta, CallbackInfo info)
    {
        CameraController controller = BBSModClient.getCameraController();

        if (controller.getCurrent() != null && !BBSRendering.isIrisShadowPass())
        {
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(controller.getRoll()));

            info.cancel();
        }
    }

    @Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
    public void onRenderHand(CallbackInfo info)
    {
        ICameraController current = BBSModClient.getCameraController().getCurrent();

        if (current instanceof PlayCameraController)
        {
            /* afrobama 2026-07-27 — 1:1 aus dem CML-fork uebernommen
             * (cml-fresh GameRendererMixin:149-173).
             *
             * fs cancelte hier UNBEDINGT, sobald ein film lief. Der apparat dahinter ist
             * vollstaendig vorhanden (renderHand-flag, shouldRenderHand() mit ease,
             * begin/endHandRendering) — nur fragte ihn niemand.
             *
             * ⛔ begin/endHandRendering() sind PFLICHT, nicht schmuck. Ein erster versuch
             * liess die hand nur durch (return statt cancel) — sie blieb trotzdem
             * unsichtbar, weil das render-setup fehlte. Genau dieses paar macht den
             * unterschied. */
            if (mchorse.bbs_mod.camera.clips.misc.TrackerClientClip.shouldRenderHand())
            {
                mchorse.bbs_mod.camera.clips.misc.TrackerClientClip.beginHandRendering();

                /* vanilla renderHand darf laufen, wenn wir die hand sehen wollen */
                return;
            }

            info.cancel();
        }
    }

    /** Gegenstueck zu beginHandRendering — raeumt das setup nach dem zeichnen wieder ab. */
    @Inject(method = "renderHand", at = @At("RETURN"))
    public void onRenderHandEnd(CallbackInfo info)
    {
        if (mchorse.bbs_mod.camera.clips.misc.TrackerClientClip.shouldRenderHand()
            && BBSModClient.getCameraController().getCurrent() != null)
        {
            mchorse.bbs_mod.camera.clips.misc.TrackerClientClip.endHandRendering();
        }
    }

    @Inject(at = @At("HEAD"), method = "renderWorld")
    private void onWorldRenderBegin(CallbackInfo callbackInfo)
    {
        /* afrobama 2026-07-27 — aus dem CML-fork (dort GameRendererMixin:175-183).
         * Gehoert zum hand/HUD-render-lebenszyklus: begin/endLevelRendering klammern
         * den welt-durchgang, damit die hand danach im richtigen zustand gezeichnet wird. */
        if (BBSModClient.getCameraController().getCurrent() != null)
        {
            mchorse.bbs_mod.camera.clips.misc.TrackerClientClip.beginLevelRendering();
        }

        BBSRendering.onWorldRenderBegin();
    }

    @Inject(at = @At("RETURN"), method = "renderWorld")
    private void onWorldRenderEnd(CallbackInfo callbackInfo)
    {
        mchorse.bbs_mod.camera.clips.misc.TrackerClientClip.endLevelRendering();
        BBSRendering.onWorldRenderEnd();
    }

    @Inject(method = "render", at = @At(value = "FIELD", target = "Lnet/minecraft/client/option/GameOptions;hudHidden:Z", opcode = Opcodes.GETFIELD, ordinal = 0), require = 0)
    private void onBeforeHudRendering(RenderTickCounter tickCounter, boolean tick, CallbackInfo info)
    {
        ICameraController current = BBSModClient.getCameraController().getCurrent();

        if (MinecraftClient.getInstance().options.hudHidden && current == null)
        {
            BBSRendering.onRenderBeforeScreen();
        }
    }
}