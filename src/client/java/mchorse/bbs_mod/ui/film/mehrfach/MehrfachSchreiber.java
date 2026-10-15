package mchorse.bbs_mod.ui.film.mehrfach;

import java.util.List;
import java.util.function.Consumer;

import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.utils.DataPath;
import mchorse.bbs_mod.utils.clips.Clip;

/**
 * Traegt EINE aenderung aus dem eigenschaften-panel auf ALLE gewaehlten clips.
 *
 * <p>Das ist der ganze motor des features. Er kommt ohne aenderung an den panels aus,
 * weil er das delta nicht vom bedienelement erfragt, sondern am ANKER-clip abliest:
 * wert vorher merken, den vom panel gelieferten schreib-auftrag auf den anker anwenden,
 * die differenz nehmen. Damit wirkt jedes panel, das schon heute {@code editMultiple}
 * benutzt, ohne eine einzige geaenderte zeile als delta.</p>
 *
 * <h2>Reihenfolge und warum sie so ist</h2>
 * <ol>
 *   <li>Der ANKER wird IMMER geschrieben — auch wenn die auswahl leer ist. Vorher fiel
 *       eine aenderung bei leerer auswahl lautlos auf den boden (der flug-modus oeffnet
 *       ueber {@code UIClipsPanel.pickActiveClip} ein panel, ohne die zeitleisten-auswahl
 *       zu setzen).</li>
 *   <li>Erst danach steht die differenz fest, also erst danach koennen die uebrigen
 *       clips bedient werden.</li>
 * </ol>
 *
 * <h2>⚠ FALLEN</h2>
 * <ul>
 *   <li>{@code ziel == anker} wird per REFERENZ verglichen. {@code Clip} erbt
 *       {@code ValueGroup.equals}, das den INHALT vergleicht — zwei inhaltsgleiche clips
 *       gelten dort als gleich, und ein {@code equals}-vergleich wuerde den falschen clip
 *       ueberspringen.</li>
 *   <li>Die grenzen des BEDIENELEMENTS muessen zu denen des WERTES passen. Klemmt das
 *       feld frueher als der wert, kommt keine neue zahl mehr an, die differenz ist 0 und
 *       die ganze gruppe steht — ohne fehlermeldung. Beim anlegen eines feldes deshalb
 *       {@code limit(wert)} benutzen, nicht von hand getippte grenzen.</li>
 * </ul>
 */
public final class MehrfachSchreiber
{
    private MehrfachSchreiber()
    {}

    /**
     * @param anker      der clip, dessen panel offen ist (bezugspunkt des deltas)
     * @param ziele      alle clips, auf die die aenderung wirken soll, anker eingeschlossen
     * @param ankerWert  der wert IM ANKER, den das bedienelement anfasst
     * @param schreiben  der schreib-auftrag des panels — setzt einen ABSOLUTEN wert
     */
    @SuppressWarnings("unchecked")
    public static <T extends BaseValue> void schreibe(Clip anker, List<Clip> ziele, T ankerWert, Consumer<T> schreiben)
    {
        if (ankerWert == null || schreiben == null)
        {
            return;
        }

        /* VOR dem schreiben fragen: die geste steckt in einem statischen zustand, den das
         * bedienelement nur fuer die dauer SEINES callbacks setzt. */
        boolean delta = MehrfachWert.nimmtDelta(ankerWert);
        double vorher = delta ? MehrfachWert.lies(ankerWert) : 0D;

        schreiben.accept(ankerWert);

        if (ziele == null || ziele.isEmpty())
        {
            return;
        }

        double unterschied = delta ? MehrfachWert.lies(ankerWert) - vorher : 0D;

        if (delta && unterschied == 0D)
        {
            return;
        }

        DataPath pfad = MehrfachZiele.pfad(ankerWert, anker);

        if (pfad == null)
        {
            return;
        }

        for (Clip ziel : ziele)
        {
            if (ziel == anker)
            {
                continue;
            }

            BaseValue gegen = MehrfachZiele.gegenstueck(ziel, pfad, ankerWert);

            if (gegen == null)
            {
                continue;
            }

            if (delta)
            {
                MehrfachWert.addiere(gegen, unterschied);
            }
            else
            {
                schreiben.accept((T) gegen);
            }
        }
    }
}
