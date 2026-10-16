package mchorse.bbs_mod.ui.framework.elements.utils;

import mchorse.bbs_mod.ui.framework.UISkala;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;

import java.util.Deque;

/**
 * Ein {@code Batcher2D#clip}-Schritt: Vanillas Ausschnitt und der eigene Spiegel.
 *
 * <p>Der Spiegel rechnet im selben Raum wie Vanillas Ausschnittstapel, also nach der Matrix
 * ({@code enableScissor -> transformAxisAligned(pose)}). Jeder eigene Renderzustand nennt sein
 * Schnittrechteck aus diesem Spiegel, Vanillas Zustaende ihres aus dem eigenen Stapel; beide
 * muessen derselbe Raum sein.</p>
 *
 * <p>⛔ NUR DIE WURZEL, NICHT DIE GANZE ZEICHENMATRIX. {@code clip} bekommt GLOBALE Einheiten
 * ({@code UIContext.globalX} zieht den Scroll-Versatz schon ab), die Zeichenmatrix enthaelt
 * denselben Versatz aber noch einmal ({@code UIContext.shiftX -> translate}); durch sie
 * gerechnet laege jeder Ausschnitt in einer gescrollten Liste um den Versatz daneben. Die
 * Wurzel ist die Verkleinerung des stufenlosen UI-Scales ({@link UISkala#rest()}), sonst
 * nichts -- bei Rest 1 ist der Spiegel damit genau der alte globale, auf den sich
 * {@code Miniaturschlange} verlaesst.</p>
 *
 * <p>⛔ VANILLA MUSS AUF DASSELBE RECHTECK KOMMEN WIE DER SPIEGEL. Frueher bekam Vanilla das
 * rohe Rechteck auch dann, wenn der Schnitt leer war -- beim Scrollen in einem Dock
 * verschwanden so die Knoepfe des ganzen Docks (vom Nutzer gemeldet). Ein leerer Schnitt geht
 * gar nicht an Vanilla ({@link LeererSchnitt}); der Spiegel traegt ihn trotzdem.</p>
 */
final class SchnittSpiegel
{
    private SchnittSpiegel()
    {}

    static void schneiden(GuiGraphicsExtractor context, Deque<ScreenRectangle> spiegel, int x, int y, int w, int h)
    {
        float rest = UISkala.rest();
        Matrix3x2f wurzel = new Matrix3x2f().scaling(rest, rest);
        ScreenRectangle oben = new ScreenRectangle(x, y, w, h).transformAxisAligned(wurzel);
        ScreenRectangle rect = LeererSchnitt.sichtbar(oben, LeererSchnitt.bildschirm(context));
        ScreenRectangle current = spiegel.peek();

        /* Dieselbe Verschachtelung wie bei Vanilla: ein innerer Schnitt weitet nie einen aeusseren. */
        if (current != null)
        {
            ScreenRectangle intersection = current.intersection(rect);

            rect = intersection == null ? ScreenRectangle.empty() : intersection;
        }

        /* Vanilla bekommt das Spiegel-Rechteck selbst, im Bildraum unter der Einheitsmatrix --
         * keine Rueckrechnung in rohe Einheiten. Die waere zweimal abgerundet
         * ({@code transformAxisAligned}: floor auf Ursprung UND Breite, Bytecode 26.3) und traf
         * bei Rest 0,8333 einen Pixel daneben; ein 1-px-Schnitt wurde so leer. */
        ScreenRectangle anVanilla = rect.width() > 0 && rect.height() > 0 ? rect
            : LeererSchnitt.ersatzPunkt(spiegel, oben.left(), oben.top(), context.guiWidth(), context.guiHeight());
        Matrix3x2fStack pose = context.pose();

        pose.pushMatrix();
        pose.identity();
        context.enableScissor(anVanilla.left(), anVanilla.top(), anVanilla.right(), anVanilla.bottom());
        pose.popMatrix();
        spiegel.push(rect);
    }
}
