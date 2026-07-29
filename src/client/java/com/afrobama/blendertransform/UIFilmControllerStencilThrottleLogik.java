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

    /**
     * Läuft das Playback gerade?
     *
     * <p>Der Tick-Vergleich allein füllt nur bei Tick-Wechseln neu, also 20x/s. Zwischen zwei
     * Ticks bewegt sich die Figur aber interpoliert weiter ({@code transition} im Render-Pfad),
     * und genau das blieb als Rest-Versatz sichtbar — der User: „das ist jetzt besser aber
     * nicht komplett synchron". Solange abgespielt wird, ist jeder Frame ein neuer Stand.</p>
     *
     * <p>⛔ WAS DAS KOSTET, sauber gemessen (2026-07-29, DEV, laufendes Playback, A/B über drei
     * Runden abwechselnd, je 3 s Settle und 4 s Messung):</p>
     * <pre>
     *   bei Tick-Wechsel : fps 51, 55, 77  -> Median 55
     *   in jedem Frame   : fps 49, 51, 67  -> Median 51      (~7 % weniger)
     * </pre>
     *
     * <p>⚠ Eine frühere Messung ergab hier 10 statt 69 fps und hätte diese Fassung beinahe
     * verhindert. Sie war unbrauchbar: direkt nach dem Playback-Start gemessen (Aufwärmphase)
     * und mit meiner eigenen Messsonde im Render-Pfad. Der Vergleich muss abwechselnd und mit
     * Settle laufen, sonst wird ein driftender Zustand einer Seite zugeschlagen.</p>
     *
     * <p>Bei stehendem Bild greift der Throttle unverändert voll — dort ist der eingesparte
     * glReadPixels-Stall echter Gewinn, und der alte Puffer ist ja korrekt.</p>
     */
    private static boolean spieltAb(Object zielObjekt)
    {
        try
        {
            mchorse.bbs_mod.ui.film.controller.UIFilmController controller =
                (mchorse.bbs_mod.ui.film.controller.UIFilmController) zielObjekt;

            return controller.panel.getRunner().isRunning();
        }
        catch (Throwable t)
        {
            return false;
        }
    }

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
        boolean szeneBewegt = spieltAb(zielObjekt)
            || (tick != Integer.MIN_VALUE && vorigerTick != null && vorigerTick.intValue() != tick);

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
