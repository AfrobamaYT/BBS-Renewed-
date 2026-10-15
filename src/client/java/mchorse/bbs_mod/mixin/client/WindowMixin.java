package mchorse.bbs_mod.mixin.client;

import com.afrobama.blendertransform.FensterGroesseLogik;
import com.afrobama.bbstools.client.blender.MixinBruecke;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public class WindowMixin
{
    //? if >=26.3 {
    /*@Inject(method = "handleEvent(Lorg/lwjgl/sdl/SDL_Event;)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void bbs$onlyOwnWindow(org.lwjgl.sdl.SDL_Event event, CallbackInfo info) throws Throwable
    {
        if ((Boolean) MixinBruecke.rufePflicht("SdlWindowEventLogik", "foreign", (Window) (Object) this, event))
        {
            info.cancel();
        }
    }

    *///?}
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
        /* framebufferWidth/Height sind @Shadow — sie gehen als wert rueber, nicht als feld. */
        return FensterGroesseLogik.skalierung(
            scaleFactor, this.framebufferWidth, this.framebufferHeight);
    }

    @ModifyReturnValue(method = "getScreenWidth", at = @At("RETURN"))
    private int bbs$overrideScreenWidth(int original)
    {
        return FensterGroesseLogik.bildschirmBreite(original);
    }

    @ModifyReturnValue(method = "getScreenHeight", at = @At("RETURN"))
    private int bbs$overrideScreenHeight(int original)
    {
        return FensterGroesseLogik.bildschirmHoehe(original);
    }

    @ModifyReturnValue(method = "getWidth", at = @At("RETURN"))
    private int bbs$overrideFramebufferWidth(int original)
    {
        return FensterGroesseLogik.pufferBreite(original);
    }

    @ModifyReturnValue(method = "getHeight", at = @At("RETURN"))
    private int bbs$overrideFramebufferHeight(int original)
    {
        return FensterGroesseLogik.pufferHoehe(original);
    }

    @ModifyReturnValue(method = "getGuiScaledWidth", at = @At("RETURN"))
    private int bbs$overrideScaledWidth(int original)
    {
        return FensterGroesseLogik.skalierteBreite(original, this.guiScale);
    }

    @ModifyReturnValue(method = "getGuiScaledHeight", at = @At("RETURN"))
    private int bbs$overrideScaledHeight(int original)
    {
        return FensterGroesseLogik.skalierteHoehe(original, this.guiScale);
    }
}
