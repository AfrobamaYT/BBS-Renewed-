package mchorse.bbs_mod.ui.framework.elements.utils.vorschau;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;

import java.util.List;

/**
 * ALLE Formvorschauen eines Bildes in EINER bildschirmgrossen Textur.
 *
 * <h2>Warum eine gemeinsame Textur und nicht eine je Zelle</h2>
 *
 * <p>Der erste Anlauf gab jeder Miniatur ihre eigene, zellgrosse Textur. Die Modelle
 * erschienen dadurch zwar — vorher war das Morph-Panel leer —, aber zu klein und teils am
 * unteren Bildrand. Der Grund ist der Bezugsrahmen: {@code renderInUI} rechnet ueber
 * {@code getUIMatrix} in BILDSCHIRM-pixeln (Zellmitte, Zellhoehe, Mausposition), waehrend
 * eine zellgrosse Textur ihren eigenen Ursprung hat. Das meiste lag damit ausserhalb der
 * kleinen Textur.</p>
 *
 * <p>⛔ DER NAHELIEGENDE AUSWEG IST TOEDLICH. Den PiP-Stapel per
 * {@code ui.last().pose().set(stapel.last().pose())} in den UI-Stapel zu kopieren und um den
 * Zellursprung zu verschieben riss die Instanz NATIV — kein Java-Fehler, kein
 * Absturzbericht, das Log endet mitten im Bild. Nicht noch einmal so versuchen.</p>
 *
 * <p>Eine bildschirmgrosse Textur loest dasselbe Problem ohne jeden Eingriff in den Stapel:
 * Bildschirmkoordinaten sind dort GUELTIG, {@code getUIMatrix} braucht keine Umrechnung, und
 * der Mausbezug (die Figur dreht sich zum Zeiger) bleibt richtig. Nebenbei kostet es EINEN
 * Texturwechsel je Bild statt einem je Vorschau — das Morph-Panel zeigt ein Dutzend.</p>
 */
public record MiniaturSammelZustand(
    List<MiniaturZustand> eintraege,
    int x0,
    int y0,
    int x1,
    int y1,
    ScreenRectangle scissorArea,
    ScreenRectangle bounds
) implements PictureInPictureRenderState
{
    /** Massstab 1: die Textur deckt den Bildschirm 1:1 ab, es gibt nichts zu skalieren. */
    @Override
    public float scale()
    {
        return 1F;
    }
}
