package mchorse.bbs_mod.ui.framework.elements.utils.vorschau;

import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.ui.framework.UIContext;
import net.minecraft.client.gui.navigation.ScreenRectangle;

import java.util.ArrayList;
import java.util.List;

/**
 * Sammelt die Formvorschauen eines Bildes und gibt sie am Ende als EINEN Bild-im-Bild-Zug ab.
 *
 * <p>{@link FormRenderer#renderUI} reiht hier ein statt sofort zu zeichnen; der
 * Oberflaechen-Durchlauf gibt am Ende ab. Warum ueberhaupt Bild im Bild und warum EINE
 * gemeinsame Textur steht in {@link MiniaturSammelZustand}.</p>
 *
 * <p>⚠️ Kein Zustand ueber Bildgrenzen: {@link #abgeben} leert die Liste immer, auch wenn
 * nichts eingereiht wurde. Ein Rest aus dem letzten Bild zeichnete sonst Vorschauen von
 * Elementen, die es nicht mehr gibt — und haelt deren {@link UIContext} am Leben.</p>
 */
public final class Miniaturschlange
{
    private static final List<MiniaturZustand> WARTEND = new ArrayList<>();

    private Miniaturschlange()
    {}

    public static void einreihen(FormRenderer<?> zeichner, UIContext kontext,
        int x1, int y1, int x2, int y2)
    {
        /* ⛔ GUI-EINHEITEN REIN, BILDPUNKTE RAUS (2026-08-04 gemessen).
         *
         * Die Aufrufer geben Element-Koordinaten, also GUI-Einheiten. Gezeichnet wird aber in
         * eine bildschirmgrosse Textur, die in BILDPUNKTEN entsteht — und getUIMatrix nimmt
         * die Zahlen unveraendert als Verschiebung und als Massstab ((y2-y1)/2.5). Ohne die
         * Umrechnung landet jede Miniatur bei ihren GUI-Zahlen als Bildpunkten: halbe
         * Position, halbe Groesse.
         *
         * Gemessen bei GUI-Skalierung 2, drei Kacheln, immer dasselbe Verhaeltnis:
         *   eingereiht GUI (365,29)-(405,69)  ->  erschien bei Bildpunkt (365,29)-(405,69)
         *   eingereiht GUI (85,120)-(125,160) ->  erschien bei Bildpunkt (85,120)-(125,160)
         * Das ist die Miniatur, die der Nutzer oben links im Viewport sah: das Abzeichen
         * neben dem Replay-Namen, um Faktor 2 nach oben links gerutscht. */
        /* Minecrafts Faktor, nicht der effektive: die Pose unten traegt die Wurzel des
         * stufenlosen UI-Scales schon (UISkala), ihr Ergebnis ist Minecrafts GUI-Raum. */
        float s = (float) net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale();
        float rest = mchorse.bbs_mod.ui.framework.UISkala.rest();

        /* ⛔ ELEMENTRAUM REIN, GLOBALER RAUM RAUS (2026-08-11 an der laufenden Instanz gemessen).
         *
         * Ein Scroll verschiebt den Zeichenraum, nicht die Element-Koordinaten. `Batcher2D#clip`
         * rechnet deshalb mit `globalX/globalY` um -- `einreihen` bekam die Zelle roh und schnitt
         * sie gegen genau diesen globalen Schnitt. Sichtbare Mob-Kachel im Morph-Panel:
         *     roh y=820  global y=70  ->  Kachel 1640..1800 px, Schnitt 140..300 px, verworfen
         * Bei Scroll 0 fallen beide Raeume zusammen: die erste Reihe stand, alles darunter war
         * weg (vom Nutzer als "beim Runterscrollen verschwinden die meisten Icons" gemeldet). */
        /* ⛔ DIE FLAECHENMATRIX GEHOERT DAZU — UND NUR SIE (2026-08-11, zweimal gemessen).
         *
         * `UIOverlayPanel` laesst ein Panel aufgehen, indem es um dessen Mitte auf den
         * Flaechenstapel skaliert (`getContext().pose()`). `Batcher2D#quad` und `#blit`
         * kopieren diese Matrix in jeden Zug -- eine eingereihte Miniatur bekam sie nie und
         * blieb waehrend der ganzen Aufblend-Animation an ihrer ENDposition stehen (vom
         * Nutzer am Quick-Replay-Waehler gemeldet, am Miniatur-Schwerpunkt je Bild belegt).
         *
         * ⛔ NICHT mit globalX/globalY KOMBINIEREN. `UIContext#shiftY` traegt den Scroll in
         * BEIDE: in die Pose (`pose().translate(0,-y)`) UND in den Viewport-Stapel, aus dem
         * `globalY` liest. Der erste Fix rechnete `pose * global(...)` -- der Scroll wirkte
         * doppelt, und ab Scroll > 0 lagen alle eingereihten Kacheln oberhalb des Bildes
         * (tools/kachel.py fand die Billboard-Kachel bei Scroll 200 nicht mehr; die flachen
         * Kacheln daneben sassen richtig). Die Pose ist bereits die VOLLE Element-nach-
         * Bildschirm-Abbildung -- exakt die, mit der die flachen Zuege derselben Zellen
         * zeichnen. Also: rohe Elementkoordinaten, nur durch die Pose. */
        org.joml.Matrix3x2fc flaechenmatrix = kontext.batcher.getContext().pose();
        org.joml.Vector2f ecke = new org.joml.Vector2f();

        flaechenmatrix.transformPosition(x1, y1, ecke);

        int px1 = Math.round(ecke.x * s);
        int py1 = Math.round(ecke.y * s);

        flaechenmatrix.transformPosition(x2, y2, ecke);

        int px2 = Math.round(ecke.x * s);
        int py2 = Math.round(ecke.y * s);

        ScreenRectangle flaeche = new ScreenRectangle(px1, py1, px2 - px1, py2 - py1);

        /* ⛔ DEN SCHNITT DES AUFRUFERS MITNEHMEN (2026-08-05, im A/B gegen 1.21.1 gefunden).
         *
         * `batcher.clip(...)` wirkt nur auf SOFORT gezeichnete Zuege. Hier wird eingereiht und
         * spaeter gezeichnet -- da ist der Schnitt weg. `UIReplayList#renderElementPart` malt
         * eine 40x40-Vorschau und klemmt sie mit clip(formX, y, 40, 20) auf halbe Hoehe: in
         * 1.21.1 sitzt das Bild kompakt am Zeilenrand, im Port lief es ueberlang aus der Zeile.
         *
         * Der Schnitt ist in BILDPUNKTEN gefuehrt wie die Kachel, also direkt schneidbar. */
        ScreenRectangle schnitt = kontext.batcher.aktuellerSchnitt();
        ScreenRectangle sichtbar = flaeche;

        if (schnitt != null)
        {
            /* ⛔ DER SCHNITT KOMMT IN GUI-EINHEITEN, DIE KACHEL IST IN BILDPUNKTEN.
             *
             * Gemessen am laufenden Spiel: schnitt=[x=85, y=170, 30x20] bei einer Kachel um
             * x=170. Der erste Anlauf hat beide roh geschnitten -- Ergebnis leer, und damit
             * verschwanden ALLE Listen-Miniaturen. Dieselbe Falle, vor der `einreihen` oben
             * schon warnt; ich bin trotzdem hineingelaufen.
             *
             * ⛔ UND ER MUSS IN DENSELBEN RAUM WIE DIE FLAECHE (Messfuehler 2026-08-11):
             * waehrend der Aufblend-Animation kam raw=208,156 px=623,356..626,359 bei
             * schnitt=[208,156 20x20] -- Flaeche pose-transformiert, Schnitt nicht, die
             * Ueberschneidung LEER, alle Kacheln der Animation verworfen. Der Spiegel
             * fuehrt den Schnitt GLOBAL (clip zieht den Scroll schon ab), die Pose enthaelt
             * Scroll UND Zoom. Also: den Scroll-Anteil aus den eigenen Koordinaten
             * zurueckrechnen (raw - global) und den Schnitt durch dieselbe Pose schicken. */
            float dx = x1 - kontext.globalX(x1);
            float dy = y1 - kontext.globalY(y1);

            /* Der Spiegel liegt im Bildraum (SchnittSpiegel), / rest holt ihn nach GLOBAL. */
            flaechenmatrix.transformPosition(schnitt.left() / rest + dx, schnitt.top() / rest + dy, ecke);

            int sl = Math.round(ecke.x * s);
            int so = Math.round(ecke.y * s);

            flaechenmatrix.transformPosition(schnitt.right() / rest + dx, schnitt.bottom() / rest + dy, ecke);

            int sx = Math.max(flaeche.left(), sl);
            int sy = Math.max(flaeche.top(), so);
            int sr = Math.min(flaeche.right(), Math.round(ecke.x * s));
            int sb = Math.min(flaeche.bottom(), Math.round(ecke.y * s));

            /* Leerer Schnitt heisst "nichts sichtbar" -- dann gar nicht erst einreihen, sonst
             * zeichnet die Kachel voll und der Schnitt hat wieder nichts bewirkt. */
            if (sr <= sx || sb <= sy)
            {
                return;
            }

            sichtbar = new ScreenRectangle(sx, sy, sr - sx, sb - sy);
        }

        WARTEND.add(new MiniaturZustand(zeichner, kontext, px1, py1, px2, py2, sichtbar, flaeche));
    }

    /**
     * Gibt alles Wartende als einen bildschirmgrossen Zug ab und leert die Schlange.
     *
     * @param kontext der Oberflaechen-Kontext, ueber dessen Batcher eingereiht wird
     * @param breite  Bildschirmbreite in GUI-Einheiten
     * @param hoehe   Bildschirmhoehe in GUI-Einheiten
     */
    public static void abgeben(UIContext kontext, int breite, int hoehe)
    {
        if (WARTEND.isEmpty())
        {
            return;
        }

        try
        {
            /* Bild-im-Bild liegt in Minecrafts GUI-Raum, die Aufrufer geben BBS-Einheiten. */
            float rest = mchorse.bbs_mod.ui.framework.UISkala.rest();

            breite = (int) Math.ceil(breite * rest);
            hoehe = (int) Math.ceil(hoehe * rest);

            ScreenRectangle voll = new ScreenRectangle(0, 0, breite, hoehe);

            /* ⛔ EIGENE EBENE, SONST HAENGT DIE SICHTBARKEIT AN DER FENSTERGROESSE
             * (2026-08-05, im Frame gemessen).
             *
             * 26.2 sortiert GUI-Elemente nicht nach Aufrufreihenfolge, sondern ueber ihre
             * BOUNDS (`GuiRenderState#findAppropriateNode`): schliesst das ZULETZT eingefuegte
             * Element die neuen Bounds ein, geht der Zustand eine Ebene hoch -- sonst SINKT er,
             * bis er etwas schneidet, und wird darueber gesetzt. Was zuletzt eingefuegt wurde,
             * haengt am Layout, und das haengt an der Fenstergroesse.
             *
             * Zuege, die in DASSELBE Ziel schreiben, gleiche Buehne (Morph-Tab):
             *     854x480    Einkopieren nach 126 Zuegen,  0 danach  -> sichtbar
             *    1280x720    Einkopieren nach  12 Zuegen, 79 danach  -> uebermalt
             * Sichtbare Modellpunkte: 1.21.1 bei 1280x720 = 2706, der Port = 0.
             *
             * `nextStratum()` ist Vanillas eigener Weg fuer "ab hier ueber allem".
             *
             * ⚠️ NICHT ueber eine neue Methode auf Batcher2D: die Klasse ist gewoben, und eine
             * ZUSAETZLICHE Methode laesst sich dort nicht einwechseln ("attempted to add a
             * method") -- der Fix waere nur mit Boot pruefbar. Der Accessor ist derselbe, den
             * Batcher2D benutzt. */
            ((mchorse.bbs_mod.mixin.client.GuiGraphicsExtractorAccessor) (Object) kontext.batcher.getContext())
                .bbs$getGuiRenderState().nextStratum();

            kontext.batcher.bildImBild(new MiniaturSammelZustand(
                new ArrayList<>(WARTEND), 0, 0, breite, hoehe, voll, voll));
        }
        finally
        {
            WARTEND.clear();
        }
    }
}
