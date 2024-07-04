package mchorse.bbs_mod.actions.types;

import mchorse.bbs_mod.actions.SuperFakePlayer;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.settings.values.ValueString;
import mchorse.bbs_mod.utils.clips.Clip;

public class CommandActionClip extends ActionClip
{
    public final ValueString command = new ValueString("command", "");

    public CommandActionClip()
    {
        this.add(this.command);
    }

    @Override
    public void apply(SuperFakePlayer player, Film film, Replay replay, int tick)
    {
        this.applyPositionRotation(player, replay, tick);

        player.getServer().getCommandManager().executeWithPrefix(player.getCommandSource(), this.command.get());
    }

    @Override
    protected Clip create()
    {
        return new CommandActionClip();
    }
}