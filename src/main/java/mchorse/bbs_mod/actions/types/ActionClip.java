package mchorse.bbs_mod.actions.types;

import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.utils.clips.Clip;
import net.fabricmc.fabric.api.entity.FakePlayer;

public abstract class ActionClip extends Clip
{
    public abstract void apply(FakePlayer player, Film film, Replay replay, int tick);
}