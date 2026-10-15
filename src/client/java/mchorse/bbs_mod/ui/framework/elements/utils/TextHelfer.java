package mchorse.bbs_mod.ui.framework.elements.utils;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.List;

/**
 * Aufgesetzte Text-Bausteine: Blocksatz ({@code wallText}) und Text auf Karte
 * ({@code textCard}). Beide zeichnen ausschliesslich ueber die oeffentlichen Wege von
 * {@link Batcher2D} — hier liegt kein eigener Zeichenweg, nur die Anordnung.
 *
 * <p>Herausgeloest aus {@link Batcher2D} (974 Zeilen, gelisteter Monolith), als dort der
 * Drehwinkel fuer Text dazukam. Die Aufrufer merken nichts: {@code Batcher2D} behaelt
 * seine Methoden und reicht hierher durch.
 */
public final class TextHelfer
{
    private TextHelfer() {}

    /**
     * Text im Blocksatz auf {@code width} Breite. Gibt die belegte Hoehe zurueck.
     *
     * @param ax waagerechte Ausrichtung je Zeile (0 links, 0.5 mittig, 1 rechts)
     * @param ay senkrechte Ausrichtung des ganzen Blocks um {@code y}
     */
    public static int wallText(Batcher2D b, String text, int x, int y, int color, int width,
        int lineHeight, float ax, float ay, boolean shadow)
    {
        FontRenderer font = b.getFont();
        List<String> list = font.wrap(text, width);
        int h = (lineHeight * (list.size() - 1)) + font.getHeight();

        y -= h * ay;

        for (String string : list)
        {
            b.text(string, (int) (x + (width - font.getWidth(string)) * ax), y, color, shadow);

            y += lineHeight;
        }

        return h;
    }

    /** Text mit Hintergrund-Karte. {@code offset} ist der Rand zwischen Schrift und Kante. */
    public static void textCard(Batcher2D b, String text, float x, float y, int color,
        int background, float offset, boolean shadow)
    {
        int a = background >> 24 & 0xff;

        if (a != 0)
        {
            if (BBSSettings.isLightTheme() && (background & 0xFFFFFF) == 0)
            {
                background = (background & 0xFF000000) | 0xFFFFFF;
            }

            FontRenderer font = b.getFont();
            float w = font.getWidth(text) + offset * 2 - 1;
            float h = font.getHeight() + offset * 2;

            /* P2: Karten sind der erste Rundeck-Abnehmer. */
            if (BBSSettings.vectorUi != null && BBSSettings.vectorUi.get())
            {
                b.rundeck(x - offset, y - offset, w, h, offset + 1F, background);
            }
            else
            {
                b.box(x - offset, y - offset, x - offset + w, y - offset + h, background);
            }
        }

        b.text(text, x, y, color, shadow);
    }

    /** Vorgabefarben fuer die Karte: weisse Schrift auf halbdurchsichtigem Schwarz. */
    public static void textCard(Batcher2D b, String text, float x, float y)
    {
        textCard(b, text, x, y, Colors.WHITE, Colors.A50, 3, true);
    }
}
