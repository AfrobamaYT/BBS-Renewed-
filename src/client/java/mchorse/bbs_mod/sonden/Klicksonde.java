package mchorse.bbs_mod.sonden;

/**
 * Meldet, welches UI-Element einen physischen Mausklick angenommen hat.
 *
 * <p>Beantwortet bei "der Knopf tut nichts" die einzige Frage, die die Suche teilt: lief der
 * Handler (dann liegt der Fehler in ihm) oder erreichte der Klick keinen (dann liegt er
 * davor — Trefferflaeche, Ueberdeckung, {@code isEnabled}, verschluckte Maustaste).</p>
 *
 * <p>Die tragende Fassung liegt in der Companion-Mod. Hier steht nur der Anschluss und die
 * leere Standardfassung; siehe {@link Sonden}.</p>
 */
public interface Klicksonde
{
    /** Standardfassung ohne Companion: hoert zu und tut nichts. */
    Klicksonde NICHTS = new Klicksonde() {};

    /**
     * Ein Element hat den Klick ANGENOMMEN.
     *
     * <p>Kommt aus {@code UIBaseMenu.mouseClicked}, und zwar nur aus dem Zweig, in dem der
     * Verteiler ein Element zurueckgibt.</p>
     */
    default void angenommenVon(Object element)
    {}

    /**
     * Der physische Klick ist durch — auch wenn ihn niemand angenommen hat.
     *
     * <p>Gerade der Fall ohne Abnehmer ist der Befund, darum feuert das immer.</p>
     */
    default void abgeschlossen(int x, int y, int knopf)
    {}
}
