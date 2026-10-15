// Coded by Afrobama/Claude — Phase 14: K-keybind for camera-mesh toggle
package com.afrobama.bbstools.client.campreview;

import com.afrobama.bbstools.client.util.CameraFrustumVisibility;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Polls the K key each client tick. When pressed (off→on edge), toggles
 * {@link CameraFrustumVisibility}. Mirrors the editor-cam handler's
 * polling pattern — no Minecraft keybinding object needed.
 *
 * <p>Fires only when no screen has focus (chat, dashboard, etc.) so we
 * don't steal K input from text fields.</p>
 */
public final class CameraMeshToggleHandler
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/cam-preview/mesh-toggle");

    private static boolean wasPressed;

    private CameraMeshToggleHandler() {}

    public static void register()
    {
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
        LOG.info("Camera-mesh visibility toggle registered (K)");
    }

    private static void tick()
    {
        try
        {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getWindow() == null) return;
            if (mc.gui.screen() != null) return;

            boolean down = InputConstants.isKeyDown(InputConstants.KEY_K);
            if (down && !wasPressed)
            {
                CameraFrustumVisibility.toggle();
                announce("§6[BBSTools] §fCamera meshes "
                    + (CameraFrustumVisibility.visible ? "§avisible" : "§7hidden"));
            }
            wasPressed = down;
        }
        catch (Throwable t)
        {
            LOG.debug("mesh-toggle tick failed: {}", t.toString());
        }
    }

    private static void announce(String msg)
    {
        try
        {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.player != null)
            {
                mc.player.sendSystemMessage(Component.literal(msg));
            }
        }
        catch (Throwable ignored) {}
    }

}
