package mchorse.bbs_mod.ui.framework.elements.utils;

/**
 * Messfuehler fuer einen Absturz, der die JVM mitnimmt, statt eine Ausnahme zu werfen.
 *
 * <h2>Wofuer das gebaut wurde</h2>
 *
 * <p>Ein nativer Absturz im Zeichenweg (Zugriffsverletzung, 0xC0000005) hinterlaesst weder
 * Stapelspur noch Ausnahme noch hs_err-Bericht. Alles, was bleibt, ist die letzte Zeile im
 * Log -- und die stammt dann von irgendeinem Nachbarn, nicht vom Schuldigen.
 *
 * <p>Der einzige Weg, so einen Absturz einzugrenzen, ist eine Spur, die VOR jedem Element
 * steht. Die letzte Zeile benennt dann genau das Element, das die JVM mitgenommen hat.
 *
 * <h2>Warum System.err und nicht der Logger</h2>
 *
 * <p>{@code System.err} ist bei der JVM ungepuffert. Ein {@code LOGGER.info} kann im Puffer
 * stehenbleiben, wenn der Prozess im naechsten Aufruf stirbt -- und dann zeigt die letzte
 * Logzeile auf eine Stelle, die schon laengst durch war. Genau diese Falle soll der Fuehler
 * nicht selbst aufstellen.
 *
 * <h2>Benutzung</h2>
 *
 * <p>Standardmaessig AUS, kostet dann einen Feldzugriff pro Element. Anschalten mit
 * {@code -Dbbs.zeichenfuehler=true}, in {@code build.gradle} verdrahtet als:
 *
 * <pre>./gradlew runQuick -Pfuehler</pre>
 */
public class Zeichenfuehler
{
    /**
     * Die Obergrenze verhindert, dass ein Fehlstart das Log ueber Stunden vollschreibt.
     * Der gesuchte Absturz liegt im ERSTEN Bild, also reichen ein paar tausend Zeilen.
     */
    private static final int GRENZE = 20000;

    private static final boolean AN = Boolean.getBoolean("bbs.zeichenfuehler");

    private static int zaehler;

    public static void vor(Object element)
    {
        if (!AN || zaehler >= GRENZE)
        {
            return;
        }

        zaehler += 1;

        System.err.println("[zeichenfuehler " + zaehler + "] " + element.getClass().getName());
    }
}
