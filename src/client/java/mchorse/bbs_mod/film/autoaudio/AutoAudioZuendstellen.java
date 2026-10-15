package mchorse.bbs_mod.film.autoaudio;

import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * WO ein partikel-effekt zuendet, ausgedrueckt in film-ticks.
 *
 * <p>Das ist der eigentliche kern von Auto Audio — nicht der ton. Ein zuenden ist eine
 * FLANKE von "paused = true" nach "paused = false". Solange der effekt schon laeuft,
 * zuendet er nicht erneut, egal wie oft der renderer in der zwischenzeit partikel
 * ausstoesst (VanillaParticleFormRenderer.tick emittiert alle {@code frequency} ticks —
 * daraus einen ton je stoss zu machen waere ein rattern, kein toneffekt).</p>
 *
 * <p>Reine rechnung: kein Minecraft, kein UI, keine dateien. Damit ist sie ohne laufendes
 * spiel pruefbar.</p>
 *
 * <h2>Die drei faelle, die der nutzer beschrieben hat</h2>
 * <ul>
 *   <li>KEIN keyframe, {@code paused = false} am formular → genau EIN zuenden bei tick 0.</li>
 *   <li>KEIN keyframe, {@code paused = true} → gar kein zuenden.</li>
 *   <li>Keyframes → tick 0 wenn es dort schon laeuft, dazu jede true→false-flanke.</li>
 * </ul>
 *
 * <h2>⚠ FALLE: ein kanal mit keyframes regiert die GANZE zeitleiste</h2>
 * {@link KeyframeChannel#findSegment(float)} liefert vor dem ERSTEN keyframe ein segment aus
 * genau diesem keyframe — der ruhewert des formulars kommt also nicht mehr vor. Wer den
 * ruhewert fuer "vor dem ersten keyframe" einsetzt, bekommt eine zuendstelle bei tick 0, die
 * im spiel nicht existiert. Deshalb wird {@link #wert} IMMER ueber den kanal befragt, sobald
 * er nicht leer ist.
 *
 * <h2>⚠ FALLE: boolesche kanaele werden nicht interpoliert</h2>
 * {@code BooleanKeyframeFactory.interpolate} gibt den LINKEN keyframe zurueck — eine
 * treppenfunktion. Der wert kurz vor keyframe i ist also exakt der wert von keyframe i-1,
 * auch wenn die beiden nur einen halben tick auseinanderliegen. Darum wird die flanke am
 * keyframe-NACHBARN abgelesen und nicht an {@code wert(tick - 1)}.
 */
public class AutoAudioZuendstellen
{
    /**
     * Obergrenze fuer erzeugte zuendstellen. Ein replay mit {@code looping = 1} ueber einen
     * langen film ergaebe sonst tausende audio-clips — jeder davon landet im export
     * (WorldVideoExportSession sammelt AudioClips aus film.camera). Lieber laut abschneiden
     * als dem nutzer eine tonspur unterschieben, die er nicht mehr aufraeumen kann.
     */
    public static final int MAX_ZUENDSTELLEN = 512;

    /** Ergebnis: die ticks, und ob dabei an der obergrenze abgeschnitten wurde. */
    public record Ergebnis(List<Integer> ticks, boolean abgeschnitten)
    {}

    /**
     * @param kanal      Der {@code paused}-kanal aus {@code Replay.properties}, oder {@code null}/leer
     *                   wenn der nutzer die eigenschaft nie keyframed hat.
     * @param ruhewert   {@code VanillaParticleForm.paused} am formular selbst — gilt nur, solange
     *                   der kanal leer ist (siehe falle oben).
     * @param filmDauer  {@code film.camera.calculateDuration()}. Begrenzt die schleifen-wiederholung.
     * @param schleife   {@code Replay.looping}; 0 = keine schleife. Der film-controller rechnet
     *                   {@code replay.getTick(tick)} VOR {@code applyProperties}, die
     *                   paused-folge wiederholt sich also wirklich.
     */
    public static Ergebnis finden(KeyframeChannel<Boolean> kanal, boolean ruhewert, int filmDauer, int schleife)
    {
        List<Integer> innerhalb = flankenImZyklus(kanal, ruhewert, schleife > 0 ? schleife : Integer.MAX_VALUE);
        boolean startZuendet = !wert(kanal, 0F, ruhewert);

        if (schleife <= 0)
        {
            Set<Integer> ticks = new LinkedHashSet<>();

            if (startZuendet)
            {
                ticks.add(0);
            }

            ticks.addAll(innerhalb);

            return begrenzen(ticks);
        }

        /* Beim umbruch zaehlt, was am ZYKLUS-ENDE galt: lief der effekt dort schon, ist der
         * sprung zurueck auf tick 0 keine flanke und darf nicht toenen. */
        boolean endeWarPausiert = wert(kanal, schleife - 1, ruhewert);
        boolean umbruchZuendet = endeWarPausiert && startZuendet;
        int zyklen = Math.max(1, (int) Math.ceil(Math.max(filmDauer, 1) / (double) schleife));
        Set<Integer> ticks = new LinkedHashSet<>();

        for (int c = 0; c < zyklen; c++)
        {
            int basis = c * schleife;

            if (c == 0 ? startZuendet : umbruchZuendet)
            {
                ticks.add(basis);
            }

            for (int tick : innerhalb)
            {
                ticks.add(basis + tick);
            }

            if (ticks.size() > MAX_ZUENDSTELLEN)
            {
                break;
            }
        }

        return begrenzen(ticks);
    }

    /**
     * Jede true→false-flanke, die an einem keyframe sitzt. Tick 0 gehoert NICHT hierher —
     * dort gibt es keinen vorgaenger, ueber den man eine flanke definieren koennte; das
     * erledigt der aufrufer ueber {@code !wert(0)}.
     */
    private static List<Integer> flankenImZyklus(KeyframeChannel<Boolean> kanal, boolean ruhewert, int grenze)
    {
        List<Integer> flanken = new ArrayList<>();

        if (kanal == null || kanal.isEmpty())
        {
            return flanken;
        }

        List<Keyframe<Boolean>> keyframes = kanal.getKeyframes();

        for (int i = 1; i < keyframes.size(); i++)
        {
            Keyframe<Boolean> vorher = keyframes.get(i - 1);
            Keyframe<Boolean> jetzt = keyframes.get(i);
            int tick = Math.round(jetzt.getTick());

            if (tick <= 0 || tick >= grenze)
            {
                continue;
            }

            if (istPausiert(vorher.getValue(), ruhewert) && !istPausiert(jetzt.getValue(), ruhewert))
            {
                flanken.add(tick);
            }
        }

        return flanken;
    }

    /** Der wirksame {@code paused}-wert an einem tick — genau so, wie FormProperties ihn setzt. */
    public static boolean wert(KeyframeChannel<Boolean> kanal, float tick, boolean ruhewert)
    {
        if (kanal == null || kanal.isEmpty())
        {
            return ruhewert;
        }

        return istPausiert(kanal.interpolate(tick, ruhewert), ruhewert);
    }

    /** Ein keyframe-wert kann aus alten filmen als null zurueckkommen — dann gilt der ruhewert. */
    private static boolean istPausiert(Boolean wert, boolean ruhewert)
    {
        return wert == null ? ruhewert : wert;
    }

    private static Ergebnis begrenzen(Set<Integer> ticks)
    {
        List<Integer> sortiert = new ArrayList<>(ticks);

        sortiert.sort(Integer::compareTo);

        if (sortiert.size() <= MAX_ZUENDSTELLEN)
        {
            return new Ergebnis(sortiert, false);
        }

        return new Ergebnis(new ArrayList<>(sortiert.subList(0, MAX_ZUENDSTELLEN)), true);
    }
}
