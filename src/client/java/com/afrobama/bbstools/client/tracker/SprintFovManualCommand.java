// Coded by Afrobama/Claude — manual sprint FOV override. Wired
// because the auto-detection from BBS replay sprint-keyframe state
// has been failing in user's BBS-CML setup across iters 14-25 — the
// recorded entity's isSprinting() returns false even when keyframe
// value=1. Manual toggle = guaranteed working FOV boost for cinematic
// recording without depending on the broken auto-pipeline.
package com.afrobama.bbstools.client.tracker;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.network.chat.Component;

public final class SprintFovManualCommand
{
    private SprintFovManualCommand() {}

    public static void register()
    {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) ->
            dispatcher.register(ClientCommandManager.literal("bbstools")
                .then(ClientCommandManager.literal("sprintfov")
                    .then(ClientCommandManager.literal("on").executes(c -> {
                        SprintFovState.MANUAL_SPRINT = true;
                        c.getSource().sendFeedback(Component.literal("§a[bbstools] sprint FOV manual = ON (1.15x boost)"));
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("off").executes(c -> {
                        SprintFovState.MANUAL_SPRINT = false;
                        c.getSource().sendFeedback(Component.literal("§e[bbstools] sprint FOV manual = OFF (back to entity-detected)"));
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("toggle").executes(c -> {
                        SprintFovState.MANUAL_SPRINT = !SprintFovState.MANUAL_SPRINT;
                        boolean on = SprintFovState.MANUAL_SPRINT;
                        c.getSource().sendFeedback(Component.literal((on ? "§a" : "§e") + "[bbstools] sprint FOV manual = " + (on ? "ON" : "OFF")));
                        return 1;
                    }))
                    .executes(c -> {
                        boolean on = SprintFovState.MANUAL_SPRINT;
                        c.getSource().sendFeedback(Component.literal("§7[bbstools] sprint FOV manual = " + (on ? "§aON" : "§eOFF") + "§7 (use 'on' / 'off' / 'toggle')"));
                        return 1;
                    })
                )
            ));
    }

}
