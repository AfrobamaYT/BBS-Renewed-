package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.camera.clips.ClipFactoryData;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * Zeichnet einen Kamera-Clip als Balken in die Zeitleiste des Replay-Editors.
 *
 * <p>Absichtlich NICHT der {@code UIClipRenderer} der Kamera-Zeitleiste: der zeichnet
 * Hüllkurven, Schatten, Griffe und Beschriftung, weil er dort die Hauptsache ist. Hier ist
 * der Clip Beiwerk — er soll zeigen, WO etwas liegt, ohne die Keyframes zu überdecken.
 * Deshalb: gedämpfte Fläche in der Clip-Farbe, kräftiger Rand, sonst nichts.</p>
 *
 * <p>User-Vorgabe dazu: „die clips sollen aber nicht den replay editor stören … achte darauf
 * das es so gestaltet ist sodass es nicht beim arbeiten stört" (2026-07-29).</p>
 */
public final class KameraSpurBalken
{
    /** Ein Clip von 0 Ticks wäre unsichtbar — so bleibt er anfassbar. */
    public static final int MIN_BREITE = 3;

    private static final float FLAECHE = 0.16F;
    private static final float FLAECHE_GEWAEHLT = 0.34F;
    private static final float RAND = 0.55F;
    private static final float RAND_GEWAEHLT = 1F;

    private KameraSpurBalken()
    {
    }

    /** Farbe des Clips, wie sie auch die Kamera-Zeitleiste benutzt. */
    public static int farbe(Clip clip)
    {
        ClipFactoryData data = BBSMod.getFactoryCameraClips().getData(clip);

        return data == null ? Colors.GRAY : (data.color & Colors.RGB);
    }

    public static void zeichne(UIContext context, Clip clip, Area balken, boolean gewaehlt)
    {
        int rgb = farbe(clip);
        boolean an = clip.enabled.get();

        int flaeche = Colors.setA(rgb, an ? (gewaehlt ? FLAECHE_GEWAEHLT : FLAECHE) : FLAECHE * 0.5F);
        int rand = Colors.setA(rgb, an ? (gewaehlt ? RAND_GEWAEHLT : RAND) : RAND * 0.5F);

        context.batcher.box(balken.x, balken.y, balken.ex(), balken.ey(), flaeche);
        context.batcher.outline(balken.x, balken.y, balken.ex(), balken.ey(), rand);

        /* Die beiden Kanten kräftiger: an ihnen wird gekürzt, und sie sagen dem Auge, wo
         * der Clip anfängt und aufhört, auch wenn die Fläche unter Keyframes verschwindet. */
        if (balken.w > MIN_BREITE * 2)
        {
            context.batcher.box(balken.x, balken.y, balken.x + 1, balken.ey(), rand);
            context.batcher.box(balken.ex() - 1, balken.y, balken.ex(), balken.ey(), rand);
        }
    }
}
