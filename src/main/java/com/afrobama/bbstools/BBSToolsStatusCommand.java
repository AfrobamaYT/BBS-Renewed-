package com.afrobama.bbstools;

import com.afrobama.bbstools.perf.BBSPerfConfig;

import com.mojang.brigadier.CommandDispatcher;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

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

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher)
    {
        dispatcher.register(Commands.literal("bbstools")
            .executes(BBSToolsStatusCommand::status)
            .then(Commands.literal("status").executes(BBSToolsStatusCommand::status))
            .then(Commands.literal("perf").executes(BBSToolsStatusCommand::perf))
            .then(Commands.literal("config")
                .then(Commands.literal("save").executes(BBSToolsStatusCommand::configSave))
                .then(Commands.literal("reload").executes(BBSToolsStatusCommand::configReload)))
        );
    }

    private static int configSave(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx)
    {
        com.afrobama.bbstools.config.BBSToolsConfig.save();
        ctx.getSource().sendSuccess(() -> Component.literal("§aBBSTools config saved to config/bbstools/config.json"), false);
        return 1;
    }

    private static int configReload(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx)
    {
        com.afrobama.bbstools.config.BBSToolsConfig.loadOrCreate();
        ctx.getSource().sendSuccess(() -> Component.literal("§aBBSTools config reloaded — " + BBSPerfConfig.dump()), false);
        return 1;
    }

    private static int status(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx)
    {
        CommandSourceStack src = ctx.getSource();
        boolean physicsMod = FabricLoader.getInstance().isModLoaded("physicsmod");
        boolean bbs = FabricLoader.getInstance().isModLoaded("bbs");

        src.sendSuccess(() -> Component.literal("§eBBSTools-Fabric §7v0.1.0 — companion mod for BBS"), false);
        src.sendSuccess(() -> Component.literal("§7BBS detected:        " + (bbs ? "§ayes" : "§cno")), false);
        src.sendSuccess(() -> Component.literal("§7PhysicsMod compat:   " + (physicsMod ? "§aactive (anti-poof + bridge)" : "§7not installed")), false);
        src.sendSuccess(() -> Component.literal("§7Commands:           §f/ragdolly"), false);
        src.sendSuccess(() -> Component.literal("§7Keybinds:           §fShift+F9 §7editor · §f^ §7replay picker · §fCtrl+V §7paste-URL"), false);
        src.sendSuccess(() -> Component.literal("§7Run §f/bbstools perf §7for live counters."), false);
        return 1;
    }

    private static int perf(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx)
    {
        CommandSourceStack src = ctx.getSource();
        src.sendSuccess(() -> Component.literal("§eBBSTools perf config: §7" + BBSPerfConfig.dump()), false);
        src.sendSuccess(() -> Component.literal("§eBBSTools perf stats:  §7" + BBSPerfConfig.stats()), false);
        return 1;
    }

}
