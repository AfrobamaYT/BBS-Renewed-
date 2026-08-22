package mchorse.bbs_mod.ui.dashboard.panels;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * Leerzustand des Auswahl-Bildschirms.
 *
 * <p>Vier der acht Hauptseiten begruessen den Nutzer mit einer leeren Liste — Particles,
 * Model Editor, Model Blocks, Audio. Das Screen-Inventar nennt das den groessten
 * Einzelgewinn des Umbaus: „Wer BBS zum ersten Mal oeffnet, sieht auf der Haelfte der
 * Seiten nicht, was er tun soll." Eine leere Flaeche sagt nichts; dieser Hinweis zeigt
 * auf das Plus, das direkt darueber schon sichtbar ist.</p>
 *
 * <p>Eigene Klasse, weil {@link UISelectionScreen} die 800-Zeilen-Grenze der Werkbank
 * erreicht hat und nur noch schrumpfen darf.</p>
 */
public class UIAuswahlLeer
{
    /** --txt-dim, --txt-off aus dem Design-System; das Symbol traegt nur einen Hauch. */
    private static final int SYMBOL = Colors.setA(0xFF9C9C9C, 0.22F);
    private static final int TITEL  = 0xFF9C9C9C;
    private static final int HINWEIS = 0xFF616161;

    public static void zeichnen(UIContext context, Area flaeche, boolean leer, Icon symbol)
    {
        zeichnen(context, flaeche, leer, symbol, "Nothing here yet", "Use + above to create one");
    }

    public static void zeichnen(UIContext context, Area flaeche, boolean leer, Icon symbol,
        String titel, String hinweis)
    {
        if (!leer || flaeche.h < 44)
        {
            return;
        }

        int mx = flaeche.mx();
        int my = flaeche.my();
        int zeile = context.batcher.getFont().getHeight();

        if (symbol != null)
        {
            context.batcher.icon(symbol, SYMBOL, mx, my - zeile - 12, 0.5F, 0.5F);
        }

        context.batcher.text(titel, mx - context.batcher.getFont().getWidth(titel) / 2, my - 2, TITEL);
        context.batcher.text(hinweis, mx - context.batcher.getFont().getWidth(hinweis) / 2, my + zeile + 3, HINWEIS);
    }
}
