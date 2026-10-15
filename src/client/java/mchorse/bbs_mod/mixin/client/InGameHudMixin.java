package mchorse.bbs_mod.mixin.client;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
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
 * <p>⚠️ Der {@code GuiGraphics} wird NICHT mehr hereingereicht. Er wird auf dem
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
    /**
     * ⚠️ NICHT je Haken einen neuen Zeichner bauen. Beide Haken dieser Klasse arbeiten
     * zusammen -- der eine schiebt das HUD beiseite, der andere holt es zurueck -- und jeder
     * Zeichner fuehrt einen EIGENEN Matrix-Stack. Zwei Zeichner heisst: der Push landet auf
     * einem Stack, den beim Pop niemand mehr kennt ("already at the bottom of the stack").
     * Darum haelt GuiZeichner genau einen je Bild.
     */
    
    
    @Inject(method = "extractRenderState", at = @At(value = "HEAD"), cancellable = true)
    public void render(DeltaTracker tickCounter, boolean bildschirmOffen, boolean hudVersteckt, CallbackInfo info)
    {
        if (MixinBruecke.rufeUndAbbrechen("HudZeichnenLogik", "anfang"))
        {
            info.cancel();
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    public void onRenderEnd(DeltaTracker tickCounter, boolean bildschirmOffen, boolean hudVersteckt, CallbackInfo info)
    {
        MixinBruecke.rufe("HudZeichnenLogik", "ende");
    }
}
