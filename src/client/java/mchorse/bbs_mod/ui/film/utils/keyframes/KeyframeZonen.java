package mchorse.bbs_mod.ui.film.utils.keyframes;

import com.mcstudio.nano.ZonenMelder;

import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.UIKeyframeDopeSheet;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Scroll;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

/**
 * Klickflaechen des Dope Sheets fuer die Abnahme.
 *
 * <p>⛔ AUS DEN EIGENEN ZAHLEN DES DOPE SHEETS, NICHT NACHGERECHNET. {@code getDopeSheetY},
 * {@code getTrackHeight} und {@code toGraphX} sind dieselben Werte, mit denen es zeichnet.
 * Eine zweite Layoutrechnung waere eine zweite Wahrheit — und ein Messgeraet, das seine
 * eigene Meinung zur Lage hat, meldet Abweichungen, die es selbst erzeugt hat.</p>
 *
 * <p>Gemeldet wird, was der alte Batcher zeichnet und der Elementbaum deshalb NICHT kennt:
 * je Kanalzeile die Beschriftung und die Spur, dazu jeder sichtbare Keyframe.</p>
 */
public final class KeyframeZonen
{
    private KeyframeZonen()
    {
    }

    /**
     * Alle Flaechen des Dope Sheets melden. {@code melder} ist vorher zu beginnen und
     * hinterher zu uebernehmen — das macht der Aufrufer, weil nur er den Zeichenlauf kennt.
     */
    public static void melden(ZonenMelder melder, UIFilmKeyframes keyframes)
    {
        UIKeyframeDopeSheet blatt = keyframes.getDopeSheet();

        if (blatt == null)
        {
            return;
        }

        Area flaeche = keyframes.area;
        int beschriftungB = Math.max(0, keyframes.getLabelWidth());
        int hoehe = (int) blatt.getTrackHeight();

        if (hoehe <= 0)
        {
            return;
        }

        int spurX = flaeche.x + beschriftungB;
        int spurB = flaeche.ex() - spurX;
        java.util.Set<UIKeyframeSheet> gesehen = new java.util.HashSet<>();

        /* Der Beschriftungs-Trenner IST ein Element (UIDraggable) und steht damit schon im
         * Elementbaum. Gemeldet wird trotzdem — mit SEINER eigenen Flaeche, nicht
         * nachgerechnet — damit die Abnahme EINE Liste hat statt zweier Quellen. */
        for (mchorse.bbs_mod.ui.framework.elements.IUIElement kind : keyframes.getChildren())
        {
            if (kind instanceof mchorse.bbs_mod.ui.framework.elements.utils.UIDraggable zieher
                && zieher.isVisible() && zieher.area.w > 0)
            {
                melder.melden("dope/trenner", zieher.area.x, zieher.area.y,
                    zieher.area.w, zieher.area.h);
            }
        }
        Scroll senkrecht = blatt.getYAxis();

        /* Der Scrollbalken wird gezeichnet, ist kein Element — und seine Flaeche liefert
         * der Scroll selbst, also dieselbe, die er auch trifft. */
        if (senkrecht != null && senkrecht.hasScrollbar())
        {
            Area balken = senkrecht.getScrollbarArea();

            zugeschnitten(melder, flaeche, "dope/scroll", balken.x, balken.y, balken.w, balken.h);
        }

        /* ⛔ ZEILE FUER ZEILE ABFRAGEN, NICHT DIE SHEET-LISTE DURCHLAUFEN (2026-08-23
         * gemessen). getDopeSheetY(sheet) faellt bei einer NICHT angeordneten Zeile still
         * auf Versatz 0 zurueck — 19 eingeklappte Kanaele meldeten dadurch alle die
         * oberste Zeile, uebereinander gestapelt. getSheet(y) ist derselbe Treffertest,
         * den das Produkt fuer den Mausklick benutzt: was er hier nennt, trifft ein Klick
         * dorthin auch wirklich. */
        /* Am ECHTEN zeilenraster abtasten, nicht ab flaeche.y: getDopeSheetY() ist die
         * Oberkante der ersten Zeile (inkl. Scroll). Ein eigenes Raster lag daneben und
         * verlor die letzte Zeile, weil ihre Mitte hinter den unteren Rand fiel. */
        for (int oben = blatt.getDopeSheetY(); oben < flaeche.ey(); oben += hoehe)
        {
            int mitte = Math.min(oben + hoehe / 2, flaeche.ey() - 1);

            if (mitte < flaeche.y)
            {
                continue;
            }

            UIKeyframeSheet zeile = blatt.getSheet(mitte);

            if (zeile == null || !gesehen.add(zeile))
            {
                continue;
            }

            String id = "dope/" + zeile.id;

            /* ⛔ DER NAME SAGT, WAS DER KLICK TUT. Ein Klick in die Beschriftungsspalte
             * ruft addKeyframeManually — er VERAENDERT den Film. Ein Abnahmelauf, der
             * blind jede Zone anklickt, wuerde also Keyframes setzen. Der Name warnt. */
            zugeschnitten(melder, flaeche, id + "/label-setzt-key", flaeche.x, oben, beschriftungB, hoehe);
            zugeschnitten(melder, flaeche, id + "/spur", spurX, oben, spurB, hoehe);

            /* Der Aufklapp-Pfeil ist ein EIGENES Klickziel: er toggelt die Gruppe, waehrend
             * ein Klick auf die Zeile daneben einen Keyframe SETZT. Ohne eigene Zone waere
             * das Auf- und Zuklappen fuer die Abnahme unerreichbar — und ein Versuch daneben
             * haette den Film veraendert. Rechteck aus poseTabPfeil, derselben Methode, die
             * der Treffertest benutzt. */
            boolean pfeil = blatt.isPoseTabParent(zeile);

            if ((pfeil || zeile.getIcon() != null) && hoehe >= 12)
            {
                int[] r = blatt.poseTabPfeil(oben, beschriftungB);

                zugeschnitten(melder, flaeche, id + (pfeil ? "/pfeil" : "/symbol"),
                    r[0], r[1], r[2], r[2]);
            }

            if (zeile.channel == null)
            {
                continue;
            }

            for (Object eintrag : zeile.channel.getKeyframes())
            {
                Keyframe rahmen = (Keyframe) eintrag;
                int x = keyframes.toGraphX(rahmen.getTick());

                if (x < spurX || x > flaeche.ex())
                {
                    continue;
                }

                /* Der Griff ist so breit wie hoch — dieselbe Faustregel wie beim Zeichnen. */
                zugeschnitten(melder, flaeche, id + "/key/" + (long) rahmen.getTick(),
                    x - hoehe / 2F, oben, hoehe, hoehe);
            }
        }
    }

    /**
     * Nur den SICHTBAREN teil melden.
     *
     * <p>⛔ Eine zone, die ueber den rand hinausragt, hat ihren mittelpunkt im
     * abgeschnittenen teil — ein klick dorthin landet bei dem, was darunter liegt. Im
     * Nano-weg steht derselbe fall als bezahlte lehre („panel3" unter der Statusleiste);
     * hier ragte die dritte Kanalzeile 3 punkte unter die Editorkante und meldete
     * Klickpunkt y=338 bei Editorende 335.</p>
     */
    private static void zugeschnitten(ZonenMelder melder, Area flaeche,
        String id, float x, float y, float w, float h)
    {
        float x1 = Math.max(x, flaeche.x);
        float y1 = Math.max(y, flaeche.y);
        float x2 = Math.min(x + w, flaeche.ex());
        float y2 = Math.min(y + h, flaeche.ey());

        melder.melden(id, x1, y1, x2 - x1, y2 - y1);
    }
}
