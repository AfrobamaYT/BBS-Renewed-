package mchorse.bbs_mod.actions;

import mchorse.bbs_mod.actions.types.ActionClip;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.utils.clips.Clip;
import net.minecraft.server.world.ServerWorld;

import java.util.List;

public class ActionPlayer
{
    public Film film;
    public int tick;
    public boolean playing = true;
    public int exception;

    private ServerWorld world;
    private int duration;

    public ActionPlayer(ServerWorld world, Film film, int tick, int exception)
    {
        this.world = world;
        this.film = film;
        this.tick = tick;
        this.exception = exception;

        this.duration = film.camera.calculateDuration();
    }

    public ServerWorld getWorld()
    {
        return this.world;
    }

    public boolean tick()
    {
        if (!this.playing)
        {
            return false;
        }

        if (this.tick >= 0)
        {
            SuperFakePlayer fakePlayer = SuperFakePlayer.get(this.world);
            List<Replay> list = this.film.replays.getList();

            for (int i = 0; i < list.size(); i++)
            {
                if (i == this.exception)
                {
                    continue;
                }

                Replay replay = list.get(i);
                List<Clip> clips = replay.actions.getClips(this.tick);

                for (Clip clip : clips)
                {
                    ((ActionClip) clip).apply(fakePlayer, this.film, replay, this.tick);
                }
            }
        }

        this.tick += 1;

        return this.tick >= this.duration;
    }
}