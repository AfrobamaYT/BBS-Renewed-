// Coded by Afrobama/Claude
package com.afrobama.bbstools.client.tracker;

import com.afrobama.bbstools.BBSToolsMod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.clips.modifiers.TrackerClip;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.utils.clips.Clip;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * /bbstools fov save|load|list — named FOV presets for the BBS tracker
 * clip, persisted to {@code <run-dir>/bbs/fov-presets.json}.
 *
 * <p>Save: probes the currently active {@link TrackerClip} on the
 * playing film's camera timeline (if any) and grabs its {@code fov} value;
 * falls back to {@code MinecraftClient.options.fov().get()} when
 * no tracker is active. Load: writes the stored FOV back to the active
 * tracker clip (so the next frame's {@code applyClip} picks it up), with
 * the same MC-options fallback. List: dumps every saved preset to chat.</p>
 *
 * <p>The matched clip is the mchorse {@link TrackerClip} (parent of the
 * live-registered {@code TrackerClientClip}, factory {@code bbs:tracker}) —
 * NOT the dead {@code BBSToolsTrackerClip} duplicate this used to match
 * (which is in no factory, so the probe never found a clip). Task 7.3.</p>
 *
 * <p>Persistence: {@code List<Entry>} via Gson, pretty-printed JSON.
 * Atomic write through a {@code .tmp} sibling + {@code Files.move}, same
 * pattern as {@link com.afrobama.bbstools.client.skin.SkinFetchCommand}.</p>
 *
 * <p>Wired in from {@code BBSToolsModClient.onInitializeClient} via
 * {@link #register()}. Quick suggestions on the {@code <name>} arg pull
 * from the in-memory preset list so save-then-load tab-completes.</p>
 */
public final class TrackerFovPresets
{
    /** JSON record format. Public mutable so Gson can reflect on it. */
    public static final class Entry
    {
        public String name;
        public float fov;

        public Entry() {}

        public Entry(String name, float fov)
        {
            this.name = name;
            this.fov = fov;
        }
    }

    private static final String LOG_PREFIX = "[TrackerFovPresets] ";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<Entry>>() {}.getType();

    /** In-memory cache. Loaded once on first use, then kept in sync with
     *  disk on every save. Volatile so suggestions on the chat thread
     *  see the latest list. */
    private static volatile List<Entry> cache;

    private TrackerFovPresets() {}

    public static void register()
    {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> registerCommand(dispatcher));
    }

    private static void registerCommand(CommandDispatcher<FabricClientCommandSource> dispatcher)
    {
        SuggestionProvider<FabricClientCommandSource> nameSuggest = (ctx, builder) ->
        {
            List<Entry> list = loadAll();
            String remaining = builder.getRemainingLowerCase();
            for (Entry e : list)
            {
                if (e.name != null && e.name.toLowerCase().startsWith(remaining))
                {
                    builder.suggest(e.name);
                }
            }
            return builder.buildFuture();
        };

        dispatcher.register(ClientCommandManager.literal("bbstools")
            .then(ClientCommandManager.literal("fov")
                .then(ClientCommandManager.literal("save")
                    .then(ClientCommandManager.argument("name", StringArgumentType.word())
                        .executes(ctx ->
                        {
                            String name = StringArgumentType.getString(ctx, "name");
                            runSave(ctx.getSource(), name);
                            return 1;
                        })))
                .then(ClientCommandManager.literal("load")
                    .then(ClientCommandManager.argument("name", StringArgumentType.word())
                        .suggests(nameSuggest)
                        .executes(ctx ->
                        {
                            String name = StringArgumentType.getString(ctx, "name");
                            runLoad(ctx.getSource(), name);
                            return 1;
                        })))
                .then(ClientCommandManager.literal("list")
                    .executes(ctx ->
                    {
                        runList(ctx.getSource());
                        return 1;
                    }))));
    }

    /* ===========================================================
     * Command bodies
     * =========================================================== */

    private static void runSave(FabricClientCommandSource source, String name)
    {
        float fov = probeCurrentFov();
        try
        {
            List<Entry> list = loadAll();
            /* Replace existing entry with the same name (case-insensitive)
             *  so re-saving doesn't duplicate. */
            Iterator<Entry> it = list.iterator();
            while (it.hasNext())
            {
                Entry e = it.next();
                if (e.name != null && e.name.equalsIgnoreCase(name)) it.remove();
            }
            list.add(new Entry(name, fov));
            saveAll(list);

            source.sendFeedback(Component.literal(String.format(
                "[BBSTools] Saved FOV preset '%s' = %.2f", name, fov)));
            log("Saved preset " + name + " = " + fov);
        }
        catch (Exception e)
        {
            source.sendError(Component.literal("[BBSTools] Failed to save FOV preset: " + e.getMessage()));
            log("ERROR saving preset " + name + ": " + e);
            e.printStackTrace();
        }
    }

    private static void runLoad(FabricClientCommandSource source, String name)
    {
        try
        {
            List<Entry> list = loadAll();
            Entry hit = null;
            for (Entry e : list)
            {
                if (e.name != null && e.name.equalsIgnoreCase(name))
                {
                    hit = e;
                    break;
                }
            }
            if (hit == null)
            {
                source.sendError(Component.literal("[BBSTools] No FOV preset named '" + name + "'"));
                return;
            }

            boolean appliedToTracker = applyFovToActiveTracker(hit.fov);
            applyFovToMcOptions(hit.fov);

            source.sendFeedback(Component.literal(String.format(
                "[BBSTools] Loaded FOV preset '%s' = %.2f (%s)",
                hit.name, hit.fov,
                appliedToTracker ? "applied to active tracker" : "no active tracker, MC options only")));
            log("Loaded preset " + hit.name + " = " + hit.fov + " trackerApplied=" + appliedToTracker);
        }
        catch (Exception e)
        {
            source.sendError(Component.literal("[BBSTools] Failed to load FOV preset: " + e.getMessage()));
            log("ERROR loading preset " + name + ": " + e);
            e.printStackTrace();
        }
    }

    private static void runList(FabricClientCommandSource source)
    {
        try
        {
            List<Entry> list = loadAll();
            if (list.isEmpty())
            {
                source.sendFeedback(Component.literal("[BBSTools] No FOV presets saved yet. Use '/bbstools fov save <name>'."));
                return;
            }
            source.sendFeedback(Component.literal("[BBSTools] FOV presets (" + list.size() + "):"));
            for (Entry e : list)
            {
                source.sendFeedback(Component.literal(String.format("  - %s = %.2f", e.name, e.fov)));
            }
        }
        catch (Exception e)
        {
            source.sendError(Component.literal("[BBSTools] Failed to list FOV presets: " + e.getMessage()));
            log("ERROR list: " + e);
            e.printStackTrace();
        }
    }

    /* ===========================================================
     * FOV probe + apply (active tracker -> MC options fallback)
     * =========================================================== */

    /** Probes the currently-active tracker clip's FOV, falling back to
     *  the MC option when there's no tracker on the cursor. Always
     *  returns a sensible value — never throws. */
    private static float probeCurrentFov()
    {
        Float fromTracker = findActiveTrackerFov();
        if (fromTracker != null)
        {
            log("Probed FOV from active tracker clip: " + fromTracker);
            return fromTracker;
        }
        double mcFov = mcOptionsFov();
        log("Probed FOV from MinecraftClient.options: " + mcFov);
        return (float) mcFov;
    }

    /** Reads the FOV from the first active {@link TrackerClip}
     *  on the playing/editing film's camera timeline. Returns null when
     *  no such clip is active so the caller can fall back. */
    private static Float findActiveTrackerFov()
    {
        TrackerClip clip = findActiveTrackerClip();
        return clip == null ? null : clip.fov.get();
    }

    /** Writes {@code fov} to the active tracker clip's ValueFloat so the
     *  next call to {@code applyClip} picks it up. */
    private static boolean applyFovToActiveTracker(float fov)
    {
        TrackerClip clip = findActiveTrackerClip();
        if (clip == null) return false;
        try
        {
            clip.fov.set(fov);
            return true;
        }
        catch (Throwable t)
        {
            log("Failed to set tracker fov: " + t);
            return false;
        }
    }

    /** Best-effort mutation of the MC FOV option so the fallback path
     *  is visible immediately. Wrapped in try/catch since the option's
     *  set() throws on clamp violations. */
    private static void applyFovToMcOptions(float fov)
    {
        try
        {
            int clamped = Math.max(30, Math.min(110, Math.round(fov)));
            Minecraft.getInstance().options.fov().set(clamped);
        }
        catch (Throwable t)
        {
            log("Failed to set MC options fov (non-fatal): " + t);
        }
    }

    /** Walks every active film controller's current camera-clip list and
     *  returns the first {@link TrackerClip} hit (matches the live
     *  {@code TrackerClientClip} subclass too). Returns null when no film
     *  is playing, no tracker is on the cursor, or the reflection probe
     *  trips on a foreign clip type. */
    private static TrackerClip findActiveTrackerClip()
    {
        try
        {
            List<BaseFilmController> controllers = BBSModClient.getFilms().getControllers();
            if (controllers == null) return null;

            for (BaseFilmController controller : controllers)
            {
                int tick = Math.max(0, controller.getTick());
                Film film = null;
                try
                {
                    /* WorldFilmController exposes film as a public field. Reflective
                     *  read keeps us safe if the field name shifts between BBS-CML
                     *  and BBS Refreshed. */
                    java.lang.reflect.Field f;
                    try { f = controller.getClass().getField("film"); }
                    catch (NoSuchFieldException nf) { f = controller.getClass().getDeclaredField("film"); }
                    f.setAccessible(true);
                    Object fv = f.get(controller);
                    if (fv instanceof Film fl) film = fl;
                }
                catch (Throwable ignored) {}

                if (film == null || film.camera == null) continue;

                List<Clip> clipsNow = film.camera.getClips(tick);
                if (clipsNow == null || clipsNow.isEmpty()) continue;

                for (Clip c : clipsNow)
                {
                    if (c instanceof TrackerClip tc) return tc;
                }
            }
        }
        catch (Throwable t)
        {
            log("findActiveTrackerClip probe failed: " + t);
        }
        return null;
    }

    private static double mcOptionsFov()
    {
        try
        {
            return Minecraft.getInstance().options.fov().get();
        }
        catch (Throwable t)
        {
            return 70D;
        }
    }

    /* ===========================================================
     * Persistence
     * =========================================================== */

    private static Path presetsFile()
    {
        Path runDir = Minecraft.getInstance().gameDirectory.toPath();
        return runDir.resolve("bbs").resolve("fov-presets.json");
    }

    /** Loads from disk on first call, then returns the in-memory cache.
     *  Never throws — returns an empty mutable list on parse failure so
     *  callers can still append and save (overwriting the bad file). */
    public static synchronized List<Entry> loadAll()
    {
        if (cache != null) return new ArrayList<>(cache);

        Path file = presetsFile();
        List<Entry> list = new ArrayList<>();
        try
        {
            if (Files.exists(file))
            {
                String json = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                if (!json.isBlank())
                {
                    List<Entry> parsed = GSON.fromJson(json, LIST_TYPE);
                    if (parsed != null)
                    {
                        for (Entry e : parsed)
                        {
                            if (e != null && e.name != null) list.add(e);
                        }
                    }
                }
                log("Loaded " + list.size() + " preset(s) from " + file);
            }
        }
        catch (Exception e)
        {
            log("Failed to read preset file " + file + ": " + e + " — starting fresh in memory");
        }
        cache = list;
        return new ArrayList<>(list);
    }

    /** Persists the given list to disk (atomic via tmp + move) and
     *  refreshes the in-memory cache. */
    public static synchronized void saveAll(List<Entry> list) throws IOException
    {
        Path file = presetsFile();
        Files.createDirectories(file.getParent());
        String json = GSON.toJson(list, LIST_TYPE);
        Path tmp = file.resolveSibling(file.getFileName().toString() + ".tmp");
        Files.write(tmp, json.getBytes(StandardCharsets.UTF_8));
        try
        {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
        catch (Exception atomicFail)
        {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
        cache = new ArrayList<>(list);
        log("Persisted " + list.size() + " preset(s) to " + file);
    }

    private static void log(String msg)
    {
        try
        {
            BBSToolsMod.LOG.info(LOG_PREFIX + msg);
        }
        catch (Throwable ignore)
        {
            System.out.println(LOG_PREFIX + msg);
        }
    }

    /** Suppresses unused warning on CompletableFuture import (kept for
     *  future async file-write parity with SkinFetchCommand). */
    @SuppressWarnings("unused")
    private static CompletableFuture<?> unusedAsyncPlaceholder() { return null; }

}
