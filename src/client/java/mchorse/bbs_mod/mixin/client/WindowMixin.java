package mchorse.bbs_mod.mixin.client;

import com.mojang.blaze3d.platform.Window;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Window.class)
public class WindowMixin
{
    @Shadow
    private int width;

    @Shadow
    private int height;

    @Shadow
    private int framebufferWidth;

    @Shadow
    private int framebufferHeight;

    @Shadow
    private int guiScaledWidth;

    @Shadow
    private int guiScaledHeight;

    /* ⚠️ 26.2: guiScale ist von double auf int gewechselt -- und setGuiScale nimmt jetzt
     * ebenfalls einen int. Ein @Shadow mit dem falschen TYP findet das Feld nicht und bricht
     * den Start ("@Shadow field guiScale was not located"). Kein Compilerfehler. */
    @Shadow
    private int guiScale;

    /**
     * While BBS UI is open, its ui_scale setting replaces whatever integer scale
     * vanilla derived from the guiScale option. setScaleFactor() and everything
     * downstream (scaled size, mouse, GUI projection) already operate on doubles,
     * so fractional values work out of the box.
     */
    /**
     * ⛔ FRAKTIONALE GUI-SKALIERUNG IST IN 26.2 NICHT MEHR MOEGLICH -- und das ist kein
     * Portierfehler, sondern eine Entscheidung von Vanilla.
     *
     * <p>Der ganze Sinn dieses Hakens war, dass BBS' ui_scale auch KRUMME Werte annehmen kann
     * (1,5 statt nur 1 oder 2). Das ging, weil {@code guiScale} und {@code setGuiScale} mit
     * {@code double} arbeiteten -- der alte Kommentar sagte es ausdruecklich: "setScaleFactor
     * and everything downstream already operate on doubles, so fractional values work out of
     * the box". In 26.2 sind beide {@code int}. Ein Zwischenwert kann gar nicht mehr
     * transportiert werden.</p>
     *
     * <p>Deshalb rundet dieser Haken jetzt. Ein BBS-ui_scale von 1,5 wird zu 2 -- die
     * Einstellung wirkt weiterhin, aber nicht mehr stufenlos. Das ist ehrlicher als der
     * Haken ersatzlos zu streichen: die Einstellung bleibt bedienbar, sie ist nur groeber.</p>
     *
     * <p>ZURUECKHOLEN hiesse, die Skalierung an Vanilla vorbei zu fuehren (eigene
     * GUI-Projektion), nicht diesen Haken zu reparieren. Eigener Entwurf, kein Nachschlag.</p>
     */
    @ModifyVariable(method = "setGuiScale", at = @At("HEAD"), argsOnly = true)
    private int bbs$overrideScaleFactor(int scaleFactor)
    {
        float custom = BBSModClient.getCustomGUIScale();

        if (custom > 0F)
        {
            /* Same lower bound vanilla's calculateScaleFactor() enforces: keep at
             * least ~320x240 GUI units on screen, so UI stays usable on small windows */
            int max = Math.max(1, Math.min(this.framebufferWidth / 320, this.framebufferHeight / 240));

            return Math.min(Math.max(Math.round(custom), 1), max);
        }

        return scaleFactor;
    }

    @Inject(method = "getScreenWidth", at = @At("HEAD"), cancellable = true)
    public void onGetWidth(CallbackInfoReturnable<Integer> info)
    {
        if (BBSRendering.canReplaceFramebuffer())
        {
            info.setReturnValue(BBSRendering.getVideoWidth());
        }
    }

    @Inject(method = "getScreenHeight", at = @At("HEAD"), cancellable = true)
    public void onGetHeight(CallbackInfoReturnable<Integer> info)
    {
        if (BBSRendering.canReplaceFramebuffer())
        {
            info.setReturnValue(BBSRendering.getVideoHeight());
        }
    }

    @Inject(method = "getWidth", at = @At("HEAD"), cancellable = true)
    public void onGetFramebufferWidth(CallbackInfoReturnable<Integer> info)
    {
        if (BBSRendering.canReplaceFramebuffer())
        {
            info.setReturnValue((int) (BBSRendering.getVideoWidth() * BBSModClient.getOriginalFramebufferScale()));
        }
    }

    @Inject(method = "getHeight", at = @At("HEAD"), cancellable = true)
    public void onGetFramebufferHeight(CallbackInfoReturnable<Integer> info)
    {
        if (BBSRendering.canReplaceFramebuffer())
        {
            info.setReturnValue((int) (BBSRendering.getVideoHeight() * BBSModClient.getOriginalFramebufferScale()));
        }
    }

    @Inject(method = "getGuiScaledWidth", at = @At("HEAD"), cancellable = true)
    public void onGetScaledWidth(CallbackInfoReturnable<Integer> info)
    {
        if (BBSRendering.canReplaceFramebuffer())
        {
            info.setReturnValue((int) (BBSRendering.getVideoWidth() / this.guiScale * BBSModClient.getOriginalFramebufferScale()));
        }
    }

    @Inject(method = "getGuiScaledHeight", at = @At("HEAD"), cancellable = true)
    public void onGetScaledHeight(CallbackInfoReturnable<Integer> info)
    {
        if (BBSRendering.canReplaceFramebuffer())
        {
            info.setReturnValue((int) (BBSRendering.getVideoHeight() / this.guiScale * BBSModClient.getOriginalFramebufferScale()));
        }
    }
}