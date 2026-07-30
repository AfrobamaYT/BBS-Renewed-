// Coded by Afrobama/Claude — /bbstools crowdmode toggle.
//
// Flips Replay.crowdMode on every replay in every active ActionPlayer
// film, then re-triggers updateReplayEntities so the spawn/un-spawn
// state matches the new flag.
//
// Usage:
//   /bbstools crowdmode all on   — set crowdMode=true on every replay
//   /bbstools crowdmode all off  — clear crowdMode on every replay
//   /bbstools crowdmode status   — print counts
//
// Registered via ClientCommandRegistrationCallback (NOT the server
// variant) because several other BBSTools modules already register
// `/bbstools` as a CLIENT-side literal root (BBSDebugEverything,
// BBSFxCommand, TimelineMarkerCommand, etc.). Server-side `/bbstools`
// subcommands get rejected by Fabric's client-side dispatcher as
// "Incorrect argument" because the client root knows `/bbstools` but
// not the server-only subcommand — and Fabric refuses to forward to
// the server after a client-side parse failure. By living in the
// client dispatcher we share the same `/bbstools` root the rest of
// the cinematic tools use.
//
// Singleplayer-only: BBSMod.getActions() returns the integrated
// server's ActionManager directly. Multiplayer dedicated-server films
// would need a network packet — defer until needed.
package com.afrobama.bbstools.client.cinematic;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;

import com.afrobama.bbstools.client.util.CrowdModeRegistry;
import com.afrobama.bbstools.mixin.ActionManagerAccessor;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.actions.ActionManager;
import mchorse.bbs_mod.actions.ActionPlayer;
import mchorse.bbs_mod.film.replays.Replay;

import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

public final class CrowdModeCommand
{
    private CrowdModeCommand() {}

    public static void register()
    {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) ->
        {
            dispatcher.register(ClientCommands.literal("bbstools")
                .then(ClientCommands.literal("crowdmode")
                    .then(ClientCommands.literal("all")
                        .then(ClientCommands.literal("on")
                            .executes(ctx -> setAll(ctx, true)))
                        .then(ClientCommands.literal("off")
                            .executes(ctx -> setAll(ctx, false))))
                    .then(ClientCommands.literal("status")
                        .executes(CrowdModeCommand::printStatus))));
        });
    }

    private static int setAll(CommandContext<FabricClientCommandSource> ctx, boolean on)
    {
        ActionManager actions = BBSMod.getActions();
        if (actions == null)
        {
            ctx.getSource().sendError(Component.literal("BBS ActionManager unavailable"));
            return 0;
        }

        int filmsTouched = 0;
        int replaysFlipped = 0;

        for (ActionPlayer player : ((ActionManagerAccessor) actions).bbstools$getPlayers())
        {
            filmsTouched++;
            for (Replay replay : player.film.replays.getList())
            {
                if (CrowdModeRegistry.isCrowdMode(replay) != on)
                {
                    CrowdModeRegistry.setCrowdMode(replay, on);
                    replaysFlipped++;
                }
            }
            /* Re-trigger the spawn/un-spawn pass so ActorEntities
             * appear/disappear immediately, no MC restart required. */
            player.updateReplayEntities();
        }

        final int finalFilms = filmsTouched;
        final int finalReplays = replaysFlipped;
        ctx.getSource().sendFeedback(Component.literal(
            String.format("crowdMode %s: %d film(s), %d replay(s) flipped",
                on ? "ON" : "OFF", finalFilms, finalReplays)));

        return Command.SINGLE_SUCCESS;
    }

    private static int printStatus(CommandContext<FabricClientCommandSource> ctx)
    {
        ActionManager actions = BBSMod.getActions();
        if (actions == null)
        {
            ctx.getSource().sendError(Component.literal("BBS ActionManager unavailable"));
            return 0;
        }

        int totalFilms = 0;
        int totalReplays = 0;
        int crowdReplays = 0;

        for (ActionPlayer player : ((ActionManagerAccessor) actions).bbstools$getPlayers())
        {
            totalFilms++;
            for (Replay replay : player.film.replays.getList())
            {
                totalReplays++;
                if (CrowdModeRegistry.isCrowdMode(replay))
                {
                    crowdReplays++;
                }
            }
        }

        final int f = totalFilms;
        final int r = totalReplays;
        final int c = crowdReplays;
        ctx.getSource().sendFeedback(Component.literal(
            String.format("crowdMode status: %d film(s), %d replay(s) total, %d in crowdMode",
                f, r, c)));

        return Command.SINGLE_SUCCESS;
    }

}
