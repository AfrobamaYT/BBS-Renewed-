package com.afrobama.blendertransform;

import com.afrobama.bbstools.client.blender.MixinBruecke;

/**
 * Logik des stencil-pick-throttles — im kernel-paket, damit sie hotswapbar ist.
 *
 * <p>Herausgelöst aus {@code UIFilmControllerStencilThrottleMixin} (2026-07-26). BBS'
 * {@code UIFilmController.renderStencil} läuft JEDEN frame, in dem die maus über dem
 * 3D-viewport steht, und macht dabei einen {@code glReadPixels}-GPU→CPU-sync-stall. Das
 * pick-ergebnis kann sich nur ändern, wenn sich die maus bewegt — steht sie still (und kein
 * alt-wechsel, und die 1s-sicherheitsfrist ist nicht um), wird der re-run übersprungen und
 * das letzte pick weiterverwendet.</p>
 *
 * <p>⚠ ZUSTAND: die vier {@code bbstools$...}-felder sind {@code @Unique}-felder der
 * ZIELKLASSE ({@code UIFilmController}) — echte instanzfelder nach dem weben. Gelesen/
 * geschrieben über {@link MixinBruecke#feldLesen}/{@link MixinBruecke#feldSchreiben} auf der
 * ziel-instanz, NICHT in einer kernel-globalen map (mehrere film-controller-instanzen
 * möglich, z.B. mehrere geöffnete filme).</p>
 *
 * <p>⚠ KEINE KERNEL-TYPEN IN DER SIGNATUR. {@code context} ist ein BBS-typ, {@code aktiv} ein
 * primitiver flag-wert aus {@code BBSPerfConfig}.</p>
 */
public final class UIFilmControllerStencilThrottleLogik
{
    private UIFilmControllerStencilThrottleLogik() {}

    /**
     * Der Film-Tick, bei dem der Stencil zuletzt gefüllt wurde — je Controller-Instanz.
     *
     * <p>⛔ NICHT im statischen Initialisierer anlegen: nach einem Hotswap dieser Klasse
     * laufen die nicht, das Feld bliebe null und der erste Zugriff wirft in JEDEM Frame
     * (am 2026-07-29 genau so eine Instanz abgeschossen). {@link #tickSpeicher()} legt
     * es lazy an und ist damit egal, wie die Klasse in die JVM kam.</p>
     *
     * <p>Schwach referenziert und je Instanz getrennt — es können mehrere Film-Controller
     * offen sein, und ein geschlossener soll nicht im Speicher hängen bleiben.</p>
     */
    private static volatile java.util.Map<Object, Integer> letzterTick;

    private static synchronized java.util.Map<Object, Integer> tickSpeicher()
    {
        java.util.Map<Object, Integer> m = letzterTick;

        if (m == null)
        {
            m = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
            letzterTick = m;
        }

        return m;
    }

    /**
     * Der aktuelle Film-Tick, oder {@link Integer#MIN_VALUE}, wenn er nicht zu holen ist.
     *
     * <p>Bewusst tolerant: ist kein Film geladen oder ändert sich BBS' Aufbau, soll der
     * Throttle in seinen alten Zustand zurückfallen und nicht das Rendern reissen.</p>
     */
    private static int aktuellerTick(Object zielObjekt)
    {
        try
        {
            mchorse.bbs_mod.ui.film.controller.UIFilmController controller =
                (mchorse.bbs_mod.ui.film.controller.UIFilmController) zielObjekt;

            return controller.panel.getRunner().ticks;
        }
        catch (Throwable t)
        {
            return Integer.MIN_VALUE;
        }
    }

    /* ⛔ WARUM NICHT JEDEN FRAME FÜLLEN (gemessen 2026-07-29, DEV, laufendes Playback):
     *
     *   Throttle 1 s (vorher) :  1,0 Füllungen/s  bei 55-63 Frames/s   <- der gemeldete Fehler
     *   bei Tick-Wechsel      : 20,0 Füllungen/s  bei 62,8 Frames/s    <- diese Fassung
     *   jeden Frame           : 10,0 Füllungen/s  bei 10,0 Frames/s    <- unbrauchbar
     *
     * Ich hatte zusätzlich auf „Playback läuft" geprüft, damit auch die Interpolation ZWISCHEN
     * zwei Ticks mitgeht. Das kostet die halbe Bildrate und mehr: der Stencil-Durchlauf rendert
     * alle Formen in einen Offscreen-Puffer und liest bei Maus im Sichtfenster per glReadPixels
     * zurück (GPU→CPU-Stall). Ohne das Picken bleiben immer noch 33,6 statt 62,8 Frames/s — es
     * ist also nicht nur der Stall, das Füllen selbst kostet ebenfalls.
     *
     * Der Tick ist die richtige Grenze: die Animation ändert sich mit der Tick-Rate. Was bleibt,
     * ist der Zwischenschritt der Interpolation, also höchstens ein Tick (50 ms) statt vorher
     * bis zu 1000 ms. Für mehr müsste erst das Füllen billiger werden. */

    /**
     * @return {@code true}, wenn der aufrufende handler {@code ci.cancel()} rufen soll
     *         (cursor stand still, letztes pick bleibt gültig).
     */
    public static boolean throttlePick(Object zielObjekt, mchorse.bbs_mod.ui.framework.UIContext context,
                                       boolean altPressed)
    {
        if (!com.afrobama.bbstools.perf.BBSPerfConfig.ENABLE_STENCIL_PICK_THROTTLE) return false;
        if (context == null) return false;

        int mx = context.mouseX;
        int my = context.mouseY;
        long now = System.currentTimeMillis();

        Object lastXo = MixinBruecke.feldLesen(zielObjekt, "bbstools$lastMouseX");
        Object lastYo = MixinBruecke.feldLesen(zielObjekt, "bbstools$lastMouseY");
        Object lastAltO = MixinBruecke.feldLesen(zielObjekt, "bbstools$lastAlt");
        Object lastRunO = MixinBruecke.feldLesen(zielObjekt, "bbstools$lastRunMs");

        int lastX = lastXo instanceof Integer ? (Integer) lastXo : Integer.MIN_VALUE;
        int lastY = lastYo instanceof Integer ? (Integer) lastYo : Integer.MIN_VALUE;
        boolean lastAlt = lastAltO instanceof Boolean && (Boolean) lastAltO;
        long lastRunMs = lastRunO instanceof Long ? (Long) lastRunO : 0L;

        boolean moved = mx != lastX || my != lastY;
        boolean altChanged = altPressed != lastAlt;

        /* ⛔ DIE SZENE BEWEGT SICH AUCH OHNE MAUS (Fehler bis 2026-07-29, vom User gemeldet).
         *
         * Die ursprüngliche Annahme oben im Mixin lautet: „das Pick-Ergebnis kann sich nur
         * ändern, wenn sich der Cursor bewegt". Das gilt für einen STEHENDEN Editor. Läuft
         * das Playback, wandert die Figur unter dem ruhenden Cursor davon — und seit dem
         * Replay-Umriss wird dieser Puffer nicht mehr nur GEPICKT, sondern GEZEICHNET.
         * Ein übersprungener Frame ist damit direkt sichtbar.
         *
         * Gemessen (DEV, laufendes Playback, Cursor still): renderStencil lief 1,0x/s gegen
         * 55 UI-Frames/s — der Umriss zeigte einen bis zu 1 s alten Stand. Der User sah es
         * genau richtig: „der bbs eigene stencil ist auch nicht synchron und der orangene
         * stencil verfolgt den bbs stencil" — beide lesen denselben Puffer.
         *
         * Der Tick deckt Playback UND Scrubben ab und lässt den Throttle dort greifen, wo er
         * gedacht war: im wirklich stehenden Bild. Dann ist der alte Puffer ja korrekt. */
        int tick = aktuellerTick(zielObjekt);
        Integer vorigerTick = tickSpeicher().get(zielObjekt);
        boolean szeneBewegt = tick != Integer.MIN_VALUE
            && vorigerTick != null && vorigerTick.intValue() != tick;

        if (!moved && !altChanged && !szeneBewegt && (now - lastRunMs) < 1000L)
        {
            /* Cursor stationär, Bild steht → letztes pick bleibt gültig → stencil-render +
             * glReadPixels-stall überspringen. */
            com.afrobama.bbstools.perf.BBSPerfConfig.stencilPickSkips++;
            return true;
        }

        MixinBruecke.feldSchreiben(zielObjekt, "bbstools$lastMouseX", mx);
        MixinBruecke.feldSchreiben(zielObjekt, "bbstools$lastMouseY", my);
        MixinBruecke.feldSchreiben(zielObjekt, "bbstools$lastAlt", altPressed);
        MixinBruecke.feldSchreiben(zielObjekt, "bbstools$lastRunMs", now);
        tickSpeicher().put(zielObjekt, tick);

        return false;
    }
}
