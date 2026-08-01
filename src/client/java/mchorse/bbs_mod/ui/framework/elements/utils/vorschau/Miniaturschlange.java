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
        ScreenRectangle flaeche = new ScreenRectangle(x1, y1, x2 - x1, y2 - y1);

        WARTEND.add(new MiniaturZustand(zeichner, kontext, x1, y1, x2, y2, flaeche, flaeche));
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
