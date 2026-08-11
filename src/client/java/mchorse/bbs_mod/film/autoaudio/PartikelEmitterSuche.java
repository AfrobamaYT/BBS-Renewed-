package mchorse.bbs_mod.film.autoaudio;

import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.VanillaParticleForm;
import mchorse.bbs_mod.utils.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Findet jeden {@link VanillaParticleForm} in einem formular-baum — samt seinem PFAD.
 *
 * <p>Der pfad ist der teil, der wirklich arbeit macht: {@code Replay.properties} adressiert
 * eine eigenschaft ueber genau diesen pfad ({@code "0/2/paused"} = koerperteil 0, darin
 * koerperteil 2, dessen {@code paused}). Ohne ihn findet man den keyframe-kanal nicht, der
 * die zuendstellen traegt.</p>
 *
 * <p>Die pfad-bildung ist absichtlich die GLEICHE wie in
 * {@code FormUtils.collectPropertyPaths(Form, List, String)} — index des koerperteils,
 * verbunden mit {@code /}, wurzel ohne prefix. Wer sie hier anders schreibt, erzeugt
 * schluessel, die der keyframe-editor nie anlegt, und Auto Audio findet nie einen kanal.</p>
 */
public class PartikelEmitterSuche
{
    /**
     * @param pfad     Pfad des formulars im baum ({@code ""} fuer die wurzel).
     * @param formular Das gefundene partikel-formular.
     */
    public record Emitter(String pfad, VanillaParticleForm formular)
    {
        /** Schluessel des {@code paused}-kanals in {@code Replay.properties}. */
        public String pausedSchluessel()
        {
            return StringUtils.combinePaths(this.pfad, "paused");
        }
    }

    public static List<Emitter> finden(Form wurzel)
    {
        List<Emitter> gefunden = new ArrayList<>();

        sammeln(wurzel, "", gefunden);

        return gefunden;
    }

    private static void sammeln(Form form, String pfad, List<Emitter> gefunden)
    {
        if (form == null)
        {
            return;
        }

        if (form instanceof VanillaParticleForm partikel)
        {
            gefunden.add(new Emitter(pfad, partikel));
        }

        List<BodyPart> teile = form.parts.getAllTyped();

        for (int i = 0; i < teile.size(); i++)
        {
            sammeln(teile.get(i).getForm(), StringUtils.combinePaths(pfad, String.valueOf(i)), gefunden);
        }
    }
}
