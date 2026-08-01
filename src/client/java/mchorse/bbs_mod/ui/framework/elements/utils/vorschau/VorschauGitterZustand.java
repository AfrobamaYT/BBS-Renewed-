package mchorse.bbs_mod.ui.framework.elements.utils.vorschau;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.joml.Matrix4f;

/**
 * Was gezeichnet werden soll, eingefroren zum Zeitpunkt des Einreihens.
 *
 * <h2>Warum das ein eigener Zustand ist und kein Zeichenaufruf</h2>
 *
 * <p>26.2 zeichnet Bild-in-Bild-Inhalte NICHT dann, wenn ein Bedienelement es verlangt,
 * sondern wenn der Bildgraph an der Reihe ist. Zwischen beidem liegt eine Phase, in der
 * globaler Zustand (Projektion, Ansichtsmatrix, Licht) laengst wieder abgeraeumt ist.
 * Deshalb muss ALLES, was zum Zeichnen noetig ist, hier drinstehen -- ein Verweis auf
 * "den aktuellen Kamerazustand" waere zum Zeichenzeitpunkt falsch.
 *
 * <p>Genau daran scheiterten zwei Fix-Versuche am 2026-07-31 (Aufgabe 96): die Geometrie
 * wurde korrekt eingereiht, aber mit einer Projektion gezeichnet, die inzwischen eine
 * andere war. Ein Bild-in-Bild-Renderer bringt seine eigene Projektion mit -- das ist der
 * Unterschied.
 *
 * @param projektion die Kameraprojektion der Vorschau, KOPIERT. Getrennt von der Ansicht,
 *                   weil der Zeichner beides einzeln braucht: entweder er schiebt sie dem
 *                   Durchgang unter, oder er rechnet die Teilung durch w selbst.
 * @param ansicht     die fertige Modell-Ansichts-Matrix, KOPIERT (der Stapel wird
 *                   weiterbenutzt, eine Referenz waere spaeter veraendert)
 * @param x0         linke Kante der Vorschauflaeche im GUI-Raum
 * @param y0         obere Kante
 * @param x1         rechte Kante
 * @param y1         untere Kante
 * @param scissorArea der Beschneidungsbereich des Elternelements, oder {@code null}
 * @param bounds     die Flaeche fuer Keulen und Sortierung
 */
public record VorschauGitterZustand(
    Matrix4f projektion,
    Matrix4f ansicht,
    int x0,
    int y0,
    int x1,
    int y1,
    ScreenRectangle scissorArea,
    ScreenRectangle bounds
) implements PictureInPictureRenderState
{
    /**
     * ⚠️ Der Massstab ist 1: die Vorschau rechnet ihre eigene Kameraprojektion bereits in
     * {@link #ansicht} ein. Ein zweiter Massstab hier wuerde sie ein zweites Mal skalieren.
     */
    @Override
    public float scale()
    {
        return 1F;
    }
}
