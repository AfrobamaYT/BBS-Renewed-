package com.afrobama.bbstools.client.solid;

import com.afrobama.bbstools.perf.BBSPerfConfig;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.utils.VideoRecorder;

/**
 * Die EINE Frage des Features: zeichnet dieser Bildaufbau gerade die Solid-Vorschau?
 *
 * <h2>Warum die Entscheidung an genau einer Stelle steht</h2>
 *
 * <p>Blender haelt seinen Darstellungsmodus (Solid / Material / Rendered) NICHT am Objekt,
 * sondern an der Ansicht: {@code View3D.shading}. Die Objekte wissen nichts davon, und die
 * Render-Pipeline (F12) liest das Feld gar nicht erst. Genau daher kann dort nie eine
 * Vorschau-Einstellung im fertigen Bild landen — nicht weil jemand daran gedacht hat, sie
 * abzuschalten, sondern weil der Exportweg sie nie sieht.
 *
 * <p>Dieselbe Bauform hier: der Schalter haengt am ZEICHENWEG (welche Pipeline waehlt
 * {@code ModelVAOPipeline}), nie an der Form, am Modell, an der Textur oder am Film. Es gibt
 * nichts, was ein Export uebernehmen koennte — er waehlt beim naechsten Zeichenbefehl wieder
 * die volle Pipeline, weil {@link #aktiv()} dann false sagt. Kein Zustand, der
 * zurueckgesetzt werden muesste, kein Aufraeumen, das man vergessen kann.
 *
 * <h2>⛔ Die vier Bedingungen, jede aus einem eigenen Grund</h2>
 *
 * <ol>
 *   <li><b>Der Nutzer will es.</b> Abschaltbar per Einstellung, Vorgabe AUS — das Feature
 *       aendert das Aussehen JEDES Modells in der Ansicht, so etwas darf sich niemand
 *       ungefragt einfangen.</li>
 *   <li><b>Optimize ist an.</b> Woertliche Vorgabe: „beim export muesste das dann automatisch
 *       auf den vollen render greifen (nur wenn der optimize aber an ist)". Ohne den
 *       Schnellpfad ist es keine Optimierung, sondern nur eine andere Ansicht.</li>
 *   <li><b>Es wird nicht exportiert.</b> Siehe {@link #exportiertGerade()}.</li>
 *   <li><b>Es ist die 3D-Ansicht.</b> Blenders Darstellungsmodus gilt fuer das 3D-Fenster,
 *       nicht fuer die Vorschaubilder im Asset-Browser. Hier heisst das: die Symbole der
 *       Formen in der Oberflaeche (ModelFormRenderer.renderUI) bleiben texturiert, denn dort
 *       ist {@code renderingWorld} false. Sie kosten ohnehin nichts — es gibt eine Handvoll,
 *       keine Menge.</li>
 * </ol>
 *
 * <h2>⚠️ Wenn das Feature nichts tut, ist DAS hier der erste Verdaechtige</h2>
 *
 * <p>Vier UND-Bedingungen heissen vier Arten, still nichts zu tun. Reihenfolge beim Suchen:
 * {@code BBSPerfConfig.ENABLE_FAST_PATH} steht auf {@code false} als Vorgabe (Opt-in via
 * {@code /bbstools perf}) — das ist die wahrscheinlichste. Danach
 * {@code BBSRendering.isCustomSize()}: sollte sich zeigen, dass die Filmtafel die
 * Sondergroesse auch OHNE Export setzt, blockt Bedingung 3 dauerhaft, und dann ist der
 * Aufruf ersatzlos zu streichen — {@code isRecording()} allein traegt den Export bereits.
 */
public final class SolidVorschau
{
    /**
     * Ob der naechste Modell-Zeichenbefehl die Solid-Pipeline nehmen soll.
     *
     * <p>Wird je Zeichenbefehl gefragt, nicht je Bild und schon gar nicht gemerkt: nur so
     * greift ein Umschalten sofort und kann kein alter Wert einen Export vergiften.
     */
    public static boolean aktiv()
    {
        /* Null-sicher: die Einstellungen werden erst beim Laden der Konfiguration angelegt,
         * gezeichnet wird aber schon vorher (Ladebildschirm). Dasselbe Muster wie in
         * JfrAutostart. */
        if (BBSSettings.solidPreview == null || !BBSSettings.solidPreview.get())
        {
            return false;
        }

        if (!BBSPerfConfig.ENABLE_FAST_PATH)
        {
            return false;
        }

        if (exportiertGerade())
        {
            return false;
        }

        return BBSRendering.isRenderingWorld();
    }

    /**
     * Laeuft gerade ein Video-Export?
     *
     * <ul>
     *   <li>{@code VideoRecorder.isRecording()} — das etablierte Tor dieses Baums. Genau so
     *       schuetzt {@code ModelFormRendererFastPathLogik.classify} den Export bereits vor
     *       dem Crowd-Schnellpfad („full model must render during export"). Beide Exportwege
     *       (Welt-Session und Editor-Recorder) schreiben Frames erst ab diesem Flag in die
     *       Datei — es schuetzt das ERGEBNIS vollstaendig.</li>
     *   <li>{@code isVideoExportDelayPending()} — das Aufwaerm-Fenster des Welt-Exports
     *       (Countdown vor dem ersten Frame), als Guertel dazu.</li>
     * </ul>
     *
     * <p>⛔ {@code BBSRendering.isCustomSize()} stand hier in der Werkbank-Fassung und ist
     * GEMESSEN gestrichen (2026-08-11, Instanz 2): die Film-Vorschau rendert dauerhaft in
     * Videoaufloesung, isCustomSize ist im Editor also IMMER true und haette die Vorschau
     * fuer immer geblockt. Als Export-Signal traegt es nichts: WorldVideoExportSession
     * setzt es mit {@code recorder.isRecording()} als Wert — es laeuft dem Recorder-Flag
     * hinterher, nie voraus.</p>
     *
     * <p>⚠️ {@code Throwable} wird geschluckt, weil diese Frage im Zeichenweg steht: ein
     * Fehler beim FRAGEN darf keinen Bildaufbau kosten. Er faellt dann auf „ja, Export" —
     * die sichere Seite.
     */
    private static boolean exportiertGerade()
    {
        try
        {
            VideoRecorder aufzeichner = BBSModClient.getVideoRecorder();

            if (aufzeichner != null && aufzeichner.isRecording())
            {
                return true;
            }

            return BBSModClient.isVideoExportDelayPending();
        }
        catch (Throwable egal)
        {
            return true;
        }
    }

    private SolidVorschau()
    {}
}
