package mchorse.bbs_mod.graphics;

import com.mojang.blaze3d.vertex.PoseStack;

import java.lang.reflect.Field;

/**
 * ⚗️ Prueft, ob ein Abschnitt den Matrizen-Stapel so hinterlaesst, wie er ihn vorfand.
 *
 * <h2>Wofuer</h2>
 *
 * <p>Vanilla bricht mit {@code IllegalStateException: Pose stack not empty} ab, wenn beim
 * Weltdurchgang noch etwas auf dem Stapel liegt. Der Abbruch kommt aber ERST BEIM NAECHSTEN
 * Weltdurchgang, mit keiner einzigen BBS-Zeile im Aufrufstapel — man sieht, DASS jemand
 * liegengelassen hat, nie WER.</p>
 *
 * <p>Am 2026-08-01 hat das drei statische Pruefungen ins Leere laufen lassen: push/pop sind
 * in allen Form-Zeichnern zaehlbar ausgeglichen, zwischen beiden steht nirgends ein frueher
 * {@code return}, und eine Ausnahme-Klammer aendert nichts. Der Ausloeser steht eben nicht
 * dort, wo es knallt.</p>
 *
 * <p>Diese Wache dreht das um: sie misst die Tiefe VOR und NACH einem Abschnitt und meldet
 * die Differenz mitsamt dem Namen des Abschnitts. Wer liegenlaesst, steht damit im
 * Messbericht, nicht im Ratespiel.</p>
 *
 * <h2>Bedienung</h2>
 *
 * <pre>
 * Stapelwache.pruefen("miniatur:" + name, stapel, () -&gt; ...);
 * </pre>
 *
 * <p>Ohne eingeschalteten {@link Messpunkt} laeuft nur die Tiefenabfrage (zwei
 * Feldzugriffe) — das ist billig genug fuer einen Zeichenpfad. Eine Abweichung wird
 * IMMER gemeldet, auch bei ausgeschalteter Messung: ein liegengelassener Stapel ist kein
 * Messwert, sondern ein Defekt.</p>
 */
public final class Stapelwache
{
    private static Field tiefenFeld;
    private static boolean feldKaputt;

    private Stapelwache()
    {}

    /** Die aktuelle Tiefe, oder -1 wenn sie sich nicht lesen laesst. */
    public static int tiefe(PoseStack stapel)
    {
        if (stapel == null || feldKaputt)
        {
            return -1;
        }

        try
        {
            if (tiefenFeld == null)
            {
                /* 26.2: PoseStack fuehrt `List poses` und `int lastIndex`. lastIndex ist die
                 * Tiefe; poses waechst nicht zurueck, weil der Stapel seine Eintraege
                 * wiederverwendet -- wer size() liest, misst den Hoechststand, nicht den
                 * Stand. Nachgesehen an der laufenden Instanz, nicht geraten. */
                Field f = mchorse.bbs_mod.utils.LaufzeitFeld.holen(PoseStack.class, "lastIndex", "field_55850");

                f.setAccessible(true);
                tiefenFeld = f;
            }

            return tiefenFeld.getInt(stapel);
        }
        catch (Throwable t)
        {
            /* Einmal laut, dann nie wieder -- eine Wache, die jedes Bild scheitert, ist
             * selbst der Schaden. */
            feldKaputt = true;

            Messpunkt.stoerung("stapelwache.feld", String.valueOf(t));

            return -1;
        }
    }

    /**
     * Fuehrt {@code arbeit} aus und meldet, wenn der Stapel danach tiefer oder flacher ist
     * als davor.
     *
     * <p>Die Arbeit wird NICHT abgefangen: eine Ausnahme soll weiterfliegen, damit sie dort
     * behandelt wird, wo jemand weiss, was zu tun ist. Die Meldung geht trotzdem raus —
     * gerade der Ausnahmefall ist der interessante.</p>
     */
    public static void pruefen(String stelle, PoseStack stapel, Runnable arbeit)
    {
        int vorher = tiefe(stapel);

        try
        {
            arbeit.run();
        }
        finally
        {
            int nachher = tiefe(stapel);

            if (vorher >= 0 && nachher != vorher)
            {
                Messpunkt.stoerung("stapel.liegengelassen",
                    stelle + ": " + vorher + " -> " + nachher);
            }
        }
    }

    /* ---------------------------------------------------------------------
     * BROTKRUME — welcher BBS-Abschnitt lief zuletzt?
     *
     * ⛔ WOFUER: der Absturz "Pose stack not empty" kommt aus vanillas checkPoseStack, und
     * im Stack steht KEIN BBS-Rahmen. Vanilla merkt den Ungleichstand nur als letzter; wer
     * ihn hinterliess, ist daraus nicht ablesbar. Sechs Berichte am 2026-08-01 sahen alle
     * gleich aus und nannten keinen Verursacher.
     *
     * Diese Krume kostet ein Feld und eine Zuweisung je Abschnitt. Dafuer sagt der naechste
     * Bericht, WAS zuletzt lief — und das grenzt die Suche von "irgendwo im Frame" auf einen
     * Abschnitt ein.
     * ------------------------------------------------------------------ */

    private static volatile String letzterAbschnitt = "(keiner)";

    /** Einen BBS-Abschnitt betreten/verlassen vermerken. */
    public static void abschnitt(String name)
    {
        letzterAbschnitt = name;
    }

    /** Was zuletzt lief — fuer Absturzberichte und den Debugger. */
    public static String letzterAbschnitt()
    {
        return letzterAbschnitt;
    }

    /* ⛔ HIER STAND EIN BEGIN/END-PAAR (merken/vergleichen) MIT EINER static-final-Map.
     *    Es wurde am 2026-08-02 gebaut und im selben Zug wieder entfernt: es rief niemand,
     *    und sein `static final`-Feld machte die Klasse ohne Not hotswap-feindlich — der
     *    Tausch endete mit "die instanz ist jetzt scharf", weil ein neues statisches Feld
     *    nach dem Swap null ist und ein finales sich per Reflexion nicht setzen laesst.
     *
     *    Lehre: spekulativ gebaute API kostet nicht nur den Bau. Sie kann eine laufende
     *    Instanz gefaehrden, bevor sie den ersten Nutzen hatte. */
}
