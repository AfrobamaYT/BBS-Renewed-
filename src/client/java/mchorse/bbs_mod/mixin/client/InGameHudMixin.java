package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.clips.misc.TrackerClientClip;
import mchorse.bbs_mod.camera.controller.ICameraController;
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

        /* Nur der welt-modus blittet hier schon: onRenderBeforeScreen haengt den
         * offscreen-framebuffer ab. Im editor laeuft RunnerCameraController — dort muss
         * der framebuffer bis zum TAIL gebunden bleiben, sonst landet das HUD im
         * haupt-framebuffer statt im viewport. Beide erben CameraWorkCameraController. */
        if (current instanceof PlayCameraController)
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