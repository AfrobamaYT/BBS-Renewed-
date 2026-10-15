package mchorse.bbs_mod.ui.framework.elements.utils.vorschau;

import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.joml.Matrix4f;

/**
 * Die Modellvorschau, eingefroren zum Zeitpunkt des Einreihens.
 *
 * <h2>Warum das Modell ueberhaupt hier durch muss</h2>
 *
 * <p>Bis 1.21.1 zeichnete die Vorschau ihr Modell sofort, direkt nach dem Panel-Hintergrund
 * -- die Reihenfolge stimmte, weil beides im selben Durchlauf passierte. 26.2 sammelt die
 * Oberflaeche erst und zeichnet sie danach. Ein Sofortzug landet damit VOR dem gesamten GUI
 * und wird vom Hintergrund uebermalt.
 *
 * <p>Gemessen (Aufgabe 96): das Modell wurde 574 Mal in vier Sekunden gezeichnet und war
 * trotzdem nie zu sehen -- 98,53 % der Vorschauflaeche trugen die Farbe des Kastens aus
 * {@code UIModelEditorPanel:303}. Nicht die Geometrie fehlte, die Reihenfolge stimmte nicht.
 *
 * <p>Der Bild-im-Bild-Weg loest genau das: eigene Textur, eigene Projektion, eigener
 * Tiefenpuffer, und der fertige Ausschnitt wird IM GUI-strom geblittet -- also nach dem
 * Hintergrund. Am Bodengitter ist der Weg bereits bewiesen (41ef900aa, f19f98364).
 *
 * @param form       was gezeichnet wird
 * @param entity     die Traegerfigur der Vorschau (Posen, Animationszustand)
 * @param kamera     die Vorschaukamera -- als Referenz, weil der Formkontext sie selbst
 *                   braucht; sie gehoert dem Element und wird im selben Bild gezeichnet
 * @param projektion Kameraprojektion, KOPIERT. Wird dem Zeichendurchgang untergeschoben,
 *                   damit die Perspektive nichts je Ecke kostet (siehe VorschauGitterRenderer)
 * @param ansicht    Modell-Ansichts-Matrix, KOPIERT (der Stapel laeuft weiter)
 * @param uebergang  Teilbild zwischen zwei Ticks
 */
public record VorschauModellZustand(
    Form form,
    IEntity entity,
    Camera kamera,
    Matrix4f projektion,
    Matrix4f ansicht,
    float uebergang,
    int x0,
    int y0,
    int x1,
    int y1,
    ScreenRectangle scissorArea,
    ScreenRectangle bounds
) implements PictureInPictureRenderState
{
    /**
     * ⚠️ Massstab 1: die Perspektive steckt in {@link #projektion}, nicht hier. Ein zweiter
     * Massstab wuerde ein zweites Mal skalieren.
     */
    @Override
    public float scale()
    {
        return 1F;
    }
}
