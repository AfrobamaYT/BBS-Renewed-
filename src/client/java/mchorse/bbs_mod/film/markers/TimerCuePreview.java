package mchorse.bbs_mod.film.markers;

import io.netty.util.collection.IntObjectMap;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.Recorder;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.LabelForm;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;

import java.util.List;

/** Updates only controller-owned forms. Existing LabelFormRenderer draws/picks the endpoints. */
public final class TimerCuePreview
{
    private TimerCuePreview()
    {}

    public static void apply(Film film, IntObjectMap<IEntity> entities, int tick, int countdown)
    {
        if (film == null || film.timerCues.getList().isEmpty())
        {
            return;
        }

        List<Replay> replays = film.replays.getList();

        for (TimerCue cue : film.timerCues.getList())
        {
            String error = TimerCueActions.error(film, cue);

            endpoint(cue, false, replays, entities, tick, countdown, error);
            endpoint(cue, true, replays, entities, tick, countdown, error);
        }
    }

    /** Broken bindings must also be visible while the editor is closed during recording. */
    public static void renderErrors(Batcher2D batcher, Recorder recorder)
    {
        if (recorder == null || mchorse.bbs_mod.film.VideoExportSession.laeuftExport())
        {
            return;
        }

        int y = 26;

        for (TimerCue cue : recorder.film.timerCues.getList())
        {
            String error = cue.enabled.get() ? TimerCueActions.error(recorder.film, cue) : "";

            if (!error.isEmpty())
            {
                batcher.textCard("Timer " + cue.label.get() + ": " + error, 5, y, 0xFFFF5555, 0xCC000000);
                y += 14;
            }
        }
    }

    private static void endpoint(TimerCue cue, boolean end, List<Replay> replays,
        IntObjectMap<IEntity> entities, int tick, int countdown, String error)
    {
        String uuid = end ? cue.endReplay.get() : cue.startReplay.get();

        for (int index = 0; index < replays.size(); index++)
        {
            Replay replay = replays.get(index);

            if (!uuid.equals(replay.uuid.get()))
            {
                continue;
            }

            IEntity entity = entities.get(index);

            if (entity == null || !(entity.getForm() instanceof LabelForm label))
            {
                return;
            }
            if (label == replay.form.get() || label.getRoot() instanceof Film)
            {
                throw new IllegalStateException("Timer preview must not mutate an authored/shared form");
            }

            String text = !error.isEmpty() ? "ERROR: " + error : countdown > 0
                ? cue.label.get() + " | Recording in " + TimerCue.seconds(countdown)
                : cue.countdown(tick, end);
            int color = end ? cue.endColor.get() : cue.startColor.get();

            if (!text.equals(label.text.get()))
            {
                label.text.set(text);
            }
            Color runtimeColor = label.color.get();

            runtimeColor.set(error.isEmpty() ? color : 0xFFFF5555, true);
            runtimeColor.a = cue.enabled.get() ? 1F : 0F;
            label.background.get().a = cue.enabled.get() ? 0.69F : 0F;

            return;
        }
    }
}
