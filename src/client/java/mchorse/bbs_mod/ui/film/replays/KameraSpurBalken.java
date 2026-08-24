package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.film.UIClips;
import mchorse.bbs_mod.ui.film.clips.renderer.IUIClipRenderer;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * Zeichnet einen Kamera-Clip in die Zeitleiste des Replay-Editors — mit demselben Renderer
 * wie die Kamera-Zeitleiste selbst.
 *
 * <p>USER-KORREKTUR 2026-07-29: erst zeichnete ich eigene, gedämpfte Kästchen, damit sie
 * „nicht stören". Sein Einwand: „da fehlt noch der look, beim tracker steht ja tracker samt
 * des icons, bei audio wäre es ne wave form … sonst sieht das wie ein random Kästchen in der
 * timeline aus." Recht hat er — ein Balken ohne Namen sagt nicht, WELCHER Clip das ist, und
 * genau das ist der Zweck der Anzeige.</p>
 *
 * <p>Der Renderer aus {@code UIClipRenderers} bringt Name, Icon, Hüllkurve und für Audio die
 * Wellenform mit. Er braucht eine {@link UIClips}-Instanz, benutzt daraus aber nur Fabrik,
 * Anzeigename und Renderer-Tabelle — nichts, was an der Zeitachse der Kamera-Leiste hängt
 * (nachgelesen in {@code UIClipRenderer} und {@code UIAudioClipRenderer}). Deshalb ist es
 * unbedenklich, die Instanz des Kamera-Editors durchzureichen und in unsere Fläche zu
 * zeichnen.</p>
 */
public final class KameraSpurBalken
{
    /** Ein Clip von 0 Ticks wäre unsichtbar — so bleibt er anfassbar. */
    public static final int MIN_BREITE = 3;

    private KameraSpurBalken()
    {
    }

    /**
     * Wie stark der Balken zurücktritt. Der Clip-Renderer malt für seine eigene Leiste, wo er
     * die Hauptsache ist; hier ist er Beiwerk hinter den Keyframes. USER 2026-07-29: „mach
     * vielleicht die opacity runter weil das lenkt ab." Der gewählte Clip bleibt kräftiger —
     * er ist der, an dem gerade gearbeitet wird.
     */
    /* Seit die Clips eigene Camera-Kopfzeilen haben (Entwurf), sind sie dort die
     * Hauptsache — der Schleier bleibt nur noch als leichte Zuruecknahme. */
    private static final float SCHLEIER = 0.18F;
    private static final float SCHLEIER_GEWAEHLT = 0.06F;

    public static void zeichne(UIContext context, UIClips clips, Clip clip, Area balken, boolean gewaehlt)
    {
        IUIClipRenderer renderer = clips.getRenderers().get(clip);

        /* Eine Zeile ist enger als die Kamera-Leiste; ein Pixel Luft oben und unten hält die
         * Balken benachbarter Layer optisch auseinander. */
        balken.y += 1;
        balken.h = Math.max(2, balken.h - 2);

        renderer.renderClip(context, clips, clip, balken, gewaehlt, gewaehlt);

        /* Gedämpft wird NACH dem Zeichnen mit einem Schleier in der Hintergrundfarbe, nicht
         * durch Eingriff in den Renderer: so bleibt jeder Clip-Typ bei seiner Darstellung —
         * Name, Icon, Wellenform — nur eben leiser. */
        context.batcher.box(balken.x, balken.y, balken.ex(), balken.ey(),
            Colors.setA(BBSSettings.deepSurface(), gewaehlt ? SCHLEIER_GEWAEHLT : SCHLEIER));
    }
}
