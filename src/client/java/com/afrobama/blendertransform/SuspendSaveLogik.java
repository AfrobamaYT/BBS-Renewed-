package com.afrobama.blendertransform;

import com.afrobama.bbstools.perf.BBSPerfConfig;

/**
 * Logik der Speicher-Pause während der Wiedergabe — im Kernel-Paket, damit sie hotswapbar ist.
 *
 * <p>Herausgelöst aus {@code UIDataDashboardPanelSuspendSaveMixin} (2026-07-29, Aufgabe #56).</p>
 *
 * <p><b>Wofür:</b> BBS' Dashboard schreibt in festen Abständen den Datenstand auf Platte.
 * Während ein Film läuft, ist das der falsche Moment — der Schreibvorgang fällt mitten in die
 * Wiedergabe und ruckelt. Läuft gerade ein Film, wird der periodische Speicherlauf also
 * ausgelassen und beim nächsten regulären Termin nachgeholt.</p>
 *
 * <p><b>Warum diese Stelle die Brücke verträgt:</b> {@code checkPeriodicSave} läuft nach
 * Zeitplan, nicht pro Frame und schon gar nicht pro Objekt. Der Brückenaufruf fällt hier
 * nicht ins Gewicht — und ob und wann ausgesetzt wird, ist genau die Art Regel, die man beim
 * Ausprobieren sofort sehen will.</p>
 *
 * <p>⛔ DER ZÄHLER GEHÖRT IN {@link BBSPerfConfig}, nicht hierher. Ein statisches Feld in
 * dieser Klasse hätte nach einem Kernel-Tausch eine zweite, leere Kopie — und die Messung
 * stünde danach still auf 0, was wie „das Feature greift nicht" aussieht.</p>
 */
public final class SuspendSaveLogik
{
    private SuspendSaveLogik() {}

    /** {@code true} = der Handler soll den periodischen Speicherlauf abbrechen. */
    public static boolean aussetzen()
    {
        if (!BBSPerfConfig.ENABLE_SUSPEND_SAVE_WHILE_PLAYING || !BBSPerfConfig.filmIsPlaying)
        {
            return false;
        }

        BBSPerfConfig.suspendedSaves++;

        return true;
    }
}
