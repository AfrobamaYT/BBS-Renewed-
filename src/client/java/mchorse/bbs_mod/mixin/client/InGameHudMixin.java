package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.clips.misc.TrackerClientClip;
import mchorse.bbs_mod.camera.controller.ICameraController;
import mchorse.bbs_mod.camera.controller.PlayCameraController;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.GuiZeichner;
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
    /**
     * ⚠️ NICHT je Haken einen neuen Zeichner bauen. Beide Haken dieser Klasse arbeiten
     * zusammen -- der eine schiebt das HUD beiseite, der andere holt es zurueck -- und jeder
     * Zeichner fuehrt einen EIGENEN Matrix-Stack. Zwei Zeichner heisst: der Push landet auf
     * einem Stack, den beim Pop niemand mehr kennt ("already at the bottom of the stack").
     * Darum haelt GuiZeichner genau einen je Bild.
     */
    private GuiGraphicsExtractor bbs$zeichnerFuerNeuesBild()
    {
        return GuiZeichner.neuFuerBild(Minecraft.getInstance());
    }

    private GuiGraphicsExtractor bbs$zeichner()
    {
        return GuiZeichner.aktuell(Minecraft.getInstance());
    }

    @Inject(method = "extractRenderState", at = @At(value = "HEAD"), cancellable = true)
    public void render(DeltaTracker tickCounter, boolean bildschirmOffen, boolean hudVersteckt, CallbackInfo info)
    {
        GuiGraphicsExtractor drawContext = this.bbs$zeichnerFuerNeuesBild();

        ICameraController current = BBSModClient.getCameraController().getCurrent();

        /* ⛔ FUELLEN GEHOERT HIERHER, ABHAENGEN AN DEN TAIL (2026-08-02, Aufgabe #19).
         *
         * In 26.2 sammelt extractRenderState erst ALLE UI-Elemente ein und zeichnet danach.
         * Wurde die Vorschau-Textur erst am TAIL gefuellt, war der Blit des Vorschaufeldes
         * laengst eingereiht — das Feld zeigte deshalb immer das Bild des VORIGEN Durchgangs
         * (gemessen per Reihenfolge-Sonden: beim Blit stand die Fuellung auf 132, waehrend
         * sie bei 133 war). Der aktuelle Replay-Umriss darueber sass bei Kamerabewegung
         * daneben, und der Abstand skalierte mit der Bildzeit.
         *
         * Der Welt-Modus (PlayCameraController) macht wie bisher beides auf einmal — dort
         * SOLL der Framebuffer hier schon abgehaengt werden. Im Editor laeuft
         * RunnerCameraController: dort wird jetzt nur GEFUELLT, und das Abhaengen bleibt am
         * TAIL, weil der Framebuffer bis dahin gebunden sein muss (sonst landet das HUD im
         * haupt-framebuffer statt im viewport). Beide erben CameraWorkCameraController. */
        if (current instanceof PlayCameraController)
        {
            BBSRendering.onRenderBeforeScreen();

            if (!TrackerClientClip.shouldRenderHud())
            {
                info.cancel();

                return;
            }
        }

        else
        {
            /* Editor und alles Uebrige: nur fuellen, nicht abhaengen. */
            BBSRendering.fuellen();
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

        /* Nur noch der Schluss — gefuellt wurde bereits am HEAD (siehe dort). */
        BBSRendering.abschliessen();

        if (TrackerClientClip.shouldRenderHud() && BBSModClient.getCameraController().getCurrent() != null)
        {
            TrackerClientClip.endHudRendering(drawContext);
            mchorse.bbs_mod.client.HudSichtbarkeit.verstecken(true);
        }
    }
}