package mchorse.bbs_mod.ui.utils;

import mchorse.bbs_mod.ui.framework.UIContext;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

/**
 * Zeichnet ein Gegenstands- oder Blocksymbol in ein Bedienelement.
 *
 * <h2>⚠️ Was das ist und was es NICHT ist</h2>
 *
 * <p>Das ist Vanillas Inventar-Symbol: dieselbe Dreiviertelansicht, die man im Rucksack sieht,
 * skaliert und in die Mitte des gegebenen Kastens gesetzt. Es ist NICHT die freie
 * dreidimensionale Vorschau, die die Block- und Gegenstandsformen bis 26.2 hatten.
 *
 * <h2>Warum die freie Drehung hier fehlt (Stand 2026-07-31)</h2>
 *
 * <p>Die alte Vorschau nahm die 3D-Matrix der Form und liess Vanillas Sofort-Zeichner den Block
 * hineinmalen. In 26.2 sammelt die Oberflaeche nur ({@code extractRenderState}) und zeichnet
 * danach ({@code GuiRenderer.render}) -- ein sofort geoeffneter Durchgang landet UNTER dem
 * gesamten GUI. Der richtige Weg fuehrt ueber {@code Batcher2D.netz}, und der braucht eine
 * Pipeline mit dem Vertexformat der Backvierecke plus die Blockatlas-Textur. Das ist eine eigene
 * Baustelle, keine Zeile in diesem Aufruf.
 *
 * <p>Bis dahin gilt: das Symbol zeigt den RICHTIGEN Block bzw. Gegenstand in der gewohnten
 * Ansicht -- die Skalierung der Form wirkt, ihre Drehung nicht. Das ist ein sichtbarer Rueckgang
 * gegenueber 1.21.1 und steht darum hier und nicht nur in einem Aufgabenzettel.
 *
 * <p>⚠️ Ein Blockzustand mit Sonderstellung (eine bestimmte Treppenrichtung, ein halber
 * Doppelblock) laesst sich als Gegenstand NICHT ausdruecken -- der Gegenstand kennt nur den
 * Block, nicht seinen Zustand. Die Vorschau zeigt dann den Grundzustand.
 */
public final class UISymbol
{
    /** Kantenlaenge, in der Vanilla ein Inventarsymbol zeichnet. */
    public static final int GROESSE = 16;

    private UISymbol()
    {}

    /**
     * Setzt das Symbol mittig in den Kasten und skaliert es mit.
     *
     * @param skalierung die uiScale der Form; 1 fuellt den Kasten so weit wie moeglich
     */
    public static void mittig(UIContext context, ItemStack stapel, int x1, int y1, int x2, int y2, float skalierung)
    {
        if (stapel == null || stapel.isEmpty())
        {
            return;
        }

        GuiGraphics zeichner = context.batcher.getContext();

        if (zeichner == null)
        {
            return;
        }

        float kasten = Math.min(x2 - x1, y2 - y1);
        float faktor = Math.max(kasten / GROESSE * skalierung, 0.01F);
        float mitteX = (x1 + x2) / 2F;
        float mitteY = (y1 + y2) / 2F;

        Matrix3x2fStack pose = zeichner.pose();

        pose.pushMatrix();

        /* Erst an die Mitte, dann skalieren, dann das Symbol um seine halbe Kantenlaenge
         * zuruecksetzen -- so bleibt der Mittelpunkt beim Skalieren stehen. */
        pose.translate(mitteX, mitteY);
        pose.scale(faktor, faktor);

        zeichner.item(stapel, -GROESSE / 2, -GROESSE / 2);

        pose.popMatrix();
    }
}
