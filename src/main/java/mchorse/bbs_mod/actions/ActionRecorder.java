package mchorse.bbs_mod.actions;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.actions.types.ActionClip;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.utils.clips.Clips;

public class ActionRecorder
{
    private Film film;
    private Clips clips = new Clips("...", BBSMod.getFactoryActionClips());
    private int tick;

    public ActionRecorder(Film film, int tick)
    {
        this.film = film;
        this.tick = tick;
    }

    public Film getFilm()
    {
        return this.film;
    }

    public Clips getClips()
    {
        return this.clips;
    }

    public void add(ActionClip clip)
    {
        if (this.tick < 0)
        {
            return;
        }

        clip.tick.set(this.tick);
        clip.duration.set(1);

        this.clips.addClip(clip);
    }

    public void tick()
    {
        this.tick += 1;
    }
}