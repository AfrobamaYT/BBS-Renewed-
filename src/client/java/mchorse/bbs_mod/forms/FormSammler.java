package mchorse.bbs_mod.forms;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;

/**
 * Der Sammler, in den Formen gerade ihre Zeichenauftraege einreichen.
 *
 * <h2>Warum es ihn gibt</h2>
 *
 * <p>In 26.2 zeichnet niemand mehr sofort: man reicht einen Auftrag bei einem
 * {@link SubmitNodeCollector} ein, und der Bildgraph zeichnet ihn spaeter in der richtigen
 * Reihenfolge. Der Sammler kommt vom Weltdurchgang ganz oben; BBS' Form-Renderer brauchen
 * ihn ganz unten.
 *
 * <p>Dieselbe Bruecke wie {@code PickZiel} und {@code Zeichenbereich}, aus demselben Grund:
 * das Ding als Parameter durch jede Form-Renderer-Signatur zu reichen waere richtig, aber
 * es sind ein Dutzend Ketten, und der Port haette dann eine Baustelle mehr statt einer
 * weniger.
 *
 * <h2>⚠️ Die Oberflaechen-Wege haben KEINEN Sammler</h2>
 *
 * <p>{@code renderInUI} zeichnet eine Form in ein Bedienelement, nicht in die Welt -- dort
 * laeuft kein Weltdurchgang und es gibt nichts einzureichen. Diese Wege muessen ihren
 * eigenen Durchgang oeffnen (siehe SofortZeichner/GehaltenesNetz) oder, fuer ganze
 * Vanilla-Szenen, ueber Mojangs PictureInPicture gehen.
 *
 * <p>Darum wirft {@link #pflicht()} statt {@code null} zurueckzugeben: ein Form-Renderer,
 * der im UI-Weg nach dem Weltsammler greift, bekommt eine klare Meldung und keine
 * Nullzeiger-Ausnahme funfzehn Zeilen spaeter.
 */
public final class FormSammler
{
    private static SubmitNodeCollector aktiv;

    public static void anmelden(SubmitNodeCollector sammler)
    {
        aktiv = sammler;
    }

    public static void abmelden()
    {
        aktiv = null;
    }

    /** Der angemeldete Sammler, oder {@code null} ausserhalb eines Weltdurchgangs. */
    public static SubmitNodeCollector aktiv()
    {
        return aktiv;
    }

    /**
     * Fuehrt {@code zeichnen} mit einem EIGENEN Sammler aus und laesst ihn danach zeichnen.
     *
     * <h2>Der Weg, den Vanilla selbst geht</h2>
     *
     * <p>Genau so loest 26.2 dieselbe Aufgabe fuer seine eigenen Vorschaubilder in
     * Bedienoberflaechen (Spielermodell im Inventar, Bannertisch, Buchmodell). Der Ablauf
     * steht in {@code PictureInPictureRenderer.prepare} und besteht aus drei Schritten:
     *
     * <ol>
     *   <li>einen frischen {@link SubmitNodeStorage} anlegen,</li>
     *   <li>die Geometrie dort EINREICHEN,</li>
     *   <li>{@code FeatureRenderDispatcher.renderAllFeatures(storage)} rufen -- das ist der
     *       Schritt, der aus eingereichten Auftraegen ein Bild macht.</li>
     * </ol>
     *
     * <p>Nachgelesen in Vanillas Bytecode, nicht erfunden: ohne den dritten Schritt sammelt
     * man Auftraege, die niemand ausfuehrt, und das Bedienelement bleibt leer -- ein stiller
     * Fehler ohne jede Meldung.
     *
     * <p>⚠️ Der vorherige Sammler wird gemerkt und wiederhergestellt. Ohne das risse ein
     * Vorschaubild, das waehrend eines Weltdurchgangs gezeichnet wird, dem Weltdurchgang
     * seinen Sammler weg.
     *
     * <p>⚠️ {@code renderAllFeatures} laeuft NACH dem Wiederherstellen, aber mit dem eigenen
     * Sammler als Argument -- es zeichnet also genau das, was in diesem Abschnitt eingereicht
     * wurde, und nichts sonst.
     */
    public static void mitEigenemSammler(Runnable zeichnen)
    {
        SubmitNodeStorage eigener = new SubmitNodeStorage();
        SubmitNodeCollector vorheriger = aktiv;

        aktiv = eigener;

        try
        {
            zeichnen.run();
        }
        finally
        {
            aktiv = vorheriger;
        }

        zeichnenLassen(eigener);
    }

    /**
     * Laesst den eingereichten Stapel zeichnen.
     *
     * <h2>⚠️ NOCH NICHT FERTIG: das Ziel passt nicht zusammen</h2>
     *
     * <p>Der Zeichenweg oeffnet einen Durchgang mit Farb- UND Tiefenziel und lehnt ihn ab,
     * wenn die Masse auseinandergehen: <em>"Depth texture: size does not match expected
     * attachment size. Is 854x480 expected 2560x1440"</em>. 854x480 ist genau die
     * oberflaechenskalierte Groesse eines 2560x1440-Fensters.
     *
     * <p>AUSGESCHLOSSEN, gemessen und nicht vermutet: es liegt NICHT an
     * {@code RenderSystem.outputColorTextureOverride} / {@code outputDepthTextureOverride}.
     * Beide wurden hier versuchsweise fuer die Dauer geleert und wiederhergestellt -- die
     * Meldung blieb Wort fuer Wort dieselbe. Der Versuch ist deshalb wieder draussen, statt
     * als unbelegter Ballast stehenzubleiben.
     *
     * <p>Der naechste Schritt ist der Weg, den Vanilla wirklich geht: seine Vorschaubilder
     * zeichnen NIE ins Hauptbild, sondern in eine EIGENE Textur mit eigener Tiefe
     * ({@code PictureInPictureRenderer.prepare} setzt vor dem Einreichen beide Umleitungen
     * auf die eigene Textur und dazu die Projektion). Solange das fehlt, trifft der
     * eingereichte Stapel auf ein Ziel, das ihm nicht gehoert.
     */
    private static void zeichnenLassen(SubmitNodeStorage eigener)
    {
        Minecraft.getInstance().gameRenderer.featureRenderDispatcher().renderAllFeatures(eigener);
    }

    public static SubmitNodeCollector pflicht()
    {
        if (aktiv == null)
        {
            throw new IllegalStateException(
                "Kein Sammler angemeldet. Formen koennen nur WAEHREND eines Weltdurchgangs "
                + "einreichen; die Oberflaechen-Wege (renderInUI) muessen einen eigenen "
                + "Durchgang oeffnen statt nach dem Weltsammler zu greifen.");
        }

        return aktiv;
    }

    private FormSammler()
    {}
}
