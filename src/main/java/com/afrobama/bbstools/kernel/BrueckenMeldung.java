package com.afrobama.bbstools.kernel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Meldet einen AUFLÖSUNGSFEHLER der brücken genau EINMAL — statt ihn still zu schlucken.
 *
 * <h2>Das problem, gemessen (2026-07-28)</h2>
 *
 * <p>{@code MixinBruecke} und {@code KernelBruecke} lieferten bei jedem auflösungsfehler ein
 * nacktes {@code null}: kernel-klasse nicht gefunden, methode nicht gefunden, feld nicht
 * gefunden. Ein handler nach dem brücken-muster tat dann NICHTS und schrieb keine einzige
 * zeile ins log. Zwei handler liefen so pro frame und pro replay ins leere:</p>
 *
 * <ul>
 *   <li>{@code WorldTransformBakeLogik} — 3 aufrufe aus
 *       {@code BaseFilmControllerWorldTransformMixin} (applyReplay HEAD/RETURN,
 *       updateEntityAndForm HEAD)</li>
 *   <li>{@code FilmEditorWorldTransformLogik} — 2 aufrufe aus
 *       {@code FilmEditorControllerWorldTransformMixin}</li>
 * </ul>
 *
 * <p>Beide kernel-klassen EXISTIERTEN NICHT — keine quelldatei im baum und keine in der
 * git-historie. Die mixins wurden auf das brücken-muster umgebaut, die logik-klassen kamen
 * nie mit. Die beiden playback-optimierungen waren seither tot, und ohne meldung sah das
 * niemand.</p>
 *
 * <p><b>ERLEDIGT 2026-07-29:</b> beide mixins entfernt. Beweisbar ohne verhaltensänderung —
 * {@code rufeUndAbbrechen} liefert bei fehlender klasse {@code false}, die {@code @Inject}s
 * haben also NIE gecancelt; eines der beiden stand zudem in keiner {@code mixins.json} und
 * wurde gar nicht gewoben. Der entwurf steht in
 * {@code docs/OFFEN-WORLD-TRANSFORM-OPTIMIERUNG.md}.</p>
 *
 * <h2>Warum EINMAL, und warum der schlüssel nicht gebaut werden darf</h2>
 *
 * <p>Die betroffenen pfade feuern pro frame mal pro replay — bei 20k replays wäre eine meldung
 * pro fehlschlag ein log-flut UND ein performance-problem. Deshalb:</p>
 *
 * <ul>
 *   <li>Ein schlüssel wird höchstens einmal gemeldet. Zweiter treffer = ein
 *       {@code containsKey} und zurück.</li>
 *   <li>⛔ FALLE: der meldungs-TEXT wird erst NACH dem containsKey gebaut. Alle parameter
 *       hier sind bereits vorhandene referenzen — der aufrufer darf für den aufruf KEINE
 *       neue string-verkettung machen. Genau diese art unsichtbarer arbeit auf dem
 *       fehler-pfad hat 2026-07-27 schon einmal 87 % CPU gekostet (siehe kommentar an
 *       {@code kernelLoaderKlasse}).</li>
 *   <li>Der schlüssel der brücken enthält die GENERATION. Nach einem kernel-swap wird ein
 *       weiter bestehender fehler also genau einmal neu gemeldet — das ist gewollt, denn der
 *       swap sollte ihn ja behoben haben.</li>
 * </ul>
 */
public final class BrueckenMeldung
{
    private BrueckenMeldung() {}

    private static final Logger LOG = LoggerFactory.getLogger("bbstools-bruecke");

    /**
     * Deckel gegen ein unbegrenztes wachsen der schlüssel-menge.
     *
     * <p>Es gibt aufrufer mit DYNAMISCHEM methodennamen (z.B.
     * {@code CubicModelAnimatorThreadSafeMixin} reicht ein feld-namen durch). Käme dort je ein
     * neuer name pro frame, wüchse die map unbegrenzt. Ab dem deckel wird nur noch gezählt.</p>
     */
    private static final int DECKEL = 200;

    /** Schlüssel -> gemeldeter text. Auch der leser des mess-kanals will den text sehen. */
    private static final Map<String, String> GEMELDET = new ConcurrentHashMap<>();

    /** Meldungen, die der deckel verworfen hat — sonst wäre das schweigen wieder still. */
    private static volatile long verworfen;

    /**
     * Meldet einen auflösungsfehler EINMAL pro schlüssel, laut (ERROR).
     *
     * @param schluessel eine bereits vorhandene zeichenkette, die den fehlerfall eindeutig
     *                   macht. NICHT für diesen aufruf zusammenbauen, siehe klassen-javadoc.
     * @param grund      konstante beschreibung, z.B. {@code "kernel-klasse nicht gefunden"}
     * @param bruecke    {@code "MixinBruecke"} oder {@code "KernelBruecke"}
     * @param klasse     einfacher kernel-klassenname aus dem aufruf
     * @param methode    methodenname aus dem aufruf
     */
    public static void einmal(String schluessel, String grund, String bruecke, String klasse,
                              String methode)
    {
        einmal(schluessel, grund, bruecke, klasse, methode, null);
    }

    /** Wie {@link #einmal(String, String, String, String, String)}, mit ursache im text. */
    public static void einmal(String schluessel, String grund, String bruecke, String klasse,
                              String methode, Throwable ursache)
    {
        if (GEMELDET.containsKey(schluessel))
        {
            return;
        }

        if (GEMELDET.size() >= DECKEL)
        {
            verworfen++;

            return;
        }

        String text = bruecke + ": " + grund + " — " + klasse + "#" + methode
            + ". Der handler ist WIRKUNGSLOS, bis das behoben ist."
            + (ursache == null ? "" : " ursache: " + ursache);

        /* putIfAbsent entscheidet das rennen: nur der erste thread loggt. */
        if (GEMELDET.putIfAbsent(schluessel, text) != null)
        {
            return;
        }

        /* Mit Stack: "ExceptionInInitializerError" allein nennt weder Klasse noch Ursache
         * (weltAnfang in echten Instanzen, 2026-10-14). Gemeldet wird ohnehin nur einmal. */
        LOG.error(text, ursache);
    }

    /**
     * Meldet EINMAL einen erwarteten, nicht-fehlerhaften zustand (INFO statt ERROR).
     *
     * <p>WOFÜR: auf einem client/server OHNE hotpatch-agent findet {@code KernelBruecke} nie
     * eine kernel-klasse. Das ist der dokumentierte normalfall und darf nicht als fehler pro
     * aufrufstelle schreien — aber EINE zeile soll es geben, damit nachvollziehbar ist, warum
     * die betroffenen handler nichts tun.</p>
     */
    public static void einmalHinweis(String schluessel, String text)
    {
        if (GEMELDET.containsKey(schluessel) || GEMELDET.size() >= DECKEL)
        {
            return;
        }

        if (GEMELDET.putIfAbsent(schluessel, text) != null)
        {
            return;
        }

        LOG.info(text);
    }

    /** Anzahl verschiedener gemeldeter fehlerfälle. */
    public static int anzahl()
    {
        return GEMELDET.size();
    }

    /** Zeile für den mess-kanal: wie viele fälle, und welche. */
    public static String zusammenfassung()
    {
        if (GEMELDET.isEmpty())
        {
            return "meldungen=0";
        }

        StringBuilder sb = new StringBuilder("meldungen=").append(GEMELDET.size());

        if (verworfen > 0)
        {
            sb.append(" (deckel erreicht, verworfen=").append(verworfen).append(")");
        }

        for (String text : GEMELDET.values())
        {
            sb.append("\n  ").append(text);
        }

        return sb.toString();
    }
}
