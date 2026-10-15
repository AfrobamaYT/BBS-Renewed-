// Coded by Afrobama/Claude — /bbstools cinematic <on|off> one-shot perf
// profile toggle for the 1m pillager-manhunt Grox-style cinematic.
//
//   /bbstools cinematic on   — visibility maximized for recording
//                              (1024² draw, 512² shadow, LOD off)
//   /bbstools cinematic off  — perf-mode defaults restored
//                              (96² draw, 48² shadow, LOD on)
//
// Companion to CinematicDistanceCommand (per-knob live tuner). This is
// the "press once before recording, press once after" preset so the
// user doesn't have to remember four separate values mid-shot.
package com.afrobama.bbstools.client.perf;

import com.afrobama.bbstools.BBSToolsMod;
import com.afrobama.bbstools.perf.BBSPerfConfig;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

public final class CinematicProfileCommand
{
    private CinematicProfileCommand() {}

    public static void register()
    {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) ->
        {
            dispatcher.register(ClientCommandManager.literal("bbstools")
                .then(ClientCommandManager.literal("cinematic")
                    .then(ClientCommandManager.literal("on")
                        .executes(ctx -> doOn(ctx.getSource())))
                    .then(ClientCommandManager.literal("off")
                        .executes(ctx -> doOff(ctx.getSource())))));
        });
    }

    private static int doOn(FabricClientCommandSource src)
    {
        BBSPerfConfig.MAX_RENDER_DISTANCE_SQ     = 1024.0D * 1024.0D;
        BBSPerfConfig.SHADOW_CULLING_DISTANCE_SQ = 512.0D  * 512.0D;
        BBSPerfConfig.ENABLE_LOD                 = false;
        BBSPerfConfig.LOD_DETAIL_RADIUS_SQ       = 256.0D  * 256.0D;

        String line = "§a[bbstools] Cinematic mode ON — visibility maximized for recording";
        BBSToolsMod.LOG.info("Cinematic profile ON — {}", BBSPerfConfig.dump());
        msg(src, line);
        return 1;
    }

    private static int doOff(FabricClientCommandSource src)
    {
        BBSPerfConfig.MAX_RENDER_DISTANCE_SQ     = 96.0D * 96.0D;
        BBSPerfConfig.SHADOW_CULLING_DISTANCE_SQ = 48.0D * 48.0D;
        BBSPerfConfig.ENABLE_LOD                 = true;
        BBSPerfConfig.LOD_DETAIL_RADIUS_SQ       = 64.0D * 64.0D;

        String line = "§e[bbstools] Cinematic mode OFF — perf-mode defaults restored";
        BBSToolsMod.LOG.info("Cinematic profile OFF — {}", BBSPerfConfig.dump());
        msg(src, line);
        return 1;
    }

    private static void msg(FabricClientCommandSource src, String s)
    {
        src.sendFeedback(Component.literal(s));
    }

}
