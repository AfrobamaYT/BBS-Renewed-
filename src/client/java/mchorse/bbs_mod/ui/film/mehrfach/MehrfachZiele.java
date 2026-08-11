package mchorse.bbs_mod.ui.film.mehrfach;

import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.utils.DataPath;
import mchorse.bbs_mod.utils.clips.Clip;

/**
 * Findet zu einem wert des ANKER-clips den gleichnamigen wert eines anderen clips.
 *
 * <p>Anker = der clip, dessen eigenschaften-panel offen ist, also der clip, dessen regler
 * der nutzer angefasst hat. Er ist der bezugspunkt des deltas (so in der aufgabe
 * festgelegt) und liegt in {@code IUIClipsDelegate.getClip()}.</p>
 *
 * <h2>⚠ DREI FALLEN, die hier abgefangen werden</h2>
 * <ol>
 *   <li>{@code BaseValueGroup.getRecursively} WIRFT bei unbekanntem pfad
 *       ({@code IllegalStateException}) — es gibt kein null zurueck. Bei gemischter
 *       auswahl (audio-clip + untertitel-clip) ist genau das der normalfall. Deshalb
 *       ausschliesslich {@link mchorse.bbs_mod.settings.values.base.BaseValueGroup#findRecursively}.</li>
 *   <li>{@code BaseValue.getRelativePath} haengt die id des vorfahren vorne an. Ein clip
 *       hat die id {@code ""}, der pfad beginnt also mit einem LEEREN segment
 *       ({@code ["", "volume"]}). Das ist gewollt und darf nicht „aufgeraeumt" werden —
 *       {@code findRecursively} zaehlt genau damit.</li>
 *   <li>{@code findRecursively} kann bei einem einsegmentigen pfad die GRUPPE SELBST
 *       zurueckgeben statt null. Der typvergleich unten faengt das mit ab.</li>
 * </ol>
 */
public final class MehrfachZiele
{
    private MehrfachZiele()
    {}

    /**
     * Der pfad des wertes innerhalb seines clips, mit dem er in einem anderen clip
     * wiedergefunden wird. {@code null}, wenn der wert gar nicht unter dem anker haengt —
     * dann darf nichts verteilt werden.
     */
    public static DataPath pfad(BaseValue ankerWert, Clip anker)
    {
        return ankerWert == null || anker == null ? null : ankerWert.getRelativePath(anker);
    }

    /**
     * Der wert desselben pfades im ziel-clip, oder {@code null}, wenn dieser clip die
     * eigenschaft nicht hat oder sie einen anderen typ hat.
     *
     * <p>Der typvergleich ist ABSICHTLICH {@code getClass() ==} und keine
     * {@code instanceof}-pruefung: eine {@code ValueInt}-eigenschaft darf nicht auf eine
     * abgeleitete andere bedeutung im nachbarclip geschrieben werden, nur weil sie
     * zufaellig zuweisbar waere.</p>
     */
    public static BaseValue gegenstueck(Clip ziel, DataPath pfad, BaseValue vorlage)
    {
        if (ziel == null || pfad == null || vorlage == null)
        {
            return null;
        }

        BaseValue wert = ziel.findRecursively(pfad);

        if (wert == null || wert == ziel)
        {
            return null;
        }

        return wert.getClass() == vorlage.getClass() ? wert : null;
    }
}
