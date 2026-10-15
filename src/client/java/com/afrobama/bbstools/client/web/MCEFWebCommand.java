// Coded by Afrobama/Claude
package com.afrobama.bbstools.client.web;

import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * {@code /bbstools web [url]} — opens an MCEF (Chromium) browser screen.
 *
 * With no argument it extracts the bundled Launchpad mockup and loads it as a
 * file:// page (so the design shows 1:1). With an argument it loads that URL,
 * handy for quick checks ({@code /bbstools web https://example.com}).
 *
 * Gated on the optional MCEF mod: the {@link MCEFWebScreen} class (which imports
 * MCEF) is only referenced after the isModLoaded check, so BBSTools never touches
 * MCEF classes when the mod is absent.
 */
public final class MCEFWebCommand
{
    private MCEFWebCommand() {}

    /* URL queued by the command, opened on the next client tick — after the chat screen has
       closed — so the browser screen isn't immediately replaced (the command runs inside the
       chat screen, whose close would otherwise null our screen before it ever renders). */
    private static volatile String pending;

    public static void register()
    {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) ->
        {
            dispatcher.register(ClientCommands.literal("bbstools")
                .then(ClientCommands.literal("web")
                    .executes(ctx -> open(ctx.getSource(), null))
                    .then(ClientCommands.argument("url", StringArgumentType.greedyString())
                        .executes(ctx -> open(ctx.getSource(), StringArgumentType.getString(ctx, "url"))))));
        });

        ClientTickEvents.END_CLIENT_TICK.register(client ->
        {
            String url = pending;

            if (url != null)
            {
                pending = null;

                /* Chat has closed by now, so currentScreen is the BBS dashboard underneath (if any);
                   capture it so bridge actions like "open film" can return to it. */
                BBSWebBridge.bbsParent = client.gui.screen();

                LoggerFactory.getLogger("bbstools-web").info("[web] tick-open MCEFWebScreen({}) parent={}", url, client.gui.screen());
                client.gui.setScreen(new MCEFWebScreen(url));
            }
        });
    }

    private static int open(FabricClientCommandSource src, String url)
    {
        if (!FabricLoader.getInstance().isModLoaded("mcef"))
        {
            src.sendFeedback(Component.literal("§c[web] MCEF not installed. Put mcef-fabric-2.1.6-1.21.1.jar in mods/ (it downloads Chromium on first launch), then restart."));

            return 0;
        }

        String target = url != null ? url : extractBundledLaunchpad(src);

        if (target == null)
        {
            return 0;
        }

        pending = target;

        src.sendFeedback(Component.literal("§a[web] opening " + target + "  §7(Esc to close)"));

        return 1;
    }

    private static String extractBundledLaunchpad(FabricClientCommandSource src)
    {
        try
        {
            Path out = FabricLoader.getInstance().getGameDir().resolve("bbstools-web").resolve("launchpad.html");

            Files.createDirectories(out.getParent());

            try (InputStream in = MCEFWebCommand.class.getResourceAsStream("/assets/bbstools/web/launchpad.html"))
            {
                if (in == null)
                {
                    src.sendFeedback(Component.literal("§c[web] bundled launchpad.html not found in the jar"));

                    return null;
                }

                Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
            }

            return out.toUri().toString();
        }
        catch (Exception e)
        {
            src.sendFeedback(Component.literal("§c[web] " + e.getMessage()));

            return null;
        }
    }

}
