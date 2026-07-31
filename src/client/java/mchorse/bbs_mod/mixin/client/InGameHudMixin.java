package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.clips.misc.TrackerClientClip;
import mchorse.bbs_mod.camera.controller.ICameraController;
import mchorse.bbs_mod.camera.controller.PlayCameraController;
import mchorse.bbs_mod.client.BBSRendering;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.2: {@code Gui.render} heisst {@code extractRenderState}, und das ist mehr als ein Name.
 * Das GUI ist zweiphasig geworden -- diese Methode SAMMELT in einen {@code GuiRenderState},
 * gezeichnet wird spaeter daraus. Beide Haken sitzen weiterhin richtig (HEAD = vor allem
 * anderen, TAIL = nachdem Vanilla seins eingesammelt hat).
 *
 * <p>⚠️ Der {@code GuiGraphicsExtractor} wird NICHT mehr hereingereicht. Er wird auf dem
 * LEBENDEN Zustand gebaut, den {@link GuiRenderStateAccessor} aus {@code Gui} holt -- ein
 * selbst gebauter Zustand waere uebersetzbar und unsichtbar, weil ihn niemand zeichnet.</p>
 *
 * <p>Die zwei zusaetzlichen booleans sind Vanillas eigene Schalter (Bildschirm offen,
 * HUD versteckt); wir werten sie nicht aus, muessen sie aber in der Signatur fuehren, sonst
 * findet der Injektor sein Ziel nicht.</p>
 */
@Mixin(Gui.class)
public class InGameHudMixin
{
    /** Zeichner auf dem lebenden GUI-Zustand -- siehe Klassendoku. */
    private GuiGraphicsExtractor bbs$zeichner()
    {
        Minecraft mc = Minecraft.getInstance();

        return new GuiGraphicsExtractor(mc,
            ((GuiRenderStateAccessor) mc.gui).bbs$getGuiRenderState(),
            mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
    }

    @Inject(method = "extractRenderState", at = @At(value = "HEAD"), cancellable = true)
    public void render(DeltaTracker tickCounter, boolean bildschirmOffen, boolean hudVersteckt, CallbackInfo info)
    {
        GuiGraphicsExtractor drawContext = this.bbs$zeichner();

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

        mchorse.bbs_mod.client.HudSichtbarkeit.verstecken(false);
        TrackerClientClip.beginHudRendering(drawContext);
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    public void onRenderEnd(DeltaTracker tickCounter, boolean bildschirmOffen, boolean hudVersteckt, CallbackInfo info)
    {
        GuiGraphicsExtractor drawContext = this.bbs$zeichner();

        BBSRendering.onRenderBeforeScreen();

        if (TrackerClientClip.shouldRenderHud() && BBSModClient.getCameraController().getCurrent() != null)
        {
            TrackerClientClip.endHudRendering(drawContext);
            mchorse.bbs_mod.client.HudSichtbarkeit.verstecken(true);
        }
    }
}