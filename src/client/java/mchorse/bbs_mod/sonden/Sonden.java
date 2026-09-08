package mchorse.bbs_mod.sonden;

/**
 * Die Anschlussdose fuer die Companion-Mod {@code bbsdev}.
 *
 * <h2>Wofuer</h2>
 *
 * <p>Bis zum 2026-09-09 entstand die oeffentliche Jar, indem <b>391 Aufrufe in 27
 * Kern-Dateien von Hand auskommentiert</b> wurden ({@code //[release]}). Dieser Schnitt hat
 * schon einmal die komplette Nano-Oberflaeche in JEDER oeffentlichen Jar stillgelegt — es
 * fehlte keine Klasse, es fehlte eine ZEILE, und gefunden wurde das erst im Bildvergleich
 * zwischen dev und public.</p>
 *
 * <p>Hier steht stattdessen ein Feld je Sonde, das im oeffentlichen Bau seine leere
 * Standardfassung behaelt. Der Kern ruft die Sonde <b>immer</b>; ob jemand zuhoert,
 * entscheidet allein, ob die Companion installiert ist. Damit gibt es keine Zeile mehr, die
 * beim Ausliefern verschwinden muss — und keine, die dabei versehentlich bleibt.</p>
 *
 * <h2>Kosten</h2>
 *
 * <p>Ein statischer Feldzugriff plus ein Aufruf mit leerem Rumpf. Solange nur die
 * {@code NICHTS}-Fassung geladen ist, ist die Aufrufstelle monomorph und der JIT setzt sie
 * weg. Das ist der Grund fuer Interface-plus-Standardfassung statt der namensbasierten
 * {@link com.afrobama.bbstools.client.blender.MixinBruecke}: die kostet einen Map-Zugriff
 * je Aufruf und traegt sich nur in Mixin-Handlern, die selten feuern.</p>
 *
 * <p>⛔ Die Felder sind {@code volatile} und werden EINMAL beim Start der Companion gesetzt,
 * nie im laufenden Betrieb umgeschaltet. Wer hier zur Laufzeit tauscht, nimmt dem JIT genau
 * die Annahme weg, die den Aufruf kostenlos macht.</p>
 */
public final class Sonden
{
    /** Wer hat den Mausklick angenommen. Ohne Companion: niemand hoert zu. */
    public static volatile Klicksonde klick = Klicksonde.NICHTS;

    /** Zeiger und Modifikatoren. Ohne Companion: echte Werte, unveraendert. */
    public static volatile Eingabesonde eingabe = Eingabesonde.NICHTS;

    private Sonden()
    {}
}
