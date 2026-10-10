package mchorse.bbs_mod.film.markers;

import mchorse.bbs_mod.camera.data.Point;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.forms.LabelForm;
import mchorse.bbs_mod.settings.values.base.BaseValue;

/** Native film transactions; no references to picker/library forms enter a cue. */
public final class TimerCueActions
{
    private TimerCueActions()
    {}

    public static TimerCue create(Film film, int tick, Point start, Point end)
    {
        if (film == null || tick < 0 || tick > Integer.MAX_VALUE - 40 || start == null || end == null
            || !Double.isFinite(start.x) || !Double.isFinite(start.y) || !Double.isFinite(start.z)
            || !Double.isFinite(end.x) || !Double.isFinite(end.y) || !Double.isFinite(end.z))
        {
            throw new IllegalArgumentException("No film or invalid cue start tick");
        }

        TimerCue[] created = new TimerCue[1];

        BaseValue.edit(film, (value) ->
        {
            TimerCue cue = value.timerCues.addCue();

            cue.label.set("Cue " + value.timerCues.getList().size());
            cue.startTick.set(tick);
            cue.endTick.set(tick + 40);
            cue.startReplay.set(endpoint(value, cue, false, start).uuid.get());
            cue.endReplay.set(endpoint(value, cue, true, end).uuid.get());
            created[0] = cue;
        });

        return created[0];
    }

    public static void rename(Film film, TimerCue cue, String name)
    {
        BaseValue.edit(film, (value) ->
        {
            cue.label.set(name);

            for (int index = 0; index < 2; index++)
            {
                boolean end = index == 1;
                Replay replay = endpoint(value, cue, end);

                if (replay != null && replay.form.get() instanceof LabelForm label)
                {
                    String text = name + (end ? " END" : " START");

                    replay.label.set(text);
                    label.text.set(text);
                }
            }
        });
    }

    private static Replay endpoint(Film film, TimerCue cue, boolean end, Point point)
    {
        LabelForm form = new LabelForm();
        Replay replay = film.replays.addReplay();
        String label = cue.label.get() + (end ? " END" : " START");
        int color = end ? cue.endColor.get() : cue.startColor.get();

        form.text.set(label);
        form.billboard.set(true);
        form.shaderShadow.set(false);
        form.transform.get().scale.set(0.5F);
        form.color.get().set(color, true);
        form.background.get().set(0xB0000000, true);
        replay.form.set(form);
        replay.label.set(label);
        replay.labelColor.set(color & 0xFFFFFF);
        replay.nurVorschau.set(true);
        replay.shadow.set(false);
        replay.category.set("Timer cues");
        replay.keyframes.x.insert(0, point.x);
        replay.keyframes.y.insert(0, point.y);
        replay.keyframes.z.insert(0, point.z);

        return replay;
    }

    public static Replay endpoint(Film film, TimerCue cue, boolean end)
    {
        String uuid = end ? cue.endReplay.get() : cue.startReplay.get();

        for (Replay replay : film.replays.getList())
        {
            if (uuid.equals(replay.uuid.get()))
            {
                return replay;
            }
        }

        return null;
    }

    public static String error(Film film, TimerCue cue)
    {
        if (!cue.timingError().isEmpty())
        {
            return cue.timingError();
        }

        for (int index = 0; index < 2; index++)
        {
            boolean end = index == 1;
            Replay replay = endpoint(film, cue, end);
            String label = end ? "END" : "START";

            if (replay == null)
            {
                return "Missing " + label + " replay";
            }
            if (!replay.enabled.get() || !(replay.form.get() instanceof LabelForm))
            {
                return label + " replay must be enabled and use a Label form";
            }
            if (replay.actor.get() || replay.fp.get() || !replay.nurVorschau.get())
            {
                return label + " replay must stay preview-only (not an actor or first-person replay)";
            }
        }

        return cue.startReplay.get().equals(cue.endReplay.get()) ? "START and END must be different replays" : "";
    }

    public static boolean isEndpoint(Film film, Replay replay)
    {
        for (TimerCue cue : film.timerCues.getList())
        {
            if (replay.uuid.get().equals(cue.startReplay.get()) || replay.uuid.get().equals(cue.endReplay.get()))
            {
                return true;
            }
        }

        return false;
    }

    public static void remove(Film film, TimerCue cue)
    {
        Replay start = endpoint(film, cue, false);
        Replay end = endpoint(film, cue, true);

        if ((start != null && !(start.form.get() instanceof LabelForm))
            || (end != null && !(end.form.get() instanceof LabelForm)))
        {
            throw new IllegalArgumentException("Timer endpoint was replaced by a different form; refusing to delete it");
        }

        BaseValue.edit(film, (value) ->
        {
            value.timerCues.remove(cue);
            if (start != null)
            {
                value.replays.remove(start);
            }
            if (end != null && end != start)
            {
                value.replays.remove(end);
            }
        });
    }
}
