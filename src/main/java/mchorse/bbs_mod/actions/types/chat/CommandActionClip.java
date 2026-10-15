package mchorse.bbs_mod.actions.types.chat;

import mchorse.bbs_mod.actions.SuperFakePlayer;
import mchorse.bbs_mod.actions.types.ActionClip;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.utils.clips.Clip;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerLevel;
import mchorse.bbs_mod.utils.CommandSourceUtils;

public class CommandActionClip extends ActionClip
{
    public final ValueString command = new ValueString("command", "");

    public CommandActionClip()
    {
        this.add(this.command);
    }

    @Override
    public void applyAction(LivingEntity actor, SuperFakePlayer player, Film film, Replay replay, int tick)
    {
        this.applyPositionRotation(player, replay, tick);

        String command = this.command.get();
        /* 26.2: Entity.createCommandSourceStack() heisst jetzt
         * createCommandSourceStackForNameResolution(ServerLevel) und braucht die welt als
         * argument. Trotz des namens ist es derselbe aufbau wie vorher, inklusive
         * rechtestufe 0 (int.NO_PERMISSIONS) -- am bytecode geprueft.
         * ServerPlayer behaelt die alte, parameterlose methode. */
        CommandSourceStack source = actor == null
            ? player.createCommandSourceStack()
            : CommandSourceUtils.of(actor, (ServerLevel) actor.level());

        player.level().getServer().getCommands().performPrefixedCommand(source, command);
    }

    @Override
    protected Clip create()
    {
        return new CommandActionClip();
    }
}