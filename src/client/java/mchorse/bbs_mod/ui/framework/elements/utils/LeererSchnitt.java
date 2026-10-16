package mchorse.bbs_mod.ui.framework.elements.utils;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;

import java.util.Deque;

/**
 * Was Vanilla statt eines LEEREN Ausschnitts bekommt.
 *
 * <h2>⛔ Wofuer (2026-08-04, Absturzgruppe G15)</h2>
 *
 * <pre>
 *   IllegalArgumentException: Scissor size must be >0, was 0x0
 * </pre>
 *
 * <p>In {@code Batcher2D#clip} stand {@code enableScissor(x, y, x, y)} -- also 0x0 -- direkt
 * unter einem Kommentar, der das Gegenteil versprach ("ein leerer Schnitt wird NICHT an
 * Vanilla gereicht"). Richtig gemeint, falsch umgesetzt.</p>
 *
 * <p>Am 2026-08-04 aus dem Bytecode von 26.2 abgelesen, nicht vermutet:</p>
 * <ul>
 * <li>{@code RenderPass.enableScissor} wirft bei {@code w <= 0 || h <= 0}.</li>
 * <li>{@code GuiRenderer.enableScissor} klemmt die Groesse mit {@code Math.max(0, ...)} und
 *     reicht die 0 GENAU DORT hinein -- Vanilla faengt es also nicht ab.</li>
 * </ul>
 *
 * <p>{@code Batcher2D#submit} verwirft leere Ausschnitte laengst. Was blieb, sind VANILLAS
 * Zeichenauftraege in diesem Fenster (Text, Gegenstaende) -- die kennen unseren Spiegel
 * nicht. Dazu passt die Signatur des Berichts: im Stapel steht keine einzige BBS-Zeile.</p>
 *
 * <h2>Warum eine eigene Klasse</h2>
 *
 * <p>Nicht aus Stilgruenden, sondern damit es hotswapbar bleibt: {@code Batcher2D} ist
 * mixin-gewoben, und dieser Weg laeuft ueber {@code Instrumentation.redefineClasses}. Der
 * kann weder ein Feld noch eine Methode hinzufuegen ("attempted to change the schema" /
 * "attempted to add a method"), NEUE Klassen dagegen laden lazy. Ein Helfer hier kostet
 * keinen Boot, derselbe Helfer in {@code Batcher2D} haette einen gekostet.</p>
 */
public final class LeererSchnitt
{
    /**
     * Ein gueltiges 1x1-Rechteck fuer einen leeren Ausschnitt.
     *
     * <p>1x1, weil es nichts Kleineres gibt, das 26.2 noch annimmt. Der Punkt muss IM
     * Eltern-Ausschnitt liegen, sonst schneidet Vanillas eigener Stapel ihn wieder auf leer
     * -- deshalb die linke obere Ecke des naechsten nicht leeren Vorfahren. Weil jeder leere
     * Abschnitt genau diesen Ersatz bekommt, liegen verschachtelte Leere alle auf demselben
     * Punkt und schneiden sich gegenseitig nicht weg.</p>
     *
     * @param vorfahren die eigenen Ausschnitte, oberster zuerst -- darf leer sein
     * @param sw        Breite der Oberflaeche, fuer den Fall ganz ohne Vorfahren
     * @param sh        Hoehe der Oberflaeche
     */
    public static ScreenRectangle ersatzPunkt(Deque<ScreenRectangle> vorfahren,
        int x, int y, int sw, int sh)
    {
        if (vorfahren != null)
        {
            for (ScreenRectangle vorfahr : vorfahren)
            {
                if (vorfahr.width() > 0 && vorfahr.height() > 0)
                {
                    return new ScreenRectangle(vorfahr.left(), vorfahr.top(), 1, 1);
                }
            }
        }

        int px = Math.max(0, Math.min(x, Math.max(0, sw - 1)));
        int py = Math.max(0, Math.min(y, Math.max(0, sh - 1)));

        return new ScreenRectangle(px, py, 1, 1);
    }

    /** Die Oberflaeche in den Koordinaten, in denen Batcher2D schneidet. */
    public static ScreenRectangle bildschirm(GuiGraphicsExtractor context)
    {
        return new ScreenRectangle(0, 0, context.guiWidth(), context.guiHeight());
    }

    /**
     * Was von einem Schnitt auf dem Bildschirm liegt; leer, wenn nichts.
     *
     * <h2>⛔ Auch ausserhalb des Fensters ist leer (2026-10-11, Aufgabe 113)</h2>
     *
     * <p>26.3s {@code GuiRenderer.enableScissor} rechnet die Breite als
     * {@code max(0, min(rechts * skala, fensterbreite) - links * skala)} (Bytecode gelesen). Ein
     * Schnitt ganz rechts neben dem Fenster wird dort 0 breit und wirft am Bildende
     * {@code Scissor size must be >0, was 0x36}. Genau das taten Wellenformen von Ton-Clips, die
     * in einer langen Zeitleiste rechts ausserhalb lagen. Die Leer-Pruefung in Batcher2D entging
     * es, weil das Rechteck in GUI-Koordinaten nicht leer ist; eine jdb-Beobachtung von
     * {@code ScreenRectangle.width} fand kein einziges Rechteck mit Breite 0.</p>
     */
    public static ScreenRectangle sichtbar(ScreenRectangle rect, ScreenRectangle bildschirm)
    {
        ScreenRectangle sichtbar = bildschirm.intersection(rect);

        return sichtbar == null ? ScreenRectangle.empty() : sichtbar;
    }

    private LeererSchnitt()
    {}
}
