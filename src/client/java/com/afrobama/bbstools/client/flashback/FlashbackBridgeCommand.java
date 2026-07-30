// Coded by Afrobama/Claude — Chat-driven driver for FlashbackBridge.
//
//   /bbstools record-world start   — begin a Flashback recording
//   /bbstools record-world stop    — finalise the .zip
//   /bbstools record-world status  — show availability + recording state
//
// Why a thin command wrapper: same reasoning as the bridge itself — the
// shim is license-clean (no compile-time Flashback ref), so the command
// has to drive it via the public static helpers, not through any
// Flashback class. Keeps the licensing story watertight even if the
// command surface grows.
//
// Registered from BBSToolsModClient.onInitializeClient.
package com.afrobama.bbstools.client.flashback;

import com.afrobama.bbstools.BBSToolsMod;

import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

public final class FlashbackBridgeCommand
{
    private FlashbackBridgeCommand() {}

    public static void register()
    {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) ->
        {
            dispatcher.register(ClientCommands.literal("bbstools")
                .then(ClientCommands.literal("record-world")
                    .then(ClientCommands.literal("start")
                        .executes(ctx -> doStart(ctx.getSource())))
                    .then(ClientCommands.literal("stop")
                        .executes(ctx -> doStop(ctx.getSource())))
                    .then(ClientCommands.literal("status")
                        .executes(ctx -> doStatus(ctx.getSource())))));
        });

        BBSToolsMod.LOG.info("FlashbackBridgeCommand registered — /bbstools record-world start|stop|status");
    }

    private static int doStart(FabricClientCommandSource src)
    {
        if (!FlashbackBridge.isAvailable())
        {
            msg(src, "§c[bbstools] Flashback mod not installed — record-world unavailable.");
            msg(src, "§7Install Flashback (by Moulberry) alongside BBSTools to enable world-recording.");
            return 0;
        }

        boolean ok = FlashbackBridge.startRecording();
        if (ok)
        {
            msg(src, "§a[bbstools] Flashback recording started.");
            BBSToolsMod.LOG.info("/bbstools record-world start — Flashback recording begun");
            return 1;
        }
        msg(src, "§c[bbstools] Flashback failed to start recording — see log for details.");
        return 0;
    }

    private static int doStop(FabricClientCommandSource src)
    {
        if (!FlashbackBridge.isAvailable())
        {
            msg(src, "§c[bbstools] Flashback mod not installed — record-world unavailable.");
            return 0;
        }

        boolean ok = FlashbackBridge.stopRecording();
        if (ok)
        {
            msg(src, "§a[bbstools] Flashback recording stopped — .zip flushed.");
            BBSToolsMod.LOG.info("/bbstools record-world stop — Flashback recording finalised");
            return 1;
        }
        msg(src, "§c[bbstools] Flashback failed to stop recording — see log for details.");
        return 0;
    }

    private static int doStatus(FabricClientCommandSource src)
    {
        boolean avail = FlashbackBridge.isAvailable();
        msg(src, "§7[bbstools] Flashback bridge status:");
        msg(src, "§7  loaded:    " + (avail ? "§ayes" : "§cno"));
        if (avail)
        {
            boolean rec = FlashbackBridge.isRecording();
            msg(src, "§7  recording: " + (rec ? "§ayes" : "§7no"));
        }
        else
        {
            msg(src, "§7  Install Flashback (by Moulberry) to enable world recording.");
        }
        return 1;
    }

    private static void msg(FabricClientCommandSource src, String s)
    {
        src.sendFeedback(Component.literal(s));
    }

}
