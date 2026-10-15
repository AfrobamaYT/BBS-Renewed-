package com.afrobama.bbstools.sonden;

/**
 * Optionale Meldungen an die Messschicht der Companion-Mod.
 *
 * <p>Alle vier Meldungen haben denselben Charakter: der oeffentliche Code hat etwas
 * beobachtet, das fuer eine Messung wertvoll ist, funktioniert aber vollstaendig ohne dass
 * jemand zuhoert. Ohne Companion ist jede ein no-op.</p>
 *
 * <p>⛔ EINE MELDUNG DARF NIE ETWAS ENTSCHEIDEN. Wer hier einen Rueckgabewert braucht, um
 * weiterzurechnen, baut eine Abhaengigkeit vom Dev-Modul in den oeffentlichen Ablauf — dann
 * gehoert die Sache an einen eigenen Anschluss mit einer ehrlichen Standardantwort, so wie
 * {@link ArmeeFilter}.</p>
 */
public interface Meldesonde
{
    /** Standardfassung ohne Companion: nimmt alles an, tut nichts. */
    Meldesonde NICHTS = new Meldesonde() {};

    /** Wird EINMAL beim Start der Companion gesetzt. */
    Halter HALTER = new Halter();

    /**
     * Ein Fehler, den der Aufrufer bewusst schluckt.
     *
     * <p>Ohne diese Meldung verschwindet er spurlos — und ein still verschluckter Fehler in
     * einem Parallelpfad ist der teuerste Bug, weil das Ergebnis plausibel falsch bleibt.</p>
     */
    default void verschluckt(String stelle, Throwable t)
    {}

    /** Eine Nutzeraktion fuer die Sitzungsaufzeichnung (Editor-Kamera, Befehle, Marken). */
    default void aktion(String art, String text)
    {}

    /** Eine Schattierungsquelle zur Durchsicht. */
    default void schattierung(int shader, String quelle)
    {}

    /** Ein Durchlauf des Schnellpfads, fuer die Zeitachse der Aufzeichnung. */
    default void schnellpfad(int einreichungen, int modelle, long index)
    {}

    /** Traegt die aktive Fassung. Eigene Klasse, weil ein Interface kein Feld haben kann. */
    final class Halter
    {
        public volatile Meldesonde aktiv = NICHTS;

        private Halter()
        {}
    }
}
