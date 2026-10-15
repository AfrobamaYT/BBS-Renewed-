package mchorse.bbs_mod.sonden;

/**
 * Haelt die Mess-Ueberlagerung aus dem Kamerabild heraus.
 *
 * <p>Die Ueberlagerung haengt am {@code HudRenderCallback} und wuerde den Viewport
 * zupflastern — sie gehoert auf den Bildschirm, nicht ins Bild, das exportiert wird.
 * Darum wird sie um den HUD-Zug herum kurz abgeschaltet.</p>
 *
 * <p>⛔ ALS PAAR, NICHT ALS FELD. Ein durchgereichtes {@code ENABLED}-Feld haette den Kern
 * gezwungen, den Zustand selbst zu merken und zurueckzuschreiben — dann steht das Merken
 * im Kern und das Schalten in der Companion, und ein Weg aus dem Zug heraus, der das
 * Zuruecksetzen ueberspringt, laesst die Ueberlagerung dauerhaft aus. Hier liegt beides
 * beieinander, und ohne Companion ist beides ein no-op.</p>
 */
public interface Ueberlagerungssonde
{
    /** Standardfassung ohne Companion: es gibt keine Ueberlagerung, also nichts zu tun. */
    Ueberlagerungssonde NICHTS = new Ueberlagerungssonde() {};

    /**
     * Schaltet die Ueberlagerung ab und liefert den Zustand, der vorher galt.
     *
     * <p>Der Rueckgabewert gehoert unveraendert in {@link #wiederherstellen(boolean)} — in
     * ein {@code finally}, damit auch ein Abbruch mitten im Zug ihn zurueckgibt.</p>
     */
    default boolean ausschalten()
    {
        return false;
    }

    /** Gegenstueck zu {@link #ausschalten()}. */
    default void wiederherstellen(boolean vorher)
    {}
}
