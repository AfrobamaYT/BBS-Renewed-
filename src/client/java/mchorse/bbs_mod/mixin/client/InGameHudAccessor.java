package mchorse.bbs_mod.mixin.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Access to InGameHud.renderMainHud for the film viewport.
 *
 * render() bails out when a screen covers the display, which is always the case with the
 * editor open. renderMainHud draws hotbar, health, hunger and crosshair without those
 * guards — exactly what ends up in the exported video.
 */
@Mixin(Gui.class)
public interface InGameHudAccessor
{
    /* ⚠️ 26.2: renderHotbarAndDecorations gibt es NICHT MEHR -- und auch keine andere
     * Einzelmethode, die "die HUD zeichnet". Das GUI ist zweiphasig geworden: Gui.
     * extractRenderState(DeltaTracker, boolean, boolean) SAMMELT alles in den GuiRenderState,
     * gezeichnet wird spaeter daraus. Die Hotbar ist dabei zu einem benannten Element geworden
     * (VanillaHudElements.HOTBAR).
     *
     * Die zwei Booleans sind aus GameRenderers Aufruf abgelesen (javap -c), nicht geraten.
     *
     * ⛔ ZU PRUEFEN AM LAUFENDEN SPIEL, ERSTER PUNKT DER LISTE: der Aufrufer
     * (UIFilmPreview) will die HUD IN SEIN BILD. Sammeln heisst aber, dass sie in den
     * GEMEINSAMEN Zustand geht und zur normalen Zeit gezeichnet wird -- moeglicherweise
     * landet sie damit auf dem Bildschirm statt im Film. Das ist genau die Sorte stille
     * Falschheit, die man nicht uebersetzen sieht; deshalb steht es hier und in der
     * Aufrufstelle als offene Frage, nicht als erledigt. */
    @Invoker("extractRenderState")
    void bbs$renderMainHud(DeltaTracker counter, boolean a, boolean b);
}
