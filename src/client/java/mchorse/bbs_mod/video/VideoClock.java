package mchorse.bbs_mod.video;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;

import java.util.Map;

/**
 * Film-Zeit fuer eine gezeichnete Entitaet: der Replay-Tick des Film-Controllers, dem sie gehoert
 * (Editor-Cursor, Welt-Wiedergabe, Export). Ohne Film (Modellblock, UI) zaehlt das Alter der
 * Entitaet -- das ist keine Film-Zeit, aber laeuft weiter wie ein Video ohne Zeitleiste.
 * UI-Vorschauen ohne Entitaet zeigen das erste Bild.
 */
public final class VideoClock
{
    private VideoClock()
    {}

    public static double ticks(IEntity entity, float transition)
    {
        if (entity == null)
        {
            /* UI-Vorschau (Replay-Liste, HUD, Formliste): Standbild statt Dauerdekodieren --
             * jede laufende Vorschau kostete einen eigenen ffmpeg-Prozess. */
            return 0D;
        }

        /* Nie getDashboard(): das baut nach dem Verlassen der Welt ein neues Dashboard ohne Kamera-Entitaet (NPE). */
        UIDashboard dashboard = BBSModClient.getDashboardIfCreated();
        UIFilmPanel panel = dashboard == null ? null : dashboard.getPanel(UIFilmPanel.class);

        if (panel != null && panel.getController() != null)
        {
            Double tick = tick(panel.getController().editorController, entity, panel.isRunning() ? transition : 0F);

            if (tick != null)
            {
                return tick;
            }
        }

        for (BaseFilmController controller : BBSModClient.getFilms().getControllers())
        {
            Double tick = tick(controller, entity, transition);

            if (tick != null)
            {
                return tick;
            }
        }

        return entity.getAge() + transition;
    }

    private static Double tick(BaseFilmController controller, IEntity entity, float transition)
    {
        if (controller == null)
        {
            return null;
        }

        for (Map.Entry<Integer, IEntity> entry : controller.entities.entrySet())
        {
            if (entry.getValue() == entity && entry.getKey() < controller.film.replays.getList().size())
            {
                Replay replay = controller.film.replays.getList().get(entry.getKey());

                return replay.getTick(controller.getTick()) + (double) transition;
            }
        }

        return null;
    }
}
