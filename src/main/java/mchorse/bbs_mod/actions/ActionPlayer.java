package mchorse.bbs_mod.actions;

import com.mojang.authlib.GameProfile;
import mchorse.bbs_mod.actions.types.ActionClip;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.utils.clips.Clip;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.server.world.ServerWorld;

import java.util.UUID;

public class ActionPlayer
{
    private static final GameProfile PROFILE = new GameProfile(UUID.fromString("12345678-9ABC-DEF1-2345-6789ABCDEF69"), "[BBS Player]");

    public Film film;
    public int tick;
    public boolean playing;

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
            FakePlayer fakePlayer = FakePlayer.get(this.world, PROFILE);

            for (Replay replay : this.film.replays.getList())
            {
                for (Clip clip : replay.actions.getClips(this.tick))
                {
                    ((ActionClip) clip).apply(fakePlayer, this.film, replay, this.tick);
                }
            }
        }

        this.tick += 1;

        return this.tick >= this.duration;
    }
}