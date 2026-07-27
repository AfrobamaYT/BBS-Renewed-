package com.afrobama.blendertransform;

import com.afrobama.bbstools.client.blender.MixinBruecke;

import mchorse.bbs_mod.camera.clips.misc.TrackerClientClip;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.ui.film.controller.UIFilmController;
import mchorse.bbs_mod.utils.clips.Clip;

/**
 * Logik hinter {@code UIFilmControllerTrackerPovMixin} — siehe {@link MixinBruecke} für das
 * muster.
 *
 * <p>Die zwei {@code @Unique}-felder des originals ({@code povBeforeTrackerSnap},
 * {@code wasControlling}) werden beim weben ECHTE felder der zielklasse — genau wie
 * {@code @Shadow}-felder. Sie sind hier über {@link MixinBruecke#feldLesen}/
 * {@link MixinBruecke#feldSchreiben} erreichbar, damit der zustand über zwei aufrufe
 * (captureBefore → autoSnapPov) hinweg an der ZIELINSTANZ hängt statt an einer
 * kernel-eigenen map (die würde bei einem swap den bezug zur instanz verlieren wollen —
 * feldLesen/Schreiben ist hier die korrekte, instanz-treue lösung).</p>
 */
public final class UiFilmControllerTrackerPovLogik
{
    private UiFilmControllerTrackerPovLogik() {}

    private static final String FELD_POV_VORHER = "bbstools$povBeforeTrackerSnap";
    private static final String FELD_WAR_CONTROLLING = "bbstools$wasControlling";

    public static void captureBefore(UIFilmController self)
    {
        MixinBruecke.feldSchreiben(self, FELD_WAR_CONTROLLING, self.isControlling());
    }

    public static void autoSnapPov(UIFilmController self)
    {
        boolean nowControlling = self.isControlling();
        Object wasControllingObj = MixinBruecke.feldLesen(self, FELD_WAR_CONTROLLING);
        boolean wasControlling = wasControllingObj instanceof Boolean b && b;

        /* Entered control mode this call. */
        if (!wasControlling && nowControlling)
        {
            if (self.getPovMode() == UIFilmController.CAMERA_MODE_FIRST_PERSON) return;
            if (!isTrackerActiveAtCursor(self)) return;
            if (!isCurrentReplayPlayerShaped(self)) return;

            MixinBruecke.feldSchreiben(self, FELD_POV_VORHER, self.getPovMode());
            self.setPov(UIFilmController.CAMERA_MODE_FIRST_PERSON);
            return;
        }

        /* Exited control mode this call. */
        if (wasControlling && !nowControlling)
        {
            Object povVorherObj = MixinBruecke.feldLesen(self, FELD_POV_VORHER);
            int povVorher = povVorherObj instanceof Integer i ? i : -1;

            if (povVorher >= 0)
            {
                self.setPov(povVorher);
                MixinBruecke.feldSchreiben(self, FELD_POV_VORHER, -1);
            }
        }
    }

    private static boolean isTrackerActiveAtCursor(UIFilmController self)
    {
        try
        {
            Film film = self.panel.getData();
            if (film == null) return false;
            int tick = self.panel.getCursor();
            for (Clip c : film.camera.getClips(tick))
            {
                if (c instanceof TrackerClientClip) return true;
            }
        }
        catch (Throwable ignored)
        {
        }
        return false;
    }

    private static boolean isCurrentReplayPlayerShaped(UIFilmController self)
    {
        try
        {
            Replay r = self.panel.replayEditor.replaysList.replays.getSelectedReplayFirst();
            if (r == null) return false;
            Form f = r.form.get();
            if (f instanceof ModelForm modelForm)
            {
                String path = modelForm.model.get();
                return path != null && path.toLowerCase().contains("player");
            }
        }
        catch (Throwable ignored)
        {
        }
        return false;
    }
}
