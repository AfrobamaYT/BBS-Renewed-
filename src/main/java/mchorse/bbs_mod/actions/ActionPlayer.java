package mchorse.bbs_mod.actions;

import com.mojang.authlib.GameProfile;
import mchorse.bbs_mod.actions.types.ActionClip;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.utils.clips.Clip;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.server.world.ServerWorld;

import java.util.List;
import java.util.UUID;

public class ActionPlayer
{
    public Film film;
    public int tick;
    public boolean playing = true;

    private ServerWorld world;
    private int duration;

    public ActionPlayer(ServerWorld world, Film film, int tick)
    {
        this.world = world;
        this.film = film;
        this.tick = tick;

        this.duration = film.camera.calculateDuration();
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

            for (Replay replay : this.film.replays.getList())
            {
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