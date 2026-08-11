package mchorse.bbs_mod.ui.film.mehrfach;

import java.util.function.ObjDoubleConsumer;
import java.util.function.ToDoubleFunction;

import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.utils.DataPath;
import mchorse.bbs_mod.utils.clips.Clip;

/**
 * Wie {@link MehrfachSchreiber}, aber fuer EINE ZAHL IN EINEM VERBUND-WERT:
 * x/y/z eines {@code ValuePoint}, yaw/pitch/roll/fov eines {@code ValueAngle}.
 *
 * <p>WARUM EIGEN: ein punkt ist kein {@code BaseValueNumber}. {@code MehrfachSchreiber}
 * kann an ihm kein delta bilden, weil er nicht weiss, WELCHE der drei zahlen sich geaendert
 * hat — der schreib-auftrag des panels ist eine undurchsichtige {@code Consumer}-lambda.
 * Hier reicht das bedienelement die eine zahl mit lese- und schreib-zugriff herein, und
 * damit ist das delta wieder eindeutig.</p>
 *
 * <p>Deckt {@code UIPointModule} und {@code UIAngleModule} ab — und damit position, winkel
 * und FOV in Idle-, Dolly-, Path-, Look-, Orbit-, Translate-, Tracker- und Audio-clips,
 * ohne dass eines dieser panels etwas davon wissen muss.</p>
 *
 * <p>⚠ Die geste entscheidet genauso wie bei {@link MehrfachWert}: ziehen = delta,
 * tippen = derselbe absolute wert auf allen. Der aufruf muss deshalb AUS dem callback des
 * zahlenfeldes kommen, sonst steht die eingabeart auf KEINE und alles wird absolut.</p>
 */
public final class MehrfachKomponente
{
    private MehrfachKomponente()
    {}

    /**
     * @param editor     liefert anker-clip und die uebrigen gewaehlten clips
     * @param ankerWert  der punkt/winkel IM ANKER-clip (darf null sein, bevor
     *                   {@code fill(...)} lief — dann passiert nichts)
     * @param lesen      holt die eine zahl aus dem verbund-wert
     * @param schreiben  setzt die eine zahl im verbund-wert
     * @param neu        der wert, den das feld jetzt anzeigt
     */
    @SuppressWarnings("unchecked")
    public static <W> void schreibe(IUIClipsDelegate editor, BaseValueBasic<W> ankerWert,
        ToDoubleFunction<W> lesen, ObjDoubleConsumer<W> schreiben, double neu)
    {
        if (ankerWert == null || editor == null)
        {
            return;
        }

        /* VOR dem schreiben fragen — die eingabeart gilt nur waehrend des callbacks. */
        boolean delta = MehrfachWert.gesteIstZug();
        double vorher = lesen.applyAsDouble(ankerWert.get());

        BaseValue.edit(ankerWert, (w) -> schreiben.accept(w.get(), neu));

        Clip anker = editor.getClip();
        double unterschied = neu - vorher;

        if (anker == null)
        {
            return;
        }

        DataPath pfad = MehrfachZiele.pfad(ankerWert, anker);

        if (pfad == null || (delta && unterschied == 0D))
        {
            return;
        }

        for (Clip ziel : editor.getMehrfachZiele())
        {
            /* Referenz-vergleich: Clip.equals vergleicht den INHALT, siehe MehrfachSchreiber. */
            if (ziel == anker)
            {
                continue;
            }

            BaseValue gegen = MehrfachZiele.gegenstueck(ziel, pfad, ankerWert);

            if (!(gegen instanceof BaseValueBasic))
            {
                continue;
            }

            BaseValueBasic<W> zielWert = (BaseValueBasic<W>) gegen;
            double wert = delta ? lesen.applyAsDouble(zielWert.get()) + unterschied : neu;

            BaseValue.edit(zielWert, (w) -> schreiben.accept(w.get(), wert));
        }
    }
}
