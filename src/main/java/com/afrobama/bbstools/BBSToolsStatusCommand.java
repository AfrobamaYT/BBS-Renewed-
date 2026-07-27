package com.afrobama.bbstools;

import com.afrobama.bbstools.perf.BBSPerfConfig;

import com.mojang.brigadier.CommandDispatcher;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

/**
 * {@code /bbstools} status command. Reports the loaded BBSTools
 * version, which optional integrations are active (PhysicsMod), and
 * a snapshot of the live perf-counter state. Useful to verify the
 * mod is wired correctly without grepping the log.
 */
public final class BBSToolsStatusCommand
{
    private BBSToolsStatusCommand()
    {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher)
    {
        dispatcher.register(CommandManager.literal("bbstools")
            .executes(BBSToolsStatusCommand::status)
            .then(CommandManager.literal("status").executes(BBSToolsStatusCommand::status))
            .then(CommandManager.literal("perf").executes(BBSToolsStatusCommand::perf))
            .then(CommandManager.literal("config")
                .then(CommandManager.literal("save").executes(BBSToolsStatusCommand::configSave))
                .then(CommandManager.literal("reload").executes(BBSToolsStatusCommand::configReload)))
        );
    }

    private static int configSave(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx)
    {
        com.afrobama.bbstools.config.BBSToolsConfig.save();
        ctx.getSource().sendFeedback(() -> Text.literal("§aBBSTools config saved to config/bbstools/config.json"), false);
        return 1;
    }

    private static int configReload(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx)
    {
        com.afrobama.bbstools.config.BBSToolsConfig.loadOrCreate();
        ctx.getSource().sendFeedback(() -> Text.literal("§aBBSTools config reloaded — " + BBSPerfConfig.dump()), false);
        return 1;
    }

    private static int status(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx)
    {
        ServerCommandSource src = ctx.getSource();
        boolean physicsMod = FabricLoader.getInstance().isModLoaded("physicsmod");
        boolean bbs = FabricLoader.getInstance().isModLoaded("bbs");

        src.sendFeedback(() -> Text.literal("§eBBSTools-Fabric §7v0.1.0 — companion mod for BBS"), false);
        src.sendFeedback(() -> Text.literal("§7BBS detected:        " + (bbs ? "§ayes" : "§cno")), false);
        src.sendFeedback(() -> Text.literal("§7PhysicsMod compat:   " + (physicsMod ? "§aactive (anti-poof + bridge)" : "§7not installed")), false);
        src.sendFeedback(() -> Text.literal("§7Keybinds:           §fShift+F9 §7editor · §f^ §7replay picker · §fCtrl+V §7paste-URL"), false);
        src.sendFeedback(() -> Text.literal("§7Run §f/bbstools perf §7for live counters."), false);
        return 1;
    }

    private static int perf(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx)
    {
        ServerCommandSource src = ctx.getSource();
        src.sendFeedback(() -> Text.literal("§eBBSTools perf config: §7" + BBSPerfConfig.dump()), false);
        src.sendFeedback(() -> Text.literal("§eBBSTools perf stats:  §7" + BBSPerfConfig.stats()), false);
        return 1;
    }

}
