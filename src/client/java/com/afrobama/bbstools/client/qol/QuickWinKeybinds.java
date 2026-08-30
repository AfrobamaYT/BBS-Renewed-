package com.afrobama.bbstools.client.qol;

import com.afrobama.bbstools.BBSToolsMod;
import com.afrobama.bbstools.client.shader.ShaderHotReload;
import com.afrobama.bbstools.perf.BBSPerfConfig;
import com.mojang.blaze3d.platform.InputConstants;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.afrobama.bbstools.client.keybinds.BBSKeyCategories;

/**
 * Three one-press quick-win hotkeys: cinematic toggle, marker pin, and
 * Veil-crowd A/B toggle.
 *
 * <p>Each binding is a Fabric {@link KeyMapping} registered under the
 * "category.bbstools" category so users see them in vanilla MC's
 * Controls screen alongside the other BBSTools shortcuts. The polling
 * loop drains {@code wasPressed()} once per tick — drain-style so that
 * accidental double-fires (multiple presses on the same tick) still
 * register as one action per tick.</p>
 */
public final class QuickWinKeybinds
{
    /** ⛔ Dev-Werkzeug registriert sich nur im Entwicklungsbaum — im Release faellt es weg,
     * wie das Sandbox-Panel im Dashboard. Reine Diagnose ohne Nutzen fuer Spieler, und F11
     * kollidiert mit Vanillas Vollbild-Taste. Nicht registrieren statt spaeter verstecken:
     * drain() vertraegt null, die Handler laufen nie an. Bewusst nicht ausgebaut — jede
     * Zeile Unterschied zum Release-Zweig muesste bei jedem Merge von Hand gepflegt werden. */
    public static final boolean DEV_ENV = net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment();

    private static final Logger LOG = LoggerFactory.getLogger("bbstools/qol/quickwins");

    /** F4 — toggle cinematic profile (calls into the same knobs as
     *  /bbstools cinematic on|off). */
    private static KeyMapping cinematicToggle;

    /** F5 — pin a marker at the current scrub cursor. */


    private static KeyMapping veilCrowdToggle;

    /** F7 — Tier 4c: trigger Iris shaderpack hot-reload. Chosen over F5
     *  (already marker-pin) and F6 (vanilla "Perspective" / often grabbed
     *  by debug huds); F7 is reliably free in vanilla + most popular
     *  modpacks. */
    private static KeyMapping shaderReload;

    /** F11 — in-game GPU debugger capture (Phase 1). Fires BBSGpuDebug.captureNow
     *  which dumps current program / VAO / VBO / FBO / pipeline state to log. */
    private static KeyMapping gpuDebugCapture;

    /** F10 — Master debugger Module 5: thread dump on demand (deadlock detect + all stacks). */
    private static KeyMapping threadDump;

    /** F9 — Master debugger Module 6: one-shot bug-report bundle (zips log+crash+modlist+screenshot). */
    private static KeyMapping bugBundle;

    /* F12 (debug-overlay) liegt seit 2026-07-28 in
     * com.afrobama.bbstools.client.keybinds.DebugOverlayKeybind. Grund: hier
     * haengt jede taste an KeyBinding.wasPressed(), und das feuert nicht,
     * solange ein screen offen ist — der overlay-schalter wird aber genau
     * dann gebraucht. Nicht zurueckholen. */

    /** Auto-incrementing label suffix so successive F5 presses don't
     *  collide on the registry's label key. */

    private QuickWinKeybinds() {}

    public static void register()
    {
        cinematicToggle = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.bbstools.cinematic_toggle",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F4,
            BBSKeyCategories.BBSTOOLS
        ));

        /* Coded by Afrobama/Claude — UNBOUND default (was F2 which collides
         * with MC's vanilla screenshot key → 10s freeze on press). User can
         * rebind in MC Controls if desired. */
        veilCrowdToggle = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.bbstools.veil_crowd_toggle",
            InputConstants.Type.KEYSYM,
            -1, /* GLFW_KEY_UNKNOWN — unbound default */
            BBSKeyCategories.BBSTOOLS
        ));

        /* F8 — BBS-core + instancing GLSL live-reload + Iris pack reload. (F7 collides with the
         * record-from-cursor hotkey, which swallowed the press → shader reload never fired.) */
        shaderReload = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.bbstools.shader_reload",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F8,
            BBSKeyCategories.BBSTOOLS
        ));

        gpuDebugCapture = !DEV_ENV ? null : KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.bbstools.gpu_debug_capture",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F11,
            BBSKeyCategories.BBSTOOLS
        ));

        threadDump = !DEV_ENV ? null : KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.bbstools.thread_dump",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F10,
            BBSKeyCategories.BBSTOOLS
        ));

        /* ⛔ NICHT F9 — und auch NICHT F2 (beides gemessen 2026-07-26).
         *
         * F9 schluckte den keybind-editor: Minecraft-keybindings laufen VOR dem
         * BBS-UI-system und `drain()` verbraucht das event mit `wasPressed()`. Damit kam
         * unser Shift+F9 (BBSToolsKeys.KEYBIND_EDITOR_OPEN) nie an — MC-keybindings
         * ignorieren modifier, also traf es auch die shift-variante.
         *
         * Erster ausweichversuch war F2 — falsch: das ist Minecrafts SCREENSHOT-taste.
         * Ich hatte nur `GLFW_KEY_F*` im quellcode gegrept und die vanilla-belegung
         * nicht geprüft. Live an der JVM nachgemessen ergibt:
         *   belegt: F2(screenshot) F4 F5 F8 F10 F11 F12
         *   frei:   F1 F3 F6 F7 F9  (F1/F3/F6/F7 nutzt BBS nur im UI, nicht als
         *                            MC-keybinding — dort kollidiert es also nicht)
         *
         * F6 ist auf MC-ebene frei; F9 bleibt damit für den keybind-editor. */
        bugBundle = !DEV_ENV ? null : KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.bbstools.bug_bundle",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F6,
            BBSKeyCategories.BBSTOOLS
        ));


        ClientTickEvents.END_CLIENT_TICK.register(client -> tick(client));

        LOG.info("Quick-win keybinds registered: F4 cinematic toggle, "
            + "F5 marker-at-cursor, F2 Veil-crowd toggle, "
            + "F7 Iris shader hot-reload.");
    }

    private static void tick(Minecraft client)
    {
        if (client == null) return;

        /* Drain wasPressed() so a single tick handles at most one
         *  action per binding, regardless of how many times the key
         *  was tapped this frame. */
        boolean fireCinematic    = drain(cinematicToggle);
        boolean fireVeil         = drain(veilCrowdToggle);
        boolean fireShaderReload = drain(shaderReload);
        boolean fireGpuDebug     = drain(gpuDebugCapture);

        if (fireCinematic)    handleCinematic(client);
        if (fireVeil)         handleVeilCrowd(client);
        if (fireShaderReload) handleShaderReload(client);
        if (fireGpuDebug)
        {
            /* Ctrl+F11 = GPU dev-loop layer 3: hard-reset poisoned GL state (no restart).
             * Plain F11 = capture snapshot. */
            boolean ctrl = GLFW.glfwGetKey(client.getWindow().handle(), GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(client.getWindow().handle(), GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;

            if (ctrl)
            {
                com.afrobama.bbstools.client.debug.BBSGpuDebug.resetGlState();
                if (client.player != null)
                    client.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a[gpu-debug] GL state reset (VAO/program/buffers unbound)"));
            }
            else
            {
                com.afrobama.bbstools.client.debug.BBSGpuDebug.enable(false);
                com.afrobama.bbstools.client.debug.BBSGpuDebug.captureNow("F11");
                if (client.player != null)
                    client.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§b[gpu-debug] capture fired — check bbstools-console.log"));
            }
        }

        boolean fireThreadDump = drain(threadDump);
        if (fireThreadDump)
        {
            com.afrobama.bbstools.client.debug.master.jvm.ThreadDumper.dumpAll();
            if (client.player != null)
                client.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§b[thread-dump] all threads dumped to log"));
        }

        boolean fireBugBundle = drain(bugBundle);
        if (fireBugBundle)
        {
            try
            {
                java.nio.file.Path zip = com.afrobama.bbstools.client.debug.master.bundle.BugReportBundle.create("F9");
                if (client.player != null)
                    client.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a[bug-bundle] saved: " + zip.getFileName()));
            }
            catch (Throwable t)
            {
                LOG.error("Bug bundle creation threw", t);
                if (client.player != null)
                    client.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c[bug-bundle] failed: " + t.getMessage()));
            }
        }
    }

    private static boolean drain(KeyMapping kb)
    {
        boolean any = false;
        if (kb == null) return false;
        while (kb.consumeClick()) any = true;
        return any;
    }

    /* === Handlers === */

    /** F4: flip between "cinematic on" and "cinematic off" presets, matching
     *  CinematicProfileCommand exactly. We treat ENABLE_LOD == false as the
     *  "cinematic ON" state (LOD disabled when recording) and ENABLE_LOD ==
     *  true as the "cinematic OFF" state (LOD on for perf-mode). */
    private static void handleCinematic(Minecraft client)
    {
        boolean wasCinematicOn = !BBSPerfConfig.ENABLE_LOD;

        if (wasCinematicOn)
        {
            /* Cinematic ON -> OFF: restore perf-mode defaults (mirror
             *  of CinematicProfileCommand.doOff). */
            BBSPerfConfig.MAX_RENDER_DISTANCE_SQ     = 96.0D * 96.0D;
            BBSPerfConfig.SHADOW_CULLING_DISTANCE_SQ = 48.0D * 48.0D;
            BBSPerfConfig.ENABLE_LOD                 = true;
            BBSPerfConfig.LOD_DETAIL_RADIUS_SQ       = 64.0D * 64.0D;

            actionBar(client, "§e[bbstools] Cinematic mode OFF — perf defaults restored");
            BBSToolsMod.LOG.info("F4 → cinematic OFF — {}", BBSPerfConfig.dump());
        }
        else
        {
            /* Cinematic OFF -> ON: maximize visibility for recording
             *  (mirror of CinematicProfileCommand.doOn). */
            BBSPerfConfig.MAX_RENDER_DISTANCE_SQ     = 1024.0D * 1024.0D;
            BBSPerfConfig.SHADOW_CULLING_DISTANCE_SQ = 512.0D  * 512.0D;
            BBSPerfConfig.ENABLE_LOD                 = false;
            BBSPerfConfig.LOD_DETAIL_RADIUS_SQ       = 256.0D  * 256.0D;

            actionBar(client, "§a[bbstools] Cinematic mode ON — visibility maximized");
            BBSToolsMod.LOG.info("F4 → cinematic ON — {}", BBSPerfConfig.dump());
        }
    }



    private static void handleVeilCrowd(Minecraft client)
    {

        actionBar(client, "§b[bbstools] §fVeil-crowd renderer: §cnicht verfuegbar (Veil fehlt fuer 26.2)");
        BBSToolsMod.LOG.info("F2 → Veil-crowd angefragt, aber Veil ist in P3 stillgelegt");
    }


    private static void handleShaderReload(Minecraft client)
    {
        actionBar(client, "§a[BBSTools] Reloading shaders (Iris pack + BBS core/instancing)…");
        BBSToolsMod.LOG.info("F7 → ShaderHotReload.reload() + BBSShaders.setup() requested");

        /* 1) BBS-core + instancing GLSL recompile. With -Dbbstools.shaderdev=true the BBS shader
         *  factories read .vsh/.fsh from the SOURCE tree, so editing a shader file + pressing F7
         *  recompiles it live — no jar redeploy, no MC restart. The GPU dev-loop's GLSL layer.
         *  Reflection because BBSTools (companion) does not compile-depend on the BBS jar. */
        boolean bbsOk = reloadBbsCoreShaders();

        /* 2) Iris shaderpack reload (unchanged). */
        boolean irisOk = false;
        try
        {
            irisOk = ShaderHotReload.INSTANCE.reload();
            if (!irisOk)
            {
                BBSToolsMod.LOG.warn("F7 → ShaderHotReload.reload() returned false "
                    + "(Iris missing or reload threw — check bbstools/shader-reload log)");
            }
        }
        catch (Throwable t)
        {
            BBSToolsMod.LOG.warn("F7 Iris shader reload failed: {}", t.toString(), t);
        }

        if (bbsOk && irisOk)      actionBar(client, "§a[BBSTools] Shaders reloaded (BBS core + Iris pack)");
        else if (bbsOk)           actionBar(client, "§a[BBSTools] BBS core/instancing shaders reloaded (Iris skipped)");
        else if (irisOk)          actionBar(client, "§a[BBSTools] Iris pack reloaded (BBS core reload failed — see log)");
        else                      actionBar(client, "§c[BBSTools] Shader reload failed — see log");
    }

    /**
     * Recompile the BBS-core + instancing GLSL programs by invoking {@code BBSShaders.setup()} in the
     * BBS jar via reflection (closes + rebuilds every core program and re-runs the LOADERS list, which
     * includes the model/block/VAT instancing shaders). Returns false if the BBS class is absent or the
     * call throws. Safe to call every F7 press; production-safe (only recompiles, no behavior change).
     */
    private static boolean reloadBbsCoreShaders()
    {
        try
        {
            Class<?> bbsShaders = Class.forName("mchorse.bbs_mod.client.BBSShaders");
            bbsShaders.getMethod("setup").invoke(null);
            BBSToolsMod.LOG.info("F7 → BBSShaders.setup() done (core + instancing GLSL recompiled)");
            return true;
        }
        catch (ClassNotFoundException e)
        {
            BBSToolsMod.LOG.warn("F7 → BBSShaders not found (BBS jar missing?) — core reload skipped");
            return false;
        }
        catch (Throwable t)
        {
            BBSToolsMod.LOG.warn("F7 → BBSShaders.setup() threw: {}", t.toString(), t);
            return false;
        }
    }

    /* === Helpers === */

    private static UIFilmPanel getFilmPanel()
    {
        try
        {
            UIDashboard dashboard = BBSModClient.getDashboard();
            if (dashboard == null) return null;
            return dashboard.getPanel(UIFilmPanel.class);
        }
        catch (Throwable t)
        {
            LOG.debug("getFilmPanel failed: {}", t.toString());
            return null;
        }
    }

    /** Send a hotbar action-bar message (over-the-hotbar HUD line). Falls
     *  back silently when the player isn't loaded yet (early-boot
     *  scenario where the keybind somehow fired before world join). */
    private static void actionBar(Minecraft client, String msg)
    {
        try
        {
            if (client != null && client.player != null)
            {
                client.player.sendOverlayMessage(Component.literal(msg));
            }
        }
        catch (Throwable ignored) {}
    }

}
