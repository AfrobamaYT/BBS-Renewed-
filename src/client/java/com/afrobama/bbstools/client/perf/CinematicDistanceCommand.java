// Coded by Afrobama/Claude — /bbstools cinematic-distance live-tuner.
// User flagged BBS replays disappearing >96 blocks during the 1m
// pillager-manhunt Grox-style cinematic; static default got bumped
// to 512² (BBSPerfConfig.MAX_RENDER_DISTANCE_SQ) but cinematic shots
// often need framing from much farther (or no cull at all). This
// command sets that knob live so the user can dial it in per-shot
// without relaunching MC.
//
//   /bbstools cinematic-distance <blocks>    — set max draw distance (1-2048 b)
//   /bbstools cinematic-distance off         — disable distance culling entirely
//   /bbstools cinematic-distance on          — re-enable distance culling
//   /bbstools cinematic-distance status      — print current settings
//
// Values are squared internally (MAX_RENDER_DISTANCE_SQ) so we square
// here at command-time, never on the render thread.
package com.afrobama.bbstools.client.perf;

import com.afrobama.bbstools.perf.BBSPerfConfig;

import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

public final class CinematicDistanceCommand
{
    private CinematicDistanceCommand() {}

    public static void register()
    {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) ->
        {
            dispatcher.register(ClientCommandManager.literal("bbstools")
                .then(ClientCommandManager.literal("cinematic-distance")
                    .executes(ctx -> doStatus(ctx.getSource()))
                    .then(ClientCommandManager.literal("status")
                        .executes(ctx -> doStatus(ctx.getSource())))
                    .then(ClientCommandManager.literal("off")
                        .executes(ctx -> doToggle(ctx.getSource(), false)))
                    .then(ClientCommandManager.literal("on")
                        .executes(ctx -> doToggle(ctx.getSource(), true)))
                    .then(ClientCommandManager.argument("blocks", IntegerArgumentType.integer(1, 2048))
                        .executes(ctx -> doSet(ctx.getSource(),
                            IntegerArgumentType.getInteger(ctx, "blocks"))))));
        });
    }

    private static int doSet(FabricClientCommandSource src, int blocks)
    {
        double sq = (double) blocks * (double) blocks;
        BBSPerfConfig.MAX_RENDER_DISTANCE_SQ = sq;
        /* Setting an explicit distance implies the user wants culling
         *  active — otherwise the value would be meaningless. Flip it
         *  back on if it was off so the change actually takes effect. */
        boolean wasOff = !BBSPerfConfig.ENABLE_DISTANCE_CULLING;
        BBSPerfConfig.ENABLE_DISTANCE_CULLING = true;
        if (wasOff)
        {
            msg(src, "§a[cinematic-distance] §fmax draw = §a" + blocks
                + "§f blocks §7(squared = " + (long) sq + ") §e— culling re-enabled");
        }
        else
        {
            msg(src, "§a[cinematic-distance] §fmax draw = §a" + blocks
                + "§f blocks §7(squared = " + (long) sq + ")");
        }
        return 1;
    }

    private static int doToggle(FabricClientCommandSource src, boolean on)
    {
        BBSPerfConfig.ENABLE_DISTANCE_CULLING = on;
        if (on)
        {
            int blocks = (int) Math.round(Math.sqrt(BBSPerfConfig.MAX_RENDER_DISTANCE_SQ));
            msg(src, "§a[cinematic-distance] §fculling §aON §7(max draw = §f"
                + blocks + "§7 blocks)");
        }
        else
        {
            msg(src, "§c[cinematic-distance] §fculling §cOFF §7— replays render at any distance "
                + "§8(watch FPS on big scenes)");
        }
        return 1;
    }

    private static int doStatus(FabricClientCommandSource src)
    {
        boolean on = BBSPerfConfig.ENABLE_DISTANCE_CULLING;
        int blocks = (int) Math.round(Math.sqrt(BBSPerfConfig.MAX_RENDER_DISTANCE_SQ));
        long sq = (long) BBSPerfConfig.MAX_RENDER_DISTANCE_SQ;
        String state = on ? "§aON" : "§cOFF";
        msg(src, "§e[cinematic-distance] §fculling=" + state
            + "§f  max=§a" + blocks + "§f blocks §7(squared=" + sq + ")");
        msg(src, "§7  /bbstools cinematic-distance <1-2048> §8| §7off §8| §7on §8| §7status");
        return 1;
    }

    private static void msg(FabricClientCommandSource src, String s)
    {
        src.sendFeedback(Component.literal(s));
    }

}
