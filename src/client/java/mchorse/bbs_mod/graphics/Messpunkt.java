package mchorse.bbs_mod.graphics;

/**
 * Ein Zaehler an einer Stelle im Zeichenweg, den man von aussen anschalten und auslesen kann.
 *
 * <h2>Wofuer</h2>
 *
 * <p>Der Zeichenweg der Oberflaeche ist eine Kette stiller Frueh-Ausstiege: kein Modell
 * gesetzt, kein Eckenpuffer, Eckenzahl 0, leerer Ausschnitt. Jeder davon steigt ohne Fehler
 * und ohne Logzeile aus. Von aussen sehen "kommt gar nicht an" und "kommt an und wird nicht
 * sichtbar" GENAU GLEICH aus -- beide Male ein schwarzes Feld. Diese Frage muss beantwortet
 * sein, BEVOR jemand einen Fix baut; sonst repariert man die Haelfte, die nie kaputt war.
 *
 * <h2>⛔ Warum System-Properties und kein statisches Feld</h2>
 *
 * <p>Ein frisch hinzugefuegtes statisches Feld ist nach einem Hotswap NULL: statische
 * Initialisierer laufen beim Tausch nicht neu. Ein Messfuehler, der genau dann kaputt ist,
 * wenn man ihn einbaut, ist schlimmer als keiner. Properties existieren schon und
 * ueberleben jeden Tausch.
 *
 * <h2>Bedienung ueber den EXEC-Kanal</h2>
 *
 * <pre>
 * mchorse.bbs_mod.graphics.Messpunkt.an(true)     einschalten (gibt "an" zurueck)
 * mchorse.bbs_mod.graphics.Messpunkt.bericht()    alle Zaehler als Text
 * mchorse.bbs_mod.graphics.Messpunkt.leeren()     Zaehler zuruecksetzen
 * </pre>
 *
 * <p>Standardmaessig AUS. Angeschaltet kostet es ein {@code getProperty} je Aufruf -- das
 * ist fuer eine Messung in Ordnung und fuer den Dauerbetrieb nicht gedacht.
 */
public final class Messpunkt
{
    private static final String SCHALTER = "bbs.mess";
    private static final String VORSATZ = "bbs.mess.";

    /** Einen Durchlauf an dieser Stelle zaehlen. Ohne Schalter passiert nichts. */
    public static void zaehle(String stelle)
    {
        zaehle(stelle, null);
    }

    /**
     * Wie oben, dazu eine Einzelheit, die den LETZTEN Durchlauf beschreibt.
     *
     * <p>Die Einzelheit wird ueberschrieben, nicht gesammelt: bei 60 Bildern je Sekunde
     * will niemand eine Liste, sondern den Zustand von eben.
     */
    public static void zaehle(String stelle, String einzelheit)
    {
        if (System.getProperty(SCHALTER) == null)
        {
            return;
        }

        String schluessel = VORSATZ + stelle;

        System.setProperty(schluessel,
            String.valueOf(Long.parseLong(System.getProperty(schluessel, "0")) + 1));

        if (einzelheit != null)
        {
            System.setProperty(VORSATZ + "~" + stelle, einzelheit);
        }
    }

    /**
     * Laeuft die Messung gerade?
     *
     * <p>⛔ VOR JEDER TEUREN EINZELHEIT ABFRAGEN. Java wertet Argumente aus, BEVOR die
     * gerufene Methode ihren Schalter prueft: {@code zaehle("x", rufer(3))} macht den
     * Stapelabzug also auch bei ausgeschalteter Messung. Genau so eingebaut und sofort
     * wieder korrigiert (2026-08-01) -- ein Messfuehler, der im Normalbetrieb Leistung
     * frisst, wird beim naechsten Profillauf zum vermeintlichen Produktfehler.
     */
    public static boolean istAn()
    {
        return System.getProperty(SCHALTER) != null;
    }

    /**
     * Die naechsten BBS-Aufrufer oberhalb dieser Stelle, als eine Zeile.
     *
     * <p>Fuer die Frage "WER macht das eigentlich", wenn Greppen nicht weiterhilft, weil
     * viele Stellen dasselbe tun koennten. Teuer (fuellt einen Stapelabzug) -- nur unter
     * einer Bedingung aufrufen, nie je Bild.
     */
    public static String rufer(int tiefe)
    {
        StringBuilder text = new StringBuilder();
        StackTraceElement[] stapel = Thread.currentThread().getStackTrace();
        int gefunden = 0;

        for (StackTraceElement e : stapel)
        {
            if (!e.getClassName().startsWith("mchorse.") && !e.getClassName().startsWith("com.afrobama."))
            {
                continue;
            }

            if (e.getClassName().endsWith("Messpunkt") || e.getClassName().endsWith("Batcher2D"))
            {
                continue;
            }

            if (gefunden > 0)
            {
                text.append(" <- ");
            }

            text.append(e.getClassName().substring(e.getClassName().lastIndexOf('.') + 1))
                .append('.').append(e.getMethodName()).append(':').append(e.getLineNumber());

            if (++ gefunden >= tiefe)
            {
                break;
            }
        }

        return text.toString();
    }

    /**
     * Eine Stoerung festhalten, die NICHT still bleiben darf -- unabhaengig vom Schalter.
     *
     * <p>Fuer den Fall, dass ein Weg wegbricht, den man von aussen nicht sieht: ein
     * Reflexionszugriff, der nach einem Spiel-Update ins Leere greift, ein Ziel, das fehlt.
     * Ein {@code catch}, das nur {@code null} zurueckgibt, verwandelt so etwas in ein
     * schwarzes Feld ohne Erklaerung -- und danach sucht man tagelang am falschen Ende.
     *
     * <p>Auslesen mit {@link #bericht()} wie alles andere; der Eintrag traegt das Praefix
     * {@code !} und faellt damit auf.
     */
    public static void stoerung(String stelle, String was)
    {
        System.setProperty(VORSATZ + "!" + stelle, was == null ? "?" : was);
    }

    public static String an(boolean ein)
    {
        if (ein)
        {
            System.setProperty(SCHALTER, "1");
        }
        else
        {
            System.clearProperty(SCHALTER);
        }

        return ein ? "an" : "aus";
    }

    /** Alle Zaehler zuruecksetzen, den Schalter aber lassen wie er ist. */
    public static String leeren()
    {
        for (String name : System.getProperties().stringPropertyNames())
        {
            if (name.startsWith(VORSATZ))
            {
                System.clearProperty(name);
            }
        }

        return "geleert";
    }

    public static String bericht()
    {
        StringBuilder text = new StringBuilder();

        System.getProperties().stringPropertyNames().stream()
            .filter((name) -> name.startsWith(VORSATZ))
            .sorted()
            .forEach((name) -> text.append(name.substring(VORSATZ.length()))
                .append(" = ").append(System.getProperty(name)).append('\n'));

        return text.length() == 0 ? "(kein zaehler gesetzt -- schalter an?)" : text.toString();
    }

    private Messpunkt()
    {}
}
