package mchorse.bbs_mod.ui.film;

import com.mcstudio.nano.ZonenMelder;

import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.renderers.TimelineRulerRenderer;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.Clips;

/**
 * Klickflaechen der Clip-Spuren fuer die Abnahme.
 *
 * <p>⛔ GEGEN DEN TREFFERTEST DES PRODUKTS VERIFIZIERT, nicht nur nachgerechnet. Die Lage
 * kommt aus {@code toGraphX}/{@code toLayerY}/{@code getLayerHeight} — denselben
 * Umrechnungen, mit denen gezeichnet wird. Danach wird der gemeldete Klickpunkt durch die
 * UMKEHRUNG geschickt ({@code fromGraphX}/{@code fromLayerY} → {@code getClipAt}); nennt sie
 * einen anderen Clip, wird die Zone NICHT gemeldet.</p>
 *
 * <p>Das faengt genau die Faelle, die eine nachgerechnete Zone still falsch machen: das
 * Lineal-Band am oberen Rand, waagerechtes Scrollen, Clips ausserhalb des sichtbaren
 * Bereichs. Eine fehlende Zone ist ehrlich; eine Zone, die danebenliegt, meldet Fehler,
 * die sie selbst erzeugt hat.</p>
 */
public final class ClipZonen
{
    private ClipZonen()
    {
    }

    public static void melden(ZonenMelder melder, UIClips ui)
    {
        Clips clips = ui.getClips();

        if (clips == null)
        {
            return;
        }

        Area flaeche = ui.area;
        /* ⛔ DAS LINEAL-BAND AUSSCHLIESSEN. Clips belegen ihre logische Zeile AUCH hinter dem
         * Lineal — getClipAt nennt sie dort, aber handleLeftClick scrubbt statt zu greifen.
         * Eine Zone dort waere geometrisch richtig und trotzdem eine Luege: gemessen wurde
         * clip/2/0 gemeldet, der Klick darauf liess die Auswahl bei clip/0/0 stehen.
         * getRulerBottom ist dieselbe Quelle, die isInRuler benutzt. */
        int oberkante = TimelineRulerRenderer.getRulerBottom(flaeche);
        int hoehe = ui.getLayerHeight();

        if (hoehe <= 0)
        {
            return;
        }

        for (Clip clip : clips.get())
        {
            int x1 = Math.max(ui.toGraphX(clip.tick.get()), flaeche.x);
            int x2 = Math.min(ui.toGraphX(clip.tick.get() + clip.duration.get()), flaeche.ex());
            int y1 = Math.max(ui.toLayerY(clip.layer.get()), oberkante);
            int y2 = Math.min(ui.toLayerY(clip.layer.get()) + hoehe, flaeche.ey());

            if (x2 - x1 <= 0 || y2 - y1 <= 0)
            {
                continue;
            }

            int kx = (x1 + x2) / 2;
            int ky = (y1 + y2) / 2;

            /* Die Gegenprobe: fuehrt der eigene Klickpunkt zu genau diesem Clip zurueck? */
            if (clips.getClipAt(ui.fromGraphX(kx), ui.fromLayerY(ky)) != clip)
            {
                continue;
            }

            melder.melden("clip/" + clip.layer.get() + "/" + clip.tick.get(),
                x1, y1, x2 - x1, y2 - y1);
        }
    }
}
