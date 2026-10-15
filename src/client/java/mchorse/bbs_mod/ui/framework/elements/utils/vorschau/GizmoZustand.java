package mchorse.bbs_mod.ui.framework.elements.utils.vorschau;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.joml.Matrix4f;

/**
 * Das Gizmo, eingefroren zum Zeitpunkt des Einreihens.
 *
 * <h2>Warum das Gizmo ueberhaupt hier durch muss</h2>
 *
 * <p>Bis 1.21.1 zeichnete {@code Gizmo.renderInterface} sofort auf das Hauptbild. 26.2
 * zeichnet die Oberflaeche in ZWEI Phasen: der UI-Durchlauf SAMMELT nur, {@code
 * GuiRenderer.render()} zeichnet danach. Ein sofort geoeffneter Renderdurchgang landet
 * deshalb UNTER dem gesamten GUI und wird vom Panel-Hintergrund uebermalt -- das Gizmo war
 * seit dem Port unsichtbar.
 *
 * <p>{@code Batcher2D.netz} reicht hier nicht: das Gizmo bringt eine EIGENE Projektion ueber
 * seine Flaeche mit und sortiert seine Griffe echt in der Tiefe ({@code drawOccludedGizmo}
 * mit einem Tiefenwand-Vorabdurchgang). Eingereihte Netze bekaemen die GUI-Projektion und
 * keinen eigenen Tiefenpuffer. Der Bild-im-Bild-Weg bringt beides mit.
 *
 * @param projektion         die Projektion des Sichtfelds, KOPIERT. Sie bildet den normierten
 *                           Geraeteraum auf die Flaeche ab -- frueher tat das der auf
 *                           {@code area} gesetzte Zeichenbereich, jetzt IST die Textur die
 *                           Flaeche.
 * @param ansicht            die eingefangene Modellansicht des Gizmos, KOPIERT (der Stapel
 *                           laeuft weiter). ⛔ GETRENNT von der Projektion: zusammen-
 *                           multipliziert laege die Perspektive im Stapel, und die Teilung
 *                           durch w geschieht erst danach -- siehe VorschauGitterRenderer.
 * @param viewportSkalierung {@code menu.height / area.h}; haelt das Gizmo unabhaengig von der
 *                           Groesse des Sichtfelds gleich gross auf dem Bildschirm. Reist
 *                           mit, damit die Zeichenphase nicht auf einen Wert angewiesen ist,
 *                           den der UI-Durchlauf hinterlassen hat.
 */
public record GizmoZustand(
    Matrix4f projektion,
    Matrix4f ansicht,
    float viewportSkalierung,
    int x0,
    int y0,
    int x1,
    int y1,
    ScreenRectangle scissorArea,
    ScreenRectangle bounds
) implements PictureInPictureRenderState
{
    /**
     * ⚠️ MASSSTAB = GUI-SKALIERUNG, nicht 1.
     *
     * <p>Der Massstab bestimmt hier NUR die Aufloesung der Textur (Texturpunkte je
     * GUI-Einheit) -- die Perspektive steckt in {@link #projektion}, und die
     * Bildpunkt-Abbildung des Grundzeichners wird im Zeichner ohnehin verworfen.
     *
     * <p>Mit 1 kaeme das Gizmo bei GUI-Skalierung 2 in halber Aufloesung heraus und waere
     * sichtbar treppig. Der frueher sofort gezeichnete Zug lief in Bildpunkten des
     * Hauptbildes; die GUI-Skalierung stellt genau diese Feinheit wieder her.
     */
    @Override
    public float scale()
    {
        return mchorse.bbs_mod.BBSModClient.getGUIScale();
    }
}
