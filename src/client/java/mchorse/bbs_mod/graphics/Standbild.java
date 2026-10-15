package mchorse.bbs_mod.graphics;

/**
 * Haelt die Vorschau-animationen an, damit zwei Aufnahmen desselben Zustands GLEICH sind.
 *
 * <h2>Wofuer</h2>
 *
 * <p>Ein A/B-vergleich ueber Bildpunkte ist nur so scharf wie seine Nullinie: nimmt man
 * zweimal dasselbe auf und es unterscheidet sich, ist jeder kleinere Unterschied zwischen
 * A und B wertlos. Am 2026-08-01 lag diese Nullinie bei 669 Punkten -- und zwar MITTEN im
 * Messfeld, weil das Vorschaumodell seine Leerlauf-animation weiterlaeuft.
 *
 * <p>{@code /tick freeze} erreicht sie NICHT. Die Oberflaeche zaehlt ihren eigenen Tick
 * ({@code UIContext.getTick()}), unabhaengig von der Welt. Genau deshalb gibt es diesen
 * Schalter.
 *
 * <h2>Zwei Dinge muessen still stehen, nicht eins</h2>
 *
 * <ol>
 * <li>der <b>Tick</b> -- sonst laeuft die Animation weiter;</li>
 * <li>der <b>Uebergang</b> (Teilbild zwischen zwei Ticks) -- sonst interpoliert der
 *     Zeichner zwischen zwei Posen weiter, obwohl der Tick steht. Wer nur das erste
 *     abschaltet, wundert sich ueber ein Restzittern.</li>
 * </ol>
 *
 * <h2>⛔ Kein Feature, ein Messwerkzeug</h2>
 *
 * <p>Standardmaessig AUS. Der Schalter liegt in einer System-Property und nicht in einem
 * statischen Feld -- aus demselben Grund wie bei {@link Messpunkt}: ein frisch
 * hinzugefuegtes statisches Feld ist nach einem Hotswap null.
 *
 * <pre>
 * System.setProperty("bbs.standbild", "1")   an
 * System.clearProperty("bbs.standbild")      aus
 * </pre>
 */
public final class Standbild
{
    private static final String SCHALTER = "bbs.standbild";

    public static boolean an()
    {
        return System.getProperty(SCHALTER) != null;
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

    /** Der Uebergang, oder 0 im Standbild -- damit zwischen zwei Posen nicht interpoliert wird. */
    public static float uebergang(float echter)
    {
        return an() ? 0F : echter;
    }

    private Standbild()
    {}
}
