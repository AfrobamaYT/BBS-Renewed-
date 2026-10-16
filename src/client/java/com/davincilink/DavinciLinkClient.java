// Coded by Afrobama/Claude — BBS <-> DaVinci Resolve live link (task 79, docs/DAVINCI-LINK-PLAN.md).
package com.davincilink;

import com.blenderbridge.BridgeServer;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;

/**
 * Starts the DaVinci link next to the Blender bridge: the same wire format on its own loopback
 * port, drained on the main thread every client tick.
 *
 * <p>PORT: the Blender bridge port + 100 (CF instance 9980, test instance n 9980 + n), so every
 * instance that already has its own bridge port gets its own link port without new settings.
 * {@code -Ddavincilink.port} overrides it.</p>
 */
public final class DavinciLinkClient implements ClientModInitializer
{
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final BridgeServer SERVER = new BridgeServer(
        Integer.getInteger("davincilink.port", BridgeServer.PORT + 100), "davincilink");

    @Override
    public void onInitializeClient()
    {
        SERVER.start();
        DavinciMedia.register();
        DavinciScriptInstall.install(SERVER.getPort());
        ClientTickEvents.END_CLIENT_TICK.register((client) ->
        {
            DavinciFilmSync.tick(SERVER);
            DavinciTab.tick();
        });
        Runtime.getRuntime().addShutdownHook(new Thread(() ->
        {
            try { DavinciScreen.INSTANCE.shutdown(); }
            finally { SERVER.stop(); }
        }, "davincilink-shutdown"));
        LOGGER.info("[davincilink] client init on port {}", SERVER.getPort());
    }
}
