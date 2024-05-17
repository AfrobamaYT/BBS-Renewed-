package mchorse.bbs_mod.ui.film.menu;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.Recorder;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.ui.ContentType;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.utils.EventPropagation;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.List;

public class UIFilmsMenu extends UIBaseMenu
{
    public UIFilmsOverlayPanel films;

    private static void saveRecorder(UIContext context, Recorder recorder)
    {
        if (recorder.tick < 0)
        {
            return;
        }

        ContentType.FILMS.getRepository().load(recorder.filmId, (group) ->
        {
            if (group instanceof Film film)
            {
                List<Replay> list = film.replays.getList();

                if (CollectionUtils.inRange(list, recorder.replayId))
                {
                    ReplayKeyframes keyframes = list.get(recorder.replayId).keyframes;

                    keyframes.copy(recorder.keyframes);

                    ContentType.FILMS.getRepository().save(recorder.filmId, (MapType) film.toData());
                    context.notify(UIKeys.FILMS_SAVED_NOTIFICATION.format(film.getId()), Colors.BLUE | Colors.A100);
                }
            }
        });
    }

    public UIFilmsMenu()
    {
        super();

        Recorder recorder = BBSModClient.getFilms().stopRecording();

        if (recorder != null)
        {
            saveRecorder(this.context, recorder);
        }

        this.films = new UIFilmsOverlayPanel();
        this.films.onClose((event) -> this.closeThisMenu());

        UIOverlay overlay = UIOverlay.addOverlay(this.context, this.films);

        overlay.eventPropagataion(EventPropagation.PASS);
    }
}