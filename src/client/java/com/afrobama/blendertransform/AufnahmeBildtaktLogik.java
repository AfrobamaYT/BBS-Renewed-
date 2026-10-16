package com.afrobama.blendertransform;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.utils.VideoRecorder;

/**
 * Logik hinter {@code RenderTickCounterMixin} — waehrend einer Aufnahme laeuft die Spielzeit
 * nicht nach der Uhr, sondern nach der Bildrate des Videos.
 *
 * <p>Gegenstueck zu {@link AufnahmeServerTaktLogik}: die zaehlt hier gesetzte
 * {@code serverTicks} auf der Serverseite ab.</p>
 *
 * <p>⛔ WARUM WERTE STATT FELDZUGRIFF. Der Handler haengt an fuenf Feldern des
 * {@code DeltaTracker.Timer} — drei {@code @Shadow}, zwei vom Mixin angebaute. Die Bruecke
 * koennte sie ueber {@code feldLesen}/{@code feldSchreiben} erreichen, aber das waeren zehn
 * Reflexionsaufrufe JE BILD. Stattdessen kommen die Werte herein und als Feld
 * {@code neu[]} zurueck; der Handler schreibt sie. Kostet einen Aufruf und ein Array.</p>
 *
 * <p>⚠️ DIE INDIZES SIND DER VERTRAG. Wer hier etwas einfuegt, muss den Handler mitziehen —
 * ein verschobener Index vertauscht stillschweigend Restbetrag und Zeitstempel.</p>
 */
public final class AufnahmeBildtaktLogik
{
    private AufnahmeBildtaktLogik() {}

    /** Ergebnis-Indizes. */
    public static final int REST = 0;
    public static final int TAKTE = 1;
    public static final int LETZTE_MS = 2;
    public static final int GEHALTENE = 3;
    public static final int LETZTE_BILDZEIT = 4;
    public static final int RUECKGABE = 5;

    /** {@link #RUECKGABE} auf diesem Wert heisst: Vanillas Rueckgabe bleibt stehen. */
    public static final int KEINE_RUECKGABE = -1;

    /**
     * {@code DeltaTracker.Timer.advanceGameTime} @HEAD.
     *
     * <p>⛔ DER FRUEHE AUSSTIEG ZAEHLT {@code gehaltene} NICHT HOCH. Das ist kein Versehen:
     * wird ein Bild wegen der Bildratenbremse uebersprungen, hat es nicht stattgefunden, und
     * die Haltezaehlung darf davon nichts mitbekommen. Wer das Hochzaehlen ans Ende zieht,
     * verkuerzt bei aktiver Bremse jede gehaltene Bildfolge.</p>
     *
     * <p>⚠️ {@code getCounter() == 0} setzt den Restbetrag zurueck — der erste Takt einer
     * Aufnahme startet sauber, sonst wanderte der Rest der letzten Aufnahme hinein.</p>
     *
     * @return {@code null}, wenn nicht aufgenommen wird und nichts zu tun ist; sonst die
     *         neuen Feldwerte in der oben festgelegten Reihenfolge
     */
    public static double[] takt(long jetztMs, float rest, float takte, long letzteMs,
        int gehaltene, long letzteBildzeit)
    {
        VideoRecorder rekorder = BBSModClient.getVideoRecorder();

        if (rekorder == null || !rekorder.isRecording())
        {
            /* Nicht aufnehmen heisst: die Haltezaehlung zuruecksetzen, sonst startet die
             * naechste Aufnahme mitten in einer alten Bildfolge. */
            return new double[] {rest, takte, letzteMs, 0, 0, KEINE_RUECKGABE};
        }

        if (rekorder.getCounter() == 0)
        {
            rest = 0F;
        }

        if (gehaltene != 0)
        {
            BBSRendering.canRender = false;

            return new double[] {rest, takte, letzteMs,
                weiterZaehlen(gehaltene), letzteBildzeit, 0};
        }

        if (BBSSettings.videoLimitFrameRate.get())
        {
            long abstand = (long) (1000F / BBSRendering.getVideoFrameRate());

            if (jetztMs - letzteBildzeit < abstand)
            {
                BBSRendering.canRender = false;

                /* Ohne weiterZaehlen — siehe oben. */
                return new double[] {rest, takte, letzteMs, gehaltene, letzteBildzeit, 0};
            }

            letzteBildzeit = jetztMs;
        }

        /* Capture the requested starting tick before advancing to the next video frame. */
        takte = rekorder.getCounter() == 0 ? 0F : 20F / (float) BBSRendering.getVideoFrameRate();
        rest += takte;

        int ganze = (int) rest;

        rest -= ganze;
        rekorder.serverTicks += ganze;
        BBSRendering.canRender = true;

        return new double[] {rest, takte, jetztMs, weiterZaehlen(gehaltene), letzteBildzeit, ganze};
    }

    /**
     * {@code SystemTimeUniforms.Timer.beginFrame} @HEAD — dieselbe Rechnung fuer IRIS' Uhr.
     *
     * <p>⛔ SHADER HABEN EINE EIGENE ZEIT. Iris fuehrt {@code frameTimeCounter} und
     * {@code lastFrameTime} getrennt vom Spiel; laeuft die Aufnahme, muss diese Uhr im
     * Videotakt weitergehen statt in Echtzeit — sonst ruckeln zeitabhaengige Shader
     * (Wasser, Wind, Wolken) im fertigen Video, waehrend die Welt sauber laeuft.</p>
     *
     * <p>⚠️ Anders als {@link #takt} zaehlt diese Uhr NICHT in Takten, sondern in Sekunden:
     * {@code lastFrameTime} bekommt 20/Bildrate, der Zaehler 1/Bildrate.</p>
     *
     * @return {@code {zaehler, letzteBildzeit, gehaltene, abbrechen}} — {@code abbrechen}
     *         ungleich 0 heisst, Iris' eigener Rumpf wird uebersprungen
     */
    public static double[] shaderTakt(float zaehler, float letzteBildzeit, int gehaltene)
    {
        VideoRecorder rekorder = BBSModClient.getVideoRecorder();

        if (rekorder == null || !rekorder.isRecording())
        {
            return new double[] {zaehler, letzteBildzeit, 0, 0};
        }

        float bildrate = BBSRendering.getVideoFrameRate();

        if (gehaltene == 0)
        {
            letzteBildzeit = 20F / bildrate;
            zaehler += 1F / bildrate;
        }

        return new double[] {zaehler, letzteBildzeit, weiterZaehlen(gehaltene), 1};
    }

    private static int weiterZaehlen(int gehaltene)
    {
        int naechste = gehaltene + 1;

        return naechste >= BBSSettings.videoHeldFrames.get() ? 0 : naechste;
    }
}
