/*
 * Coded by Afrobama/Claude.
 */
package com.afrobama.bbstools.client.timeline;

import com.afrobama.bbstools.BBSToolsMod;
import com.mojang.brigadier.CommandDispatcher;

import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /bbstools ripple-delete} — NLE-style ripple delete on the BBS
 * timeline.
 *
 * <p>Behaviour: take the clip currently selected inside the BBS film
 * editor, delete it, then for every clip on every track of the film
 * (camera, screen and per-replay action tracks) that <em>starts at or
 * after</em> the deleted clip's start tick, subtract the deleted
 * clip's duration from its {@code tick} so the gap closes.</p>
 *
 * <p>The implementation uses reflection end-to-end so the companion
 * mod compiles cleanly even when the BBS classes
 * (which can rename between BBS-CML and BBS-Renewed) aren't on the
 * client classpath. We resolve:</p>
 *
 * <ul>
 *   <li>{@code mchorse.bbs_mod.BBSModClient#getDashboard()} → the
 *       in-game dashboard singleton.</li>
 *   <li>{@code UIDashboard#getPanel(Class)} → the open {@code
 *       UIFilmPanel} (or null if no film open).</li>
 *   <li>{@code UIFilmPanel.cameraEditor / actionEditor / screenEditor}
 *       — three {@code UIClipsPanel} fields. We probe each, find the
 *       one whose {@code getClip()} returns non-null, and that's our
 *       selection.</li>
 *   <li>{@code UIDataDashboardPanel#getData()} → the active {@code
 *       Film}.</li>
 *   <li>{@code Film.camera / Film.screen} (public {@code Clips}) and
 *       {@code Film.replays.getList()} → each {@code Replay} has its
 *       own {@code actions} {@code Clips}. We shift across all of
 *       these so e.g. action clips on actor 2 don't dangle past their
 *       camera shot when a clip in front of them is rippled out.</li>
 *   <li>{@code Clips.get()} → unmodifiable list of {@code Clip}, then
 *       per-clip we read/write {@code clip.tick} (a {@code ValueInt})
 *       via its {@code get()/set(int)} methods.</li>
 *   <li>{@code Clips.remove(Clip)} → drops the selected clip.</li>
 *   <li>{@code UIDataDashboardPanel#save()} → persists the film so
 *       the change survives reload (would otherwise revert).</li>
 * </ul>
 *
 * <p>Failure mode: every step that touches BBS reflection is wrapped
 * so a missing method or renamed field surfaces as a chat error
 * instead of crashing the client. Log-first per project policy — the
 * full stack trace also goes to {@code bbstools-console.log} so a
 * later relaunch isn't needed to see what broke.</p>
 *
 * <p>Wiring: registered from {@code BBSToolsModClient.onInitializeClient()}.</p>
 */
public final class RippleDeleteCommand
{
    private static final String BBS_MOD_CLIENT = "mchorse.bbs_mod.BBSModClient";
    private static final String UI_FILM_PANEL = "mchorse.bbs_mod.ui.film.UIFilmPanel";

    private RippleDeleteCommand()
    {
    }

    public static void register()
    {
        ClientCommandRegistrationCallback.EVENT.register(
            (dispatcher, registry) -> registerCommand(dispatcher));
        BBSToolsMod.LOG.info("RippleDeleteCommand: /bbstools ripple-delete registered");
    }

    private static void registerCommand(CommandDispatcher<FabricClientCommandSource> dispatcher)
    {
        dispatcher.register(ClientCommands.literal("bbstools")
            .then(ClientCommands.literal("ripple-delete")
                .executes(ctx ->
                {
                    run(ctx.getSource());
                    return 1;
                })));
    }

    /**
     * Entry point — finds the selection, performs the ripple, saves.
     * All BBS interaction is reflection-gated.
     */
    private static void run(FabricClientCommandSource source)
    {
        try
        {
            /* 1. Resolve the open UIFilmPanel via the dashboard. */
            Object filmPanel = findOpenFilmPanel();

            if (filmPanel == null)
            {
                reply(source, "Ripple delete: no film editor is open.");
                return;
            }

            /* 2. Find which editor (camera/action/screen) has a clip
             *    selected. First non-null wins. */
            Object[] selection = findSelectedClip(filmPanel);

            if (selection == null)
            {
                reply(source, "Ripple delete: no clip is selected. Click a clip in the timeline first.");
                return;
            }

            Object selectedClip = selection[0];
            String selectedFromEditor = (String) selection[1];

            int deletedTick = readIntValue(selectedClip, "tick");
            int deletedDuration = readIntValue(selectedClip, "duration");

            if (deletedDuration <= 0)
            {
                reply(source, "Ripple delete: selected clip has non-positive duration; aborting.");
                return;
            }

            /* 3. Reach the underlying Film so we can iterate every
             *    track (camera + screen + per-replay actions). */
            Object film = invoke(filmPanel, "getData");

            if (film == null)
            {
                reply(source, "Ripple delete: dashboard has no active film.");
                return;
            }

            int shiftedClips = 0;

            /* 4. Camera + screen — both are public Clips fields on Film. */
            shiftedClips += rippleClipsContainer(getField(film, "camera"), selectedClip, deletedTick, deletedDuration);
            shiftedClips += rippleClipsContainer(getField(film, "screen"), selectedClip, deletedTick, deletedDuration);

            /* 5. Per-replay action tracks. Replays.getList() is the
             *    canonical list accessor (mirrors UIReplaysEditor). */
            Object replays = getField(film, "replays");
            if (replays != null)
            {
                Object replayList = invoke(replays, "getList");
                if (replayList instanceof List<?> list)
                {
                    for (Object replay : list)
                    {
                        Object actions = getField(replay, "actions");
                        shiftedClips += rippleClipsContainer(actions, selectedClip, deletedTick, deletedDuration);
                    }
                }
            }

            /* 6. Delete the selected clip from its own container. We
             *    don't know which container owned it without a search,
             *    so we ask every one of them. The one that contains it
             *    will drop it; the others are no-ops. */
            removeFromAllContainers(film, selectedClip);

            /* 7. Persist. UIDataDashboardPanel.save() writes back to
             *    disk; without this the change is lost on reload. */
            invokeIfPresent(filmPanel, "save");

            reply(source, "Ripple delete: removed clip from %s (tick=%d, dur=%d), shifted %d trailing clips left.".formatted(
                selectedFromEditor, deletedTick, deletedDuration, shiftedClips));
        }
        catch (Throwable t)
        {
            BBSToolsMod.LOG.error("Ripple delete failed", t);
            reply(source, "Ripple delete failed: " + t.getClass().getSimpleName() + " — see bbstools-console.log");
        }
    }

    /* === BBS reflection helpers ============================== */

    /**
     * @return the open UIFilmPanel instance, or null if no film is
     *         currently the focused dashboard panel.
     */
    private static Object findOpenFilmPanel() throws Exception
    {
        Class<?> clientCls = Class.forName(BBS_MOD_CLIENT);
        Method getDashboard = clientCls.getMethod("getDashboard");
        Object dashboard = getDashboard.invoke(null);

        if (dashboard == null)
        {
            return null;
        }

        Class<?> filmPanelCls = Class.forName(UI_FILM_PANEL);
        Method getPanel = dashboard.getClass().getMethod("getPanel", Class.class);

        return getPanel.invoke(dashboard, filmPanelCls);
    }

    /**
     * Probes the three UIClipsPanel fields on UIFilmPanel and returns
     * the first one whose {@code getClip()} is non-null.
     *
     * @return {@code {clip, editorName}} or null if nothing selected.
     */
    private static Object[] findSelectedClip(Object filmPanel) throws Exception
    {
        String[] editorFields = { "cameraEditor", "actionEditor", "screenEditor" };

        for (String fieldName : editorFields)
        {
            Object editor = getField(filmPanel, fieldName);
            if (editor == null)
            {
                continue;
            }

            Object clip = invoke(editor, "getClip");
            if (clip != null)
            {
                return new Object[] { clip, fieldName };
            }
        }

        return null;
    }

    /**
     * Walks every clip in {@code container} (a BBS {@code Clips}) and
     * subtracts {@code deletedDuration} from any clip whose {@code
     * tick.get() >= deletedTick} and which is not the deleted clip
     * itself.
     *
     * <p>Why "{@code >=}" rather than "{@code >}": the deleted clip
     * sits at exactly {@code deletedTick}, but other tracks may also
     * have clips starting at the same tick (a sync point). Those
     * should ripple too — they were aligned with the deleted clip's
     * <em>head</em>, so after deletion they should align with the
     * gap-closed position, which is exactly {@code tick -
     * deletedDuration}.</p>
     *
     * @return number of clips actually shifted.
     */
    private static int rippleClipsContainer(Object container, Object skipClip, int deletedTick, int deletedDuration)
    {
        if (container == null)
        {
            return 0;
        }

        int shifted = 0;

        try
        {
            Object listObj = invoke(container, "get");
            if (!(listObj instanceof List<?> list))
            {
                return 0;
            }

            /* Snapshot to avoid CME if Clips.get() returned a live view. */
            List<Object> snapshot = new ArrayList<>(list);

            for (Object clip : snapshot)
            {
                if (clip == skipClip)
                {
                    continue;
                }

                int tick = readIntValue(clip, "tick");

                if (tick >= deletedTick)
                {
                    int newTick = Math.max(0, tick - deletedDuration);
                    writeIntValue(clip, "tick", newTick);
                    shifted++;
                }
            }
        }
        catch (Throwable t)
        {
            BBSToolsMod.LOG.warn("rippleClipsContainer error on {}: {}",
                container.getClass().getSimpleName(), t.toString());
        }

        return shifted;
    }

    /**
     * Removes the selected clip from whichever track-container owns
     * it. We don't search ahead-of-time — instead we call {@code
     * remove(Object)} on every container; only the owning one actually
     * drops it.
     */
    private static void removeFromAllContainers(Object film, Object clipToRemove)
    {
        List<Object> containers = new ArrayList<>();

        containers.add(getField(film, "camera"));
        containers.add(getField(film, "screen"));

        Object replays = getField(film, "replays");
        if (replays != null)
        {
            try
            {
                Object replayList = invoke(replays, "getList");
                if (replayList instanceof List<?> list)
                {
                    for (Object replay : list)
                    {
                        containers.add(getField(replay, "actions"));
                    }
                }
            }
            catch (Throwable ignored) {}
        }

        for (Object container : containers)
        {
            if (container == null) continue;

            try
            {
                /* Clips.remove(Clip) — Clip arg type, so look up by
                 *  declared name and pass via reflection. */
                Method remove = findMethod(container.getClass(), "remove", 1);
                if (remove != null)
                {
                    remove.invoke(container, clipToRemove);
                }
            }
            catch (Throwable t)
            {
                BBSToolsMod.LOG.debug("remove failed on container {}: {}",
                    container.getClass().getSimpleName(), t.toString());
            }
        }
    }

    /* === Tiny reflection toolkit ============================= */

    /**
     * Read {@code (ValueInt) target.<fieldName>}.get() with the
     * standard {@code clip.tick.get()} / {@code clip.duration.get()}
     * pattern used across BBS.
     */
    private static int readIntValue(Object target, String fieldName) throws Exception
    {
        Object valueInt = getField(target, fieldName);
        if (valueInt == null)
        {
            throw new IllegalStateException("Field " + fieldName + " not found or null on " + target.getClass().getName());
        }

        Method get = valueInt.getClass().getMethod("get");
        Object boxed = get.invoke(valueInt);

        if (boxed instanceof Number n)
        {
            return n.intValue();
        }

        throw new IllegalStateException("Field " + fieldName + " did not return a Number (got " + boxed + ")");
    }

    /** Write to a {@code ValueInt} via its {@code set(int)} method. */
    private static void writeIntValue(Object target, String fieldName, int value) throws Exception
    {
        Object valueInt = getField(target, fieldName);
        if (valueInt == null)
        {
            return;
        }

        Method set = findMethod(valueInt.getClass(), "set", 1);
        if (set == null)
        {
            return;
        }

        Class<?> param = set.getParameterTypes()[0];

        if (param == int.class || param == Integer.class)
        {
            set.invoke(valueInt, value);
        }
        else if (param == Object.class)
        {
            set.invoke(valueInt, (Object) Integer.valueOf(value));
        }
    }

    private static Object getField(Object target, String name)
    {
        if (target == null) return null;

        Class<?> cls = target.getClass();

        while (cls != null && cls != Object.class)
        {
            try
            {
                Field f = cls.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            }
            catch (NoSuchFieldException e)
            {
                cls = cls.getSuperclass();
            }
            catch (Throwable t)
            {
                return null;
            }
        }

        return null;
    }

    private static Object invoke(Object target, String name) throws Exception
    {
        Method m = findMethod(target.getClass(), name, 0);
        if (m == null)
        {
            throw new NoSuchMethodException(name + "() on " + target.getClass().getName());
        }
        return m.invoke(target);
    }

    private static void invokeIfPresent(Object target, String name)
    {
        try
        {
            Method m = findMethod(target.getClass(), name, 0);
            if (m != null)
            {
                m.invoke(target);
            }
        }
        catch (Throwable t)
        {
            BBSToolsMod.LOG.debug("invokeIfPresent({}) failed: {}", name, t.toString());
        }
    }

    /** First method on {@code cls} (or super) with the given name and arg count. */
    private static Method findMethod(Class<?> cls, String name, int arity)
    {
        while (cls != null)
        {
            for (Method m : cls.getDeclaredMethods())
            {
                if (m.getName().equals(name) && m.getParameterCount() == arity)
                {
                    m.setAccessible(true);
                    return m;
                }
            }
            cls = cls.getSuperclass();
        }
        return null;
    }

    private static void reply(FabricClientCommandSource source, String msg)
    {
        source.sendFeedback(Component.literal("[BBSTools] " + msg));
    }

}
