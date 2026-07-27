package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.clips.misc.TrackerClientClip;
import mchorse.bbs_mod.camera.controller.ICameraController;
import mchorse.bbs_mod.camera.controller.CameraWorkCameraController;
import mchorse.bbs_mod.camera.controller.PlayCameraController;
import mchorse.bbs_mod.client.BBSRendering;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class InGameHudMixin
{
    @Inject(method = "render", at = @At(value = "HEAD"), cancellable = true)
    public void render(DrawContext drawContext, RenderTickCounter tickCounter, CallbackInfo info)
    {
        ICameraController current = BBSModClient.getCameraController().getCurrent();

        /* afrobama 2026-07-27 — 1:1 aus dem CML-fork (dort InGameHudMixin:22-54).
         *
         * fs cancelte das HUD UNBEDINGT, sobald ein film lief — der "show HUD"-wunsch
         * eines tracker-clips kam nie an. Wie bei der hand ist das begin/endHudRendering-
         * paar pflicht, nicht schmuck: es klammert das zeichnen und setzt hudHidden
         * danach zurueck, damit der zustand des nutzers erhalten bleibt. */
        if (current instanceof CameraWorkCameraController)
        {
            BBSRendering.onRenderBeforeScreen();

            if (!TrackerClientClip.shouldRenderHud())
            {
                info.cancel();

                return;
            }
        }

        if (!TrackerClientClip.shouldRenderHud())
        {
            return;
        }

        if (current == null)
        {
            return;
        }

        MinecraftClient.getInstance().options.hudHidden = false;
        TrackerClientClip.beginHudRendering(drawContext);
    }

    @Inject(method = "render", at = @At("TAIL"))
    public void onRenderEnd(DrawContext drawContext, RenderTickCounter tickCounter, CallbackInfo info)
    {
        BBSRendering.onRenderBeforeScreen();

        if (TrackerClientClip.shouldRenderHud() && BBSModClient.getCameraController().getCurrent() != null)
        {
            TrackerClientClip.endHudRendering(drawContext);
            MinecraftClient.getInstance().options.hudHidden = true;
        }
    }
}