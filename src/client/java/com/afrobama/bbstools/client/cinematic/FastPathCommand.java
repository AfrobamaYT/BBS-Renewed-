// Coded by Afrobama/Claude — /bbstools fastpath toggle.
//
// Flips BBSPerfConfig.CROWD_IRIS_FAST_PATH at runtime so the user
// can A/B-test fast-but-transparent vs slow-but-visible villager
// rendering without an MC restart.
//
// Usage:
//   /bbstools fastpath on      — enable 54-byte Iris-extended push (~30-40 FPS, transparent)
//   /bbstools fastpath off     — fallback VC chain (~7 FPS, visible)
//   /bbstools fastpath status  — show current state
//
// Registered client-side (matches the /bbstools root that
// BBSDebugEverything / TimelineMarkerCommand / etc. use).
package com.afrobama.bbstools.client.cinematic;

import com.afrobama.bbstools.perf.BBSPerfConfig;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

public final class FastPathCommand
{
    private FastPathCommand() {}

    public static void register()
    {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) ->
        {
            dispatcher.register(ClientCommandManager.literal("bbstools")
                .then(ClientCommandManager.literal("fastpath")
                    .then(ClientCommandManager.literal("on")
                        .executes(ctx -> setFlag(ctx, true)))
                    .then(ClientCommandManager.literal("off")
                        .executes(ctx -> setFlag(ctx, false)))
                    .then(ClientCommandManager.literal("status")
                        .executes(FastPathCommand::printStatus))));
        });
    }

    private static int setFlag(CommandContext<FabricClientCommandSource> ctx, boolean on)
    {
        BBSPerfConfig.CROWD_IRIS_FAST_PATH = on;
        ctx.getSource().sendFeedback(Component.literal(
            String.format("CrowdIris fast-path %s — %s",
                on ? "ON" : "OFF",
                on ? "30-40 FPS, villagers TRANSPARENT (known bug)"
                   : "7 FPS, villagers VISIBLE (safe fallback)")));
        return Command.SINGLE_SUCCESS;
    }

    private static int printStatus(CommandContext<FabricClientCommandSource> ctx)
    {
        ctx.getSource().sendFeedback(Component.literal(
            "CrowdIris fast-path: " + (BBSPerfConfig.CROWD_IRIS_FAST_PATH ? "ON" : "OFF")));
        return Command.SINGLE_SUCCESS;
    }

}
