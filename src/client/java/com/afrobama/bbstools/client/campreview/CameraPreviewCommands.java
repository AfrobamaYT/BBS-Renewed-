// Coded by Afrobama/Claude — chat commands for camera preview (BBSTools)
package com.afrobama.bbstools.client.campreview;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

/**
 * /bbstools campreview <add|clear|list|layout|track|untrack|res|offset|angle|remove>
 *
 * <p>Mirrors the {@code /bbs-renewed cam-preview} commands that lived in
 * BBS-Renewed source. Since BBS-Renewed is no longer the loaded jar, the
 * commands move under BBSTools' namespace. UI windows + render pipeline
 * are identical — only the command-front-end namespace changes.</p>
 *
 * @deprecated will be removed in Phase 11 — see new clip-driven design
 */
@Deprecated
public final class CameraPreviewCommands
{
    private CameraPreviewCommands() {}

    public static void register()
    {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) ->
        {
            dispatcher.register(ClientCommandManager.literal("bbstools")
                .then(ClientCommandManager.literal("campreview")
                    .then(ClientCommandManager.literal("add")
                        .executes(ctx -> { runAdd(ctx.getSource(), "Camera"); return 1; })
                        .then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                            .executes(ctx -> { runAdd(ctx.getSource(), StringArgumentType.getString(ctx, "name")); return 1; })))
                    .then(ClientCommandManager.literal("clear")
                        .executes(ctx -> { runClear(ctx.getSource()); return 1; }))
                    .then(ClientCommandManager.literal("list")
                        .executes(ctx -> { runList(ctx.getSource()); return 1; }))
                    .then(ClientCommandManager.literal("layout")
                        .executes(ctx -> { runLayout(ctx.getSource()); return 1; }))
                    .then(ClientCommandManager.literal("track")
                        .then(ClientCommandManager.argument("camName", StringArgumentType.string())
                            .then(ClientCommandManager.argument("replayIndex", IntegerArgumentType.integer(0))
                                .executes(ctx -> { runTrack(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "camName"),
                                    IntegerArgumentType.getInteger(ctx, "replayIndex")); return 1; }))))
                    .then(ClientCommandManager.literal("untrack")
                        .then(ClientCommandManager.argument("camName", StringArgumentType.string())
                            .executes(ctx -> { runTrack(ctx.getSource(),
                                StringArgumentType.getString(ctx, "camName"), -1); return 1; })))
                    .then(ClientCommandManager.literal("res")
                        .then(ClientCommandManager.argument("camName", StringArgumentType.string())
                            .executes(ctx -> { runRes(ctx.getSource(),
                                StringArgumentType.getString(ctx, "camName")); return 1; })))
                    .then(ClientCommandManager.literal("offset")
                        .then(ClientCommandManager.argument("camName", StringArgumentType.string())
                            .then(ClientCommandManager.argument("x", DoubleArgumentType.doubleArg())
                                .then(ClientCommandManager.argument("y", DoubleArgumentType.doubleArg())
                                    .then(ClientCommandManager.argument("z", DoubleArgumentType.doubleArg())
                                        .executes(ctx -> { runOffset(ctx.getSource(),
                                            StringArgumentType.getString(ctx, "camName"),
                                            DoubleArgumentType.getDouble(ctx, "x"),
                                            DoubleArgumentType.getDouble(ctx, "y"),
                                            DoubleArgumentType.getDouble(ctx, "z")); return 1; }))))))
                    .then(ClientCommandManager.literal("angle")
                        .then(ClientCommandManager.argument("camName", StringArgumentType.string())
                            .then(ClientCommandManager.argument("yaw", DoubleArgumentType.doubleArg())
                                .then(ClientCommandManager.argument("pitch", DoubleArgumentType.doubleArg())
                                    .executes(ctx -> { runAngle(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "camName"),
                                        DoubleArgumentType.getDouble(ctx, "yaw"),
                                        DoubleArgumentType.getDouble(ctx, "pitch")); return 1; })))))
                    .then(ClientCommandManager.literal("remove")
                        .then(ClientCommandManager.argument("camName", StringArgumentType.greedyString())
                            .executes(ctx -> { runRemove(ctx.getSource(),
                                StringArgumentType.getString(ctx, "camName")); return 1; })))));
        });
    }

    private static CameraPreview findByName(String name)
    {
        for (CameraPreview p : CameraPreviewRegistry.list())
        {
            if (p.name.equalsIgnoreCase(name)) return p;
        }
        return null;
    }

    private static void runAdd(FabricClientCommandSource src, String name)
    {
        CameraPreview p = CameraPreviewRegistry.addAtPlayer(name);
        src.sendFeedback(Component.literal(String.format(
            "§a[BBSTools] §fCamera preview '%s' added at (%.1f, %.1f, %.1f). Open dashboard.",
            p.name, p.posX, p.posY, p.posZ)));
    }

    private static void runClear(FabricClientCommandSource src)
    {
        int n = CameraPreviewRegistry.list().size();
        CameraPreviewRegistry.clear();
        src.sendFeedback(Component.literal("§a[BBSTools] §fCleared " + n + " camera preview(s)."));
    }

    private static void runList(FabricClientCommandSource src)
    {
        java.util.List<CameraPreview> list = CameraPreviewRegistry.list();
        src.sendFeedback(Component.literal("§a[BBSTools] §f" + list.size() + " camera preview(s):"));
        for (CameraPreview p : list)
        {
            src.sendFeedback(Component.literal(String.format(
                "§7  - %s mode=%s pos=(%.1f, %.1f, %.1f) window=(%d,%d %dx%d) fb=%dx%d",
                p.name, p.mode, p.posX, p.posY, p.posZ, p.windowX, p.windowY, p.windowW, p.windowH,
                p.fbWidth, p.fbHeight)));
        }
    }

    private static void runLayout(FabricClientCommandSource src)
    {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return;
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        CameraPreviewRegistry.autoLayout(w, h);
        src.sendFeedback(Component.literal("§a[BBSTools] §fAuto-layout applied ("
            + CameraPreviewRegistry.list().size() + " windows)"));
    }

    private static void runTrack(FabricClientCommandSource src, String camName, int replayIndex)
    {
        CameraPreview p = findByName(camName);
        if (p == null)
        {
            src.sendFeedback(Component.literal("§c[BBSTools] No preview named '" + camName + "'"));
            return;
        }

        if (replayIndex < 0)
        {
            CameraPreviewRegistry.setTracker(p, null);
            src.sendFeedback(Component.literal("§a[BBSTools] §f'" + camName + "' untracked → manual mode"));
            return;
        }

        mchorse.bbs_mod.film.Film film = null;
        try
        {
            java.util.List<mchorse.bbs_mod.film.BaseFilmController> ctls =
                mchorse.bbs_mod.BBSModClient.getFilms().getControllers();
            if (ctls != null && !ctls.isEmpty()) film = ctls.get(ctls.size() - 1).film;
        }
        catch (Throwable ignored) {}

        if (film == null || film.replays == null || replayIndex >= film.replays.getList().size())
        {
            src.sendFeedback(Component.literal("§c[BBSTools] No film loaded or replay index out of range"));
            return;
        }

        mchorse.bbs_mod.film.replays.Replay r = film.replays.getList().get(replayIndex);
        CameraPreviewRegistry.setTracker(p, r);
        src.sendFeedback(Component.literal("§a[BBSTools] §f'" + camName + "' now tracking replay #" + replayIndex
            + " '" + (r.getName() == null ? "(unnamed)" : r.getName()) + "'"));
    }

    private static void runRes(FabricClientCommandSource src, String camName)
    {
        CameraPreview p = findByName(camName);
        if (p == null)
        {
            src.sendFeedback(Component.literal("§c[BBSTools] No preview named '" + camName + "'"));
            return;
        }
        CameraPreviewRegistry.cycleResolution(p);
        src.sendFeedback(Component.literal("§a[BBSTools] §f'" + camName + "' resolution → "
            + p.fbWidth + "x" + p.fbHeight));
    }

    private static void runOffset(FabricClientCommandSource src, String camName, double x, double y, double z)
    {
        CameraPreview p = findByName(camName);
        if (p == null)
        {
            src.sendFeedback(Component.literal("§c[BBSTools] No preview named '" + camName + "'"));
            return;
        }
        if (p.mode == CameraPreview.Mode.TRACKER)
        {
            p.trackOffsetX = (float) x;
            p.trackOffsetY = (float) y;
            p.trackOffsetZ = (float) z;
            src.sendFeedback(Component.literal(String.format(
                "§a[BBSTools] §f'%s' tracker offset → (%.2f, %.2f, %.2f)", camName, x, y, z)));
        }
        else
        {
            p.posX += x; p.posY += y; p.posZ += z;
            src.sendFeedback(Component.literal(String.format(
                "§a[BBSTools] §f'%s' moved by (%.2f, %.2f, %.2f) → (%.1f, %.1f, %.1f)",
                camName, x, y, z, p.posX, p.posY, p.posZ)));
        }
    }

    private static void runAngle(FabricClientCommandSource src, String camName, double yaw, double pitch)
    {
        CameraPreview p = findByName(camName);
        if (p == null)
        {
            src.sendFeedback(Component.literal("§c[BBSTools] No preview named '" + camName + "'"));
            return;
        }
        if (p.mode == CameraPreview.Mode.TRACKER)
        {
            p.trackOffsetYaw = (float) yaw;
            p.trackOffsetPitch = (float) pitch;
            src.sendFeedback(Component.literal(String.format(
                "§a[BBSTools] §f'%s' tracker angle offset → yaw=%.1f pitch=%.1f", camName, yaw, pitch)));
        }
        else
        {
            p.yaw = (float) yaw;
            p.pitch = (float) pitch;
            src.sendFeedback(Component.literal(String.format(
                "§a[BBSTools] §f'%s' angle set → yaw=%.1f pitch=%.1f", camName, yaw, pitch)));
        }
    }

    private static void runRemove(FabricClientCommandSource src, String camName)
    {
        CameraPreview p = findByName(camName);
        if (p == null)
        {
            src.sendFeedback(Component.literal("§c[BBSTools] No preview named '" + camName + "'"));
            return;
        }
        CameraPreviewRegistry.remove(p.id);
        src.sendFeedback(Component.literal("§a[BBSTools] §fRemoved preview '" + camName + "'"));
    }

}
