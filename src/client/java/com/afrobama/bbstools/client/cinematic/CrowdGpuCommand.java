// Coded by Afrobama/Claude — /bbstools crowdgpu toggle (Option B).
//
// Enables/disables the GPU-instanced crowd renderer powered by the
// BBSTools-iris-fork's BBSTOOLS_CROWD_ENTITIES ShaderKey. When ON,
// CrowdInstancedRenderer prefers CrowdIrisGpuInstancedRenderer over
// the CPU-bake CrowdIrisWrappedRenderer.
//
// Requirements (will silently fall through if missing):
//   - iris-fabric-1.8.8-bbstools+mc1.21.1.jar installed
//   - Shaderpack active (Complementary Unbound, BSL, etc.)
//   - Grox villager mesh loaded (BBS modul ready)
//
// Usage:
//   /bbstools crowdgpu on      — enable GPU-instanced path
//   /bbstools crowdgpu off     — fallback to CPU-bake CrowdIrisWrappedRenderer
//   /bbstools crowdgpu status  — show ENABLED + init state
package com.afrobama.bbstools.client.cinematic;

import com.afrobama.bbstools.client.crowd.mass.CrowdIrisGpuInstancedRenderer;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

public final class CrowdGpuCommand
{
    private CrowdGpuCommand() {}

    public static void register()
    {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) ->
        {
            dispatcher.register(ClientCommandManager.literal("bbstools")
                .then(ClientCommandManager.literal("crowdgpu")
                    .then(ClientCommandManager.literal("on")
                        .executes(ctx -> setFlag(ctx, true)))
                    .then(ClientCommandManager.literal("off")
                        .executes(ctx -> setFlag(ctx, false)))
                    .then(ClientCommandManager.literal("status")
                        .executes(CrowdGpuCommand::printStatus))));
        });
    }

    private static int setFlag(CommandContext<FabricClientCommandSource> ctx, boolean on)
    {
        /* HARD-BLOCKED 2026-05-29: the GPU-instanced path is UNFINISHED — it
         *  renders the crowd invisible (incomplete iris-fork crowd shader) and
         *  the user kept enabling it by accident, masking the working CPU-bake
         *  path. `on` is refused unless the hidden dev flag
         *  -Dbbstools.crowdgpu.dev=true is set. `off` always works. */
        if (on && !Boolean.parseBoolean(System.getProperty("bbstools.crowdgpu.dev", "false")))
        {
            CrowdIrisGpuInstancedRenderer.ENABLED = false;
            ctx.getSource().sendFeedback(Component.literal(
                "§cCrowdGPU is DISABLED — the GPU-instanced path is unfinished (renders invisible). "
                + "The working crowd renders via the CPU-bake / BOBJ-GL paths automatically. "
                + "Dev-only: launch with -Dbbstools.crowdgpu.dev=true to force-enable."));
            return Command.SINGLE_SUCCESS;
        }

        CrowdIrisGpuInstancedRenderer.ENABLED = on;
        ctx.getSource().sendFeedback(Component.literal(String.format(
            "CrowdGPU (Option B, instanced renderer, DEV) %s.",
            on ? "ON" : "OFF")));
        return Command.SINGLE_SUCCESS;
    }

    private static int printStatus(CommandContext<FabricClientCommandSource> ctx)
    {
        ctx.getSource().sendFeedback(Component.literal(String.format(
            "CrowdGPU: ENABLED=%s",
            CrowdIrisGpuInstancedRenderer.ENABLED)));
        return Command.SINGLE_SUCCESS;
    }

}
