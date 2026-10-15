package mchorse.bbs_mod.sonden;

/**
 * Setzt Marken in den GL-Strom, damit ein Frame-Debugger (RenderDoc, apitrace, Nsight)
 * zeigen kann, welcher Zug wozu gehoert.
 *
 * <p>Wofuer sie da sind, steht an den Aufrufstellen: ohne Gruppen liegt jede Vorschau als
 * namenloser Zeichenaufruf in einer flachen Liste — beim Umriss-Versatz waren das 1776
 * Stueck, und den richtigen zu finden kostete zwei Tage.</p>
 *
 * <h2>Zwei Regeln, die beim Umbau bezahlt sind</h2>
 *
 * <p>⛔ <b>Paarig.</b> Ein {@code gruppeAuf} ohne {@code gruppeZu} verschachtelt den Event
 * Browser falsch — das ist schlimmer als gar keine Marke. Darum feuern beide IMMER, auch
 * ohne Companion; die Standardfassung tut nur nichts.</p>
 *
 * <p>⛔ <b>{@link #marken()} fragen, bevor eine Marke ZUSAMMENGEBAUT wird.</b> Wo die
 * Beschriftung aus Teilen entsteht, kostet sie ohne diese Abfrage im oeffentlichen Bau eine
 * Allokation je Kachel — Kosten, die es dort heute nicht gibt, weil die Zeile bisher
 * auskommentiert war. Bei konstanten Beschriftungen ist die Abfrage unnoetig.</p>
 */
public interface Gpusonde
{
    /** Standardfassung ohne Companion: keine Marken, keine Kosten. */
    Gpusonde NICHTS = new Gpusonde() {};

    /**
     * Sieht hier jemand zu? Nur dann lohnt es, eine Beschriftung zusammenzubauen.
     *
     * <p>Nicht als Schalter VOR dem Aufruf benutzen — siehe die Paarigkeitsregel im
     * Klassen-Javadoc.</p>
     */
    default boolean marken()
    {
        return false;
    }

    /** Beschriftete Gruppe im GL-Strom auf. {@code null} heisst: keine Marke. */
    default void gruppeAuf(String marke)
    {}

    /** Gegenstueck zu {@link #gruppeAuf}. */
    default void gruppeZu()
    {}

    /** Benannter Bereich fuer den Profiler (NVTX). */
    default void bereichAuf(String marke)
    {}

    /** Gegenstueck zu {@link #bereichAuf}. */
    default void bereichZu()
    {}

    /** Zustand vor einem Zeichenaufruf festhalten — fuer die Aufnahme einzelner Zuege. */
    default void vorZeichnen(String stelle, int programm, int vao, int modus, int anzahl, int instanzen)
    {}
}
