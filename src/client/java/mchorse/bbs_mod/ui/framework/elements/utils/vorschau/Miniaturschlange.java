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
        float s = mchorse.bbs_mod.BBSModClient.getGUIScale();

        int px1 = Math.round(x1 * s);
        int py1 = Math.round(y1 * s);
        int px2 = Math.round(x2 * s);
        int py2 = Math.round(y2 * s);

        ScreenRectangle flaeche = new ScreenRectangle(px1, py1, px2 - px1, py2 - py1);

        WARTEND.add(new MiniaturZustand(zeichner, kontext, px1, py1, px2, py2, flaeche, flaeche));
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
            ScreenRectangle voll = new ScreenRectangle(0, 0, breite, hoehe);

            kontext.batcher.bildImBild(new MiniaturSammelZustand(
                new ArrayList<>(WARTEND), 0, 0, breite, hoehe, voll, voll));
        }
        finally
        {
            WARTEND.clear();
        }
    }
}
