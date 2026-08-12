package mchorse.bbs_mod.ui.framework.elements.utils.schrift;

import java.util.ArrayList;
import java.util.List;

/**
 * Minecrafts Paragraph-Stilcodes fuer den Vektor-Text: Farbcodes schalten die
 * Zeichenfarbe, {@code §r} setzt auf die Basisfarbe zurueck, die uebrigen Codes
 * (fett/kursiv/…) werden v1 ueberlesen — der Text erscheint normal statt gar nicht.
 */
public final class VektorStil
{
    private static final int[] FARBEN = {
        0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
        0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};

    /** Kein Farbwechsel (Format-Code wie §l) — aktuelle Farbe behalten. */
    public static final int BEHALTEN = Integer.MIN_VALUE;

    private VektorStil()
    {}

    /** Neue Farbe fuer den Code hinter {@code §}, Alpha kommt von der Basis. */
    public static int farbe(char code, int basis, int aktuell)
    {
        int i = "0123456789abcdef".indexOf(Character.toLowerCase(code));

        if (i >= 0)
        {
            return (basis & 0xFF000000) | FARBEN[i];
        }

        return code == 'r' || code == 'R' ? basis : BEHALTEN;
    }

    /** MC-Schattenfarbe: RGB geviertelt, Alpha bleibt. */
    public static int dunkeln(int color)
    {
        return (color & 0xFF000000) | ((color >> 2) & 0x3F3F3F);
    }

    /**
     * Zeilenumbruch mit den Vektor-Breiten ({@code messer} liefert die Breite eines
     * Stuecks): gierig an Wortgrenzen, Stilcodes werden auf die Folgezeile getragen.
     */
    public static List<String> umbruch(String text, int breite, java.util.function.ToIntFunction<String> messer)
    {
        List<String> zeilen = new ArrayList<>();

        for (String absatz : text.split("\n", -1))
        {
            String stil = "";
            StringBuilder zeile = new StringBuilder();

            for (String wort : absatz.split(" ", -1))
            {
                String probe = zeile.isEmpty() ? stil + wort : zeile + " " + wort;

                if (!zeile.isEmpty() && messer.applyAsInt(probe) > breite)
                {
                    zeilen.add(zeile.toString());
                    stil = stilVon(zeile.toString(), stil);
                    zeile = new StringBuilder(stil + wort);
                }
                else
                {
                    zeile = new StringBuilder(probe);
                }

                /* Ein einzelnes Ueberlaenge-Wort hart brechen, sonst schoebe es die Zeile raus. */
                while (messer.applyAsInt(zeile.toString()) > breite && zeile.length() > stil.length() + 1)
                {
                    int schnitt = zeile.length() - 1;

                    while (schnitt > stil.length() + 1 && messer.applyAsInt(zeile.substring(0, schnitt)) > breite)
                    {
                        schnitt--;
                    }

                    zeilen.add(zeile.substring(0, schnitt));
                    stil = stilVon(zeile.substring(0, schnitt), stil);
                    zeile = new StringBuilder(stil + zeile.substring(schnitt));
                }
            }

            zeilen.add(zeile.toString());
        }

        return zeilen;
    }

    /** Der zuletzt wirksame Farbcode einer Zeile — Vorspann fuer die naechste. */
    private static String stilVon(String zeile, String bisher)
    {
        String stil = bisher;

        for (int i = 0; i < zeile.length() - 1; i++)
        {
            if (zeile.charAt(i) == '§')
            {
                char code = Character.toLowerCase(zeile.charAt(i + 1));

                if (code == 'r')
                {
                    stil = "";
                }
                else if ("0123456789abcdef".indexOf(code) >= 0)
                {
                    stil = "§" + code;
                }
            }
        }

        return stil;
    }
}
