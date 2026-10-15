package mchorse.bbs_mod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Der Oberflaechen-Zeichner DIESES Bildes -- einer, nicht je Aufrufstelle einer.
 *
 * <h2>Warum das eine eigene Klasse ist und kein {@code new} an Ort und Stelle</h2>
 *
 * <p>In 26.2 gibt es {@code GuiGraphics} nicht mehr; wer in die Oberflaeche zeichnen will,
 * baut sich einen {@link GuiGraphicsExtractor} um den laufenden {@code GuiRenderState}.
 * Der Aufbau ist billig, und genau das verleitet dazu, ihn an jeder Stelle neu zu machen.
 *
 * <p>⛔ DAS GEHT SCHIEF, SOBALD ZWEI STELLEN ZUSAMMENARBEITEN. Jeder Zeichner fuehrt einen
 * EIGENEN Matrix-Stack ({@code org.joml.Matrix3x2fStack}). Wer auf dem einen
 * {@code pushMatrix()} ruft und auf dem anderen {@code popMatrix()}, poppt einen leeren
 * Stack -- {@code IllegalStateException: already at the bottom of the stack}.
 *
 * <p>Genau so ist es passiert: {@code TrackerClientClip.beginHudRendering} schiebt das HUD
 * am Anfang von {@code Gui.extractRenderState} beiseite, {@code endHudRendering} holt es am
 * Ende zurueck. Beide Haken bauten sich ihren eigenen Zeichner, also lag der Push auf einem
 * Stack, den beim Pop niemand mehr kannte.
 *
 * <p>Diese Klasse haelt darum genau einen Zeichner pro Bild. {@link #neuFuerBild(Minecraft)}
 * gehoert an den Bildanfang, {@link #aktuell(Minecraft)} ueberall sonst hin.
 */
public final class GuiZeichner
{
    private static GuiGraphicsExtractor aktuell;

    /**
     * Baut den Zeichner fuer ein neues Bild und merkt ihn.
     *
     * <p>Gehoert AN DEN BILDANFANG (der HEAD-Haken auf {@code Gui.extractRenderState}) und
     * nirgendwo sonst hin: jeder Aufruf wirft den Matrix-Stand des vorherigen Zeichners weg.
     */
    public static GuiGraphicsExtractor neuFuerBild(Minecraft mc)
    {
        aktuell = bauen(mc);

        return aktuell;
    }

    /**
     * Der Zeichner dieses Bildes.
     *
     * <p>Gibt es noch keinen -- etwa weil ein Weg ausserhalb der Oberflaechenphase zeichnet --,
     * entsteht einer. Der hat dann seinen eigenen, frischen Matrix-Stack; das ist richtig,
     * solange derselbe Weg auch wieder aufraeumt.
     */
    public static GuiGraphicsExtractor aktuell(Minecraft mc)
    {
        if (aktuell == null)
        {
            aktuell = bauen(mc);
        }

        return aktuell;
    }

    private static GuiGraphicsExtractor bauen(Minecraft mc)
    {
        return new GuiGraphicsExtractor(mc,
            ((mchorse.bbs_mod.mixin.client.GuiRenderStateAccessor) mc.gui).bbs$getGuiRenderState(),
            mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
    }

    private GuiZeichner()
    {}
}
