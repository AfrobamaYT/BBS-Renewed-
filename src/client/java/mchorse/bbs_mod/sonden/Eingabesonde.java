package mchorse.bbs_mod.sonden;

/**
 * Schleust Zeiger- und Modifikator-Werte durch, damit eine Messroute sie setzen kann.
 *
 * <p>Jede Methode bekommt den ECHTEN Wert und gibt einen zurueck. Die Standardfassung gibt
 * ihn unveraendert weiter — im oeffentlichen Bau ist das der einzige Zustand, den es gibt,
 * und der JIT setzt den Aufruf weg.</p>
 *
 * <p>⛔ ES GIBT JE WERT GENAU EINEN TRICHTER, und der ist bewusst so gewaehlt, dass alle
 * Ereignisse hindurchlaufen: der Zeiger ueber {@code UIContext.setMouse} und
 * {@code UIScreen.render}, die Modifikatoren ueber {@code Window}. Zwei Quellen fuer
 * dieselbe Zahl waeren nicht mehr nachvollziehbar — genau daran scheiterte die
 * VirtuelleMaus, bevor sie 2026-08-04 an setMouse kam: der gesetzte Zeiger wirkte nur auf
 * den einen Wert, den die Route selbst schrieb, und war im naechsten Bild wieder weg.</p>
 */
public interface Eingabesonde
{
    /** Standardfassung ohne Companion: jeder Wert geht unveraendert durch. */
    Eingabesonde NICHTS = new Eingabesonde() {};

    default int mausX(int echt)
    {
        return echt;
    }

    default int mausY(int echt)
    {
        return echt;
    }

    default boolean strg(boolean echt)
    {
        return echt;
    }

    default boolean umschalt(boolean echt)
    {
        return echt;
    }

    default boolean alt(boolean echt)
    {
        return echt;
    }

    /** Nur fuer den Messbericht — steht hinter {@code Messpunkt.istAn()}. */
    default String zustand()
    {
        return "aus";
    }
}
