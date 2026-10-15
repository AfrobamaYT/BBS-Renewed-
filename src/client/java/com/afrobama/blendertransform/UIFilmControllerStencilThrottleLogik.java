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
     * Ist gerade ein Replay ausgewaehlt, wird also ein Umriss aus dem Stencil GEZEICHNET?
     *
     * <p>Bewusst tolerant: laesst sich das nicht feststellen, verhaelt sich der Throttle wie
     * bisher — ein Anzeigefehler ist aergerlich, ein gerissener Renderpfad schlimmer.
     */
    private static boolean zeigtUmriss(Object zielObjekt)
    {
        try
        {
            if (!mchorse.bbs_mod.BBSSettings.editorReplayOutline.get())
            {
                return false;
            }

            mchorse.bbs_mod.ui.film.controller.UIFilmController controller =
                (mchorse.bbs_mod.ui.film.controller.UIFilmController) zielObjekt;

            return controller.panel.replayEditor.getReplay() != null;
        }
        catch (Throwable t)
        {
            return false;
        }
    }

    /**
     * Die Kamera dieses Bildes, als Zahl zum Vergleichen.
     *
     * <p>⛔ DRITTER FALL DERSELBEN LÜCKE (vom Nutzer gemeldet 2026-08-02): „die orangene
     * silhoute bewegt sich genau wie das schatten problem bei kamera bewegung".
     *
     * <p>Die Annahme des Throttles war ursprünglich „das Pick-Ergebnis ändert sich nur, wenn
     * sich der CURSOR bewegt". Zweimal hat sie schon nicht gereicht — Playback und Scrubben
     * kamen als {@code szeneBewegt} dazu. Der dritte Fall ist die KAMERA: beim Fliegen fängt
     * der Editor den Cursor ein ({@code toggleMousePointer}), {@code context.mouseX/Y} steht
     * also still, und ohne laufendes Playback wechselt auch kein Tick. Der Puffer wird dann
     * bis zu einer Sekunde nicht neu gefüllt, während sich das Bild darunter komplett
     * verschiebt — und weil aus diesem Puffer seit dem Replay-Umriss GEZEICHNET wird, steht
     * die Silhouette sichtbar neben der Figur.
     *
     * <p>Gemessen vor dem Fix (Editor offen, Cursor still): {@code stencil.eingang} = 5 gegen
     * {@code umriss.gezeichnet} = 159 in derselben Messung — der Umriss lief also 30× so oft
     * wie die Füllung, aus der er liest.
     *
     * <p>Verglichen wird die View-Rotation, die der Weltdurchgang zu Beginn jedes Bildes in
     * {@code BBSRendering.camera} stempelt (WorldRendererMixin, HEAD von LevelRenderer.render)
     * — dieselbe Matrix, mit der {@code renderStencil} den Puffer füllt. Bewusst tolerant:
     * geht das Lesen schief, verhält sich der Throttle wie bisher.
     */
    /**
     * Alte, parameterlose Fassung — bleibt bestehen, damit die Klasse HOTSWAPBAR bleibt.
     *
     * <p>⛔ NICHT LOESCHEN. Beim Ersetzen der Signatur schlug der Tausch fehl mit
     * {@code UnsupportedOperationException: delete method not implemented} — JDI kann
     * Methoden hinzufuegen, aber nicht entfernen. Eine Signaturaenderung ist damit ein
     * Boot-Grund, ein Zusatz nicht. Sie liegt hier also nur, um genau den zu vermeiden.</p>
     */
    private static long kameraStand()
    {
        return kameraStand(null);
    }

    private static long kameraStand(Object zielObjekt)
    {
        /* ⛔ VIERTER ANLAUF — UND DIESMAL KEINE VERMUTUNG, SONDERN DER WERT, MIT DEM
         *    GEZEICHNET WIRD (2026-08-04).
         *
         * Hier stand `gameRenderer.mainCamera()`. Gemessen hat dieser Vergleich bei bewegter
         * Kamera 186 Bewegungen gegen 6198 Stillstaende gezaehlt — 97 % der Bilder galten als
         * ruhig, und der Nutzer sah den Versatz weiter. Der Kommentar von damals schloss mit
         * "welche Kamera der Editor wirklich fuehrt, ist damit offen".
         *
         * Seit dem Umriss-Fix ist sie nicht mehr offen: der Editor zeichnet mit
         * `worldRenderContext.worldState().cameraRenderState` (UIFilmController, Zeile ~1358).
         * mainCamera() ist eine ANDERE Kamera — deshalb konnte der Vergleich die Bewegung
         * gar nicht sehen. Eine Sonde, die einen anderen Wert liest als den benutzten, kann
         * den Fehler nicht finden; sie beendet die Suche mit Gewissheit.
         *
         * Gelesen wird Drehung UND Position: wer geradeaus fliegt, dreht nichts, und der
         * dritte Anlauf ist genau daran gescheitert.
         *
         * Bewusst tolerant: geht das Lesen schief, faellt es auf mainCamera() zurueck und
         * der Throttle verhaelt sich wie bisher — schlechter als vorher wird es nie. */
        try
        {
            java.lang.reflect.Field f = zielObjekt.getClass().getDeclaredField("worldRenderContext");
            f.setAccessible(true);
            Object ctx = f.get(zielObjekt);

            if (ctx != null)
            {
                Object levelState = ctx.getClass().getMethod("levelState").invoke(ctx);
                Object cam = levelState.getClass().getField("cameraRenderState").get(levelState);

                org.joml.Matrix4f dreh =
                    (org.joml.Matrix4f) cam.getClass().getField("viewRotationMatrix").get(cam);
                net.minecraft.world.phys.Vec3 p =
                    (net.minecraft.world.phys.Vec3) cam.getClass().getField("pos").get(cam);

                /* Es geht nur um "hat sich etwas geaendert", nicht um den Wert. */
                return java.lang.Double.doubleToLongBits(p.x) * 31L
                     + java.lang.Double.doubleToLongBits(p.y) * 131L
                     + java.lang.Double.doubleToLongBits(p.z) * 1531L
                     + java.lang.Float.floatToIntBits(dreh.m00()) * 15131L
                     + java.lang.Float.floatToIntBits(dreh.m02()) * 151531L
                     + java.lang.Float.floatToIntBits(dreh.m20()) * 1515131L
                     + java.lang.Float.floatToIntBits(dreh.m22()) * 15151531L;
            }
        }
        catch (Throwable ignoriert)
        {
            /* faellt unten auf den alten Weg zurueck */
        }

        try
        {
            net.minecraft.client.Camera k =
                net.minecraft.client.Minecraft.getInstance().gameRenderer.mainCamera();

            net.minecraft.world.phys.Vec3 p = k.position();

            return java.lang.Double.doubleToLongBits(p.x) * 31L
                 + java.lang.Double.doubleToLongBits(p.y) * 131L
                 + java.lang.Double.doubleToLongBits(p.z) * 1531L
                 + java.lang.Float.floatToIntBits(k.xRot()) * 15131L
                 + java.lang.Float.floatToIntBits(k.yRot()) * 151531L;
        }
        catch (Throwable t)
        {
            return 0L;
        }
    }

    /** Kamerastand des letzten Füllens, je Controller-Instanz. */
    private static volatile java.util.Map<Object, Long> letzteKamera;

    private static synchronized java.util.Map<Object, Long> kameraSpeicher()
    {
        java.util.Map<Object, Long> m = letzteKamera;

        if (m == null)
        {
            /* Lazy wie tickSpeicher(): nach einem Hotswap laufen statische Initialisierer
             * nicht, das Feld bliebe null und der erste Zugriff würde in JEDEM Bild werfen. */
            m = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
            letzteKamera = m;
        }

        return m;
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
        long kamera = kameraStand(zielObjekt);
        Long vorigeKamera = kameraSpeicher().get(zielObjekt);
        boolean kameraBewegt = vorigeKamera != null && vorigeKamera.longValue() != kamera;

        /* ⛔ WIRD AUS DEM PUFFER GEZEICHNET, DARF ER NICHT ALTERN (2026-08-02).
         *
         * Der Throttle stammt aus einer Zeit, in der dieser Puffer NUR gepickt wurde: teuer
         * ist der glReadPixels-Stall, und ein Pick-Ergebnis aendert sich nur unter einem
         * bewegten Cursor. Seit dem Replay-Umriss wird aus demselben Puffer GEZEICHNET —
         * damit ist jedes uebersprungene Bild direkt sichtbar.
         *
         * Drei Anlaeufe haben das mit immer feineren Bewegungs-Erkennern zu retten versucht
         * (Playback, Tick, Kamera). Der letzte ist gemessen gescheitert: der Kamera-Vergleich
         * meldete bei bewegter Kamera 186 Bewegungen gegen 6198 Stillstaende — 97 % der
         * Bilder galten als ruhig, und der Nutzer sah den Versatz weiter. Welche Kamera der
         * Editor wirklich fuehrt, ist damit offen; eine vierte Vermutung waere geraten.
         *
         * Deshalb die Regel, die keine Vermutung braucht: ist der Umriss SICHTBAR, wird
         * nicht gedrosselt. Dann ist der Puffer ein Anzeige-Element und muss zu dem Bild
         * passen, ueber dem er liegt. Ohne ausgewaehltes Replay (kein Umriss) greift der
         * Throttle unveraendert voll — dort ist der eingesparte Stall echter Gewinn. */
        boolean umrissSichtbar = zeigtUmriss(zielObjekt);

        boolean szeneBewegt = spieltAb(zielObjekt)
            || (tick != Integer.MIN_VALUE && vorigerTick != null && vorigerTick.intValue() != tick)
            || kameraBewegt
            || umrissSichtbar;

        /* ⚗️ Zaehler an der ENTSCHEIDUNG. Nach dem Kamera-Auslöser fiel das Verhältnis von
         * 1:32 auf 1:13 — er greift also, aber nicht durchgängig. Nur diese drei Zahlen
         * trennen die Fälle: bewegt sich die Kamera und wird TROTZDEM gedrosselt, oder
         * merkt der Vergleich die Bewegung gar nicht erst? */
        mchorse.bbs_mod.graphics.Messpunkt.zaehle(
            kameraBewegt ? "throttle.kamera-bewegt" : "throttle.kamera-still");

        if (!moved && !altChanged && !szeneBewegt && (now - lastRunMs) < 1000L)
        {
            mchorse.bbs_mod.graphics.Messpunkt.zaehle("throttle.gedrosselt");
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
        kameraSpeicher().put(zielObjekt, kamera);

        return false;
    }
}
