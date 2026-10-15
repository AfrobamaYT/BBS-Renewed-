// Coded by Afrobama/Claude — /bbstools accelrender toggle (Tier 1a).
//
// Flips BBSCrowdAcceleratedRenderer.ENABLED at runtime so the user can
// A/B-test AccelRender GPU compute path vs the existing CPU-bake +
// Sodium-push slow path without an MC restart.
//
// Requires:
//   - AcceleratedRendering mod installed (probed lazily at first frame)
//   - Iris shaderpack active (CrowdIrisWrappedRenderer's path)
//
// Usage:
//   /bbstools accelrender on      — enable AccelRender compute dispatch
//   /bbstools accelrender off     — fallback to iter 86 CPU-bake path
//   /bbstools accelrender status  — show ENABLED + probe state + mesh ready + counters
package com.afrobama.bbstools.client.cinematic;

import com.afrobama.bbstools.client.crowd.mass.BBSCrowdAcceleratedRenderer;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

public final class AccelRenderCommand
{
    private AccelRenderCommand() {}

    public static void register()
    {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) ->
        {
            dispatcher.register(ClientCommandManager.literal("bbstools")
                .then(ClientCommandManager.literal("accelrender")
                    .then(ClientCommandManager.literal("on")
                        .executes(ctx -> setFlag(ctx, true)))
                    .then(ClientCommandManager.literal("off")
                        .executes(ctx -> setFlag(ctx, false)))
                    .then(ClientCommandManager.literal("status")
                        .executes(AccelRenderCommand::printStatus))));
        });
    }

    private static int setFlag(CommandContext<FabricClientCommandSource> ctx, boolean on)
    {
        BBSCrowdAcceleratedRenderer.ENABLED = on;
        if (on)
        {
            BBSCrowdAcceleratedRenderer.probeOnce();
            BBSCrowdAcceleratedRenderer.resetFailures();
            ctx.getSource().sendFeedback(Component.literal(
                "WARNING: AccelRender is EXPERIMENTAL. Known to freeze PC " +
                "(hard-reset required) on camera-pan with >500 visible mobs. " +
                "Currently capped at " + com.afrobama.bbstools.perf.BBSPerfConfig.ACCEL_RENDER_MAX_VISIBLE +
                " visible. Save your work before testing."));
        }
        ctx.getSource().sendFeedback(Component.literal(
            String.format("AccelRender path %s — available=%s, mesh-ready=%s",
                on ? "ON" : "OFF",
                BBSCrowdAcceleratedRenderer.available(),
                BBSCrowdAcceleratedRenderer.meshReady)));
        return Command.SINGLE_SUCCESS;
    }

    private static int printStatus(CommandContext<FabricClientCommandSource> ctx)
    {
        ctx.getSource().sendFeedback(Component.literal(String.format(
            "AccelRender: ENABLED=%s available=%s mesh-ready=%s dispatched=%d build-attempts=%d",
            BBSCrowdAcceleratedRenderer.ENABLED,
            BBSCrowdAcceleratedRenderer.available(),
            BBSCrowdAcceleratedRenderer.meshReady,
            BBSCrowdAcceleratedRenderer.instancesDispatched,
            BBSCrowdAcceleratedRenderer.meshBuildAttempts)));
        return Command.SINGLE_SUCCESS;
    }

}
