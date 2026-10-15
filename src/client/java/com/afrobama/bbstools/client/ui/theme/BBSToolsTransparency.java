// Coded by Afrobama/Claude — UI Overhaul Phase 1: editor transparency state
package com.afrobama.bbstools.client.ui.theme;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.mojang.blaze3d.platform.InputConstants;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import net.minecraft.network.chat.Component;
import com.afrobama.bbstools.client.keybinds.BBSKeyCategories;

/**
 * Background-only transparency state for the BBSTools UI overhaul.
 *
 * <p>{@link #PANEL_BG_ALPHA} is multiplied into the alpha channel of
 * panel-background draws by {@code Batcher2DBackgroundAlphaMixin}.
 * Component, icons, borders, sliders, buttons are NOT affected — content
 * stays fully opaque so the editor stays readable at any setting.</p>
 *
 * <p>Keybind: Ctrl + Alt + T toggles between 100% (no transparency)
 * and the {@link #LAST_USER_VALUE} (default 50%). A slider in the
 * settings panel later will let the user fine-tune.</p>
 */
public final class BBSToolsTransparency
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/theme/transparency");

    /** Current transparency multiplier for panel backgrounds. 1.0 =
     *  fully opaque (no effect), 0.0 = fully transparent. */
    public static volatile float PANEL_BG_ALPHA = 1.0F;

    /** Last non-1.0 value the user set, so the toggle keybind has
     *  something to restore. */
    public static volatile float LAST_USER_VALUE = 0.5F;

    private static KeyMapping toggleKey;
    private static boolean lastKeyDown;

    private BBSToolsTransparency() {}

    /** Wire the toggle keybind via Fabric. Call from client init. */
    public static void register()
    {
        loadFromDisk();

        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.bbstools.ui_transparency_toggle",
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_T,
            BBSKeyCategories.BBSTOOLS
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client ->
        {
            if (client == null || client.getWindow() == null) return;
            long handle = client.getWindow().handle();
            boolean ctrl = InputConstants.isKeyDown(InputConstants.KEY_LCONTROL)
                        || InputConstants.isKeyDown(InputConstants.KEY_RCONTROL);
            boolean alt = InputConstants.isKeyDown(InputConstants.KEY_LALT)
                       || InputConstants.isKeyDown(InputConstants.KEY_RALT);
            boolean t = InputConstants.isKeyDown(InputConstants.KEY_T);
            boolean combo = ctrl && alt && t;
            if (combo && !lastKeyDown)
            {
                toggle();
                if (client.player != null)
                {
                    net.minecraft.client.Minecraft.getInstance().gui.hud.setOverlayMessage(Component.literal(
                        String.format("§a[BBSTools] §fEditor-Hintergrund-Transparenz: §b%d%%",
                            Math.round(PANEL_BG_ALPHA * 100F))), false);
                }
            }
            lastKeyDown = combo;
        });

        LOG.info("Transparency toggle (Ctrl+Alt+T) registered");
    }

    /** Cycle between 1.0 (off) and the last user value. */
    public static void toggle()
    {
        float before = PANEL_BG_ALPHA;
        if (PANEL_BG_ALPHA >= 0.999F)
        {
            PANEL_BG_ALPHA = LAST_USER_VALUE;
        }
        else
        {
            LAST_USER_VALUE = PANEL_BG_ALPHA;
            PANEL_BG_ALPHA = 1.0F;
        }
        float after = PANEL_BG_ALPHA;
        recordUndo(before, after, "Toggle transparency");
        LOG.info("PANEL_BG_ALPHA = {}", PANEL_BG_ALPHA);
    }

    /** Programmatic set (for slider). */
    public static void set(float v)
    {
        if (v < 0F) v = 0F;
        if (v > 1F) v = 1F;
        float before = PANEL_BG_ALPHA;
        PANEL_BG_ALPHA = v;
        if (v < 0.999F) LAST_USER_VALUE = v;
        recordUndo(before, v, "Set transparency");
    }

    /* UI Overhaul Phase 9b — demonstrate undo-stack integration. The
     * transparency setting is the lightest-weight user-visible state
     * we own, so it doubles as the canary integration for the new
     * undo overlay. Skip when the delta is meaningless (toggle that
     * landed on the same value, redundant sets) so the stack stays
     * useful. */
    private static void recordUndo(float before, float after, String label)
    {
        if (Math.abs(before - after) < 0.001F) return;
        com.afrobama.bbstools.client.ui.undo.BBSToolsUndoStack.push(
            new com.afrobama.bbstools.client.ui.undo.BBSToolsUndoEntry(
                String.format("%s (%d%% → %d%%)",
                    label,
                    Math.round(before * 100F),
                    Math.round(after * 100F)),
                () -> { PANEL_BG_ALPHA = before; if (before < 0.999F) LAST_USER_VALUE = before; saveToDisk(); },
                () -> { PANEL_BG_ALPHA = after;  if (after  < 0.999F) LAST_USER_VALUE = after;  saveToDisk(); }));
        saveToDisk();
    }

    /* === Persistence (Phase 9d) === */

    private static File getFile()
    {
        File configRoot = FabricLoader.getInstance().getConfigDir().toFile();
        File bbsDir = new File(configRoot, "bbstools");
        if (!bbsDir.exists()) bbsDir.mkdirs();
        return new File(bbsDir, "transparency.json");
    }

    private static void loadFromDisk()
    {
        File f = getFile();
        if (!f.exists()) return;
        try
        {
            String body = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim();
            float alpha = parseField(body, "alpha", 1F);
            float last  = parseField(body, "lastUserValue", 0.5F);
            if (alpha < 0F) alpha = 0F; else if (alpha > 1F) alpha = 1F;
            if (last  < 0F) last  = 0F; else if (last  > 1F) last  = 1F;
            PANEL_BG_ALPHA  = alpha;
            LAST_USER_VALUE = last;
            LOG.info("Loaded transparency state alpha={} lastUserValue={}", alpha, last);
        }
        catch (Exception ex)
        {
            LOG.warn("Failed to load transparency state from {} — keeping defaults", f, ex);
        }
    }

    private static void saveToDisk()
    {
        File f = getFile();
        try
        {
            String body = String.format(java.util.Locale.ROOT,
                "{\n  \"alpha\": %.4f,\n  \"lastUserValue\": %.4f\n}\n",
                PANEL_BG_ALPHA, LAST_USER_VALUE);
            Files.write(f.toPath(), body.getBytes(StandardCharsets.UTF_8));
        }
        catch (IOException ex)
        {
            LOG.warn("Failed to save transparency state to {}", f, ex);
        }
    }

    /** Bare-bones JSON-key lookup so we don't pull a parser dep for two
     *  fields. Returns {@code defaultValue} on any parse error.
     *  (C1 fix from the final review — original implementation had
     *  start &gt; j whenever the colon was followed by whitespace,
     *  which silently dropped every persisted value.) */
    private static float parseField(String body, String key, float defaultValue)
    {
        try
        {
            String needle = "\"" + key + "\"";
            int i = body.indexOf(needle);
            if (i < 0) return defaultValue;
            i = body.indexOf(':', i);
            if (i < 0) return defaultValue;
            int start = i + 1;
            while (start < body.length()
                && (body.charAt(start) == ' ' || body.charAt(start) == '\t'))
            {
                start++;
            }
            int j = start;
            /* allow a leading minus, then digits + at most one decimal point. */
            if (j < body.length() && body.charAt(j) == '-') j++;
            while (j < body.length()
                && (Character.isDigit(body.charAt(j)) || body.charAt(j) == '.'))
            {
                j++;
            }
            if (j <= start) return defaultValue;
            return Float.parseFloat(body.substring(start, j));
        }
        catch (Throwable t)
        {
            return defaultValue;
        }
    }

}
