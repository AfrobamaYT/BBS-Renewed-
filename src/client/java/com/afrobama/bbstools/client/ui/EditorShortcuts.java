// Coded by Afrobama/Claude — useful editor shortcuts commonly missing
// from the BBS film editor. Fabric-registered KeyBindings polled per
// END_CLIENT_TICK; only fire while the BBS dashboard / film panel is
// the active screen so the user typing in chat or running around the
// world never trips one of these accidentally.
//
// Bindings:
//   Ctrl+D            duplicate selected clip (reflection of
//                     UIFilmPanel.cameraEditor.getClip(); clone via the
//                     BBS Clip.copy() method; add at clip.tick + clip.
//                     duration on the same Clips container)
//   Space             toggle play/pause of the timeline (calls
//                     UIFilmPanel.togglePlayback() reflectively so we
//                     don't bind hard to the symbol — BBS-Renewed may
//                     rename it)
//   Home              jump cursor to tick 0
//   End               jump cursor to film duration (camera.
//                     calculateDuration())
//
// All BBS-internal access (UIFilmPanel field reads, Clip.copy(),
// Clips.addClip(), togglePlayback(), etc.) is reflective + wrapped in
// try/catch so a future BBS rename can't crash the client tick loop.
//
// Reference: RecordFromCursorHandler — same END_CLIENT_TICK +
// drain-on-press pattern, but that one uses BBSToolsKeys' KeyCombo
// registry (BBS-side persistence). Editor shortcuts don't need BBS
// keybind editor integration so they live on Fabric KeyBindings — same
// approach as QuickWinKeybinds (F2/F4/F5).
package com.afrobama.bbstools.client.ui;

import com.afrobama.bbstools.BBSToolsMod;
import com.mojang.blaze3d.platform.InputConstants;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import com.afrobama.bbstools.client.keybinds.BBSKeyCategories;

/**
 * Editor-only shortcuts that ship with BBSTools to fill in gaps left
 * by stock BBS (duplicate-clip, set-marker, play/pause, jump to start
 * /end). Polled per client tick via Fabric {@link KeyMapping}s; only
 * fires when a BBS {@code UIScreen} is open AND there is a loaded
 * film, so the bindings never collide with world-time interactions.
 *
 * <p>Wired from {@code BBSToolsModClient.onInitializeClient}.</p>
 */
public final class EditorShortcuts
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/ui/editor-shortcuts");

    /** Ctrl+D — duplicate the selected clip. */
    private static KeyMapping duplicateClip;

    /** Space — toggle play/pause of the BBS timeline. */
    private static KeyMapping togglePlay;

    /** Home — jump the cursor to tick 0. */
    private static KeyMapping jumpStart;

    /** End — jump the cursor to film end. */
    private static KeyMapping jumpEnd;

    /** Track shifted modifiers ourselves because Fabric {@link KeyMapping}s
     *  only resolve to a single physical key — combos like Ctrl+D must
     *  be enforced by polling the modifier state when the main key
     *  pulse arrives. */
    private EditorShortcuts() {}

    public static void register()
    {
        duplicateClip = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.bbstools.editor_duplicate_clip",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_D,
            BBSKeyCategories.BBSTOOLS
        ));

        togglePlay = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.bbstools.editor_toggle_play",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_SPACE,
            BBSKeyCategories.BBSTOOLS
        ));

        jumpStart = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.bbstools.editor_jump_start",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_HOME,
            BBSKeyCategories.BBSTOOLS
        ));

        jumpEnd = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.bbstools.editor_jump_end",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_END,
            BBSKeyCategories.BBSTOOLS
        ));


        ClientTickEvents.END_CLIENT_TICK.register(EditorShortcuts::tick);

        LOG.info("Editor shortcuts registered — Ctrl+D duplicate, Ctrl+Shift+M marker, "
            + "Space play/pause, Home/End jump start/end. Editor-only (gated on BBS UIScreen + loaded film).");
    }

    /* === Tick loop ============================================ */

    private static void tick(Minecraft client)
    {
        if (client == null) return;

        /* Drain wasPressed() unconditionally even when we won't act —
         *  otherwise presses queue up while the gate is closed and all
         *  fire at once when the user opens the editor. */
        boolean fireDuplicate = drain(duplicateClip);
        boolean firePlay      = drain(togglePlay);
        boolean fireStart     = drain(jumpStart);
        boolean fireEnd       = drain(jumpEnd);

        /* Gate: BBS editor (any UIScreen) must be the active screen AND
         *  a film must be loaded. Without the screen gate, Space would
         *  jump the player. */
        if (!isBBSScreenOpen(client))
        {
            return;
        }

        UIFilmPanel panel = getFilmPanel();
        if (panel == null || safeGetFilm(panel) == null)
        {
            return;
        }

        long handle = client.getWindow().handle();
        boolean ctrl  = isKeyHeld(handle, InputConstants.KEY_LCONTROL)
                     || isKeyHeld(handle, InputConstants.KEY_RCONTROL);
        boolean shift = isKeyHeld(handle, InputConstants.KEY_LSHIFT)
                     || isKeyHeld(handle, InputConstants.KEY_RSHIFT);

        try
        {
            /* Ctrl+D — duplicate. Ignore bare D so the user can still
             *  type "D" into BBS textboxes. */
            if (fireDuplicate && ctrl && !shift)
            {
                handleDuplicate(client, panel);
            }

            /* Space — play/pause. Reject when any modifier is held so
             *  text editing isn't disrupted by the editor's input
             *  fields auto-receiving space. We also bail out below if
             *  a textbox is focused (best-effort detection). */
            if (firePlay && !ctrl && !shift && !isLikelyTypingInTextbox(client))
            {
                handleTogglePlay(client, panel);
            }

            /* Home / End — bare keys, no modifier. */
            if (fireStart && !ctrl && !shift)
            {
                handleJumpStart(client, panel);
            }

            if (fireEnd && !ctrl && !shift)
            {
                handleJumpEnd(client, panel);
            }
        }
        catch (Throwable t)
        {
            LOG.debug("editor-shortcut tick failed: {}", t.toString());
        }
    }

    /* === Handlers ============================================= */

    /** Ctrl+D: duplicate the currently selected clip on whichever
     *  editor (camera/action/screen) owns the selection. The clone is
     *  placed at {@code tick = original.tick + original.duration} so
     *  it sits flush after the source. */
    private static void handleDuplicate(Minecraft client, UIFilmPanel panel)
    {
        try
        {
            Object[] selection = findSelectedClipAndContainer(panel);
            if (selection == null)
            {
                actionBar(client, "§e[bbstools] Ctrl+D: no clip selected");
                return;
            }

            Object selectedClip = selection[0];
            Object container    = selection[1];
            String editorName   = (String) selection[2];

            int srcTick     = readIntValue(selectedClip, "tick");
            int srcDuration = readIntValue(selectedClip, "duration");

            /* Clip.copy() is the canonical clone method in BBS. */
            Method copy = findMethod(selectedClip.getClass(), "copy", 0);
            if (copy == null)
            {
                actionBar(client, "§c[bbstools] Ctrl+D: Clip.copy() missing — BBS API mismatch");
                LOG.warn("duplicate: Clip.copy() not found on {}", selectedClip.getClass().getName());
                return;
            }

            Object cloned = copy.invoke(selectedClip);
            if (cloned == null)
            {
                actionBar(client, "§c[bbstools] Ctrl+D: copy returned null");
                return;
            }

            int newTick = Math.max(0, srcTick + srcDuration);
            writeIntValue(cloned, "tick", newTick);

            /* Clips.addClip(Clip) is the public add method on the
             *  camera / screen / action Clips container. */
            Method addClip = findMethod(container.getClass(), "addClip", 1);
            if (addClip == null)
            {
                actionBar(client, "§c[bbstools] Ctrl+D: Clips.addClip() missing — BBS API mismatch");
                LOG.warn("duplicate: addClip not found on {}", container.getClass().getName());
                return;
            }

            addClip.invoke(container, cloned);

            /* Persist + refresh the editor inspector so the duplicate
             *  appears immediately without the user clicking off and
             *  back on. UIFilmPanel.save() / fillData() are the same
             *  hooks RippleDeleteCommand uses. */
            invokeIfPresent(panel, "save");
            invokeIfPresent(panel, "fillData");

            actionBar(client, "§a[bbstools] Ctrl+D — duplicated clip @ tick " + newTick);
            BBSToolsMod.LOG.info("Editor shortcut Ctrl+D: duplicated clip on {} (src tick={}, dur={}, new tick={})",
                editorName, srcTick, srcDuration, newTick);
        }
        catch (Throwable t)
        {
            LOG.warn("Ctrl+D duplicate failed: {}", t.toString(), t);
            actionBar(client, "§c[bbstools] Ctrl+D failed — see bbstools-console.log");
        }
    }


    /** Space: toggle BBS timeline play/pause. UIFilmPanel.
     *  togglePlayback() is the canonical entry — keep reflective so
     *  BBS-Renewed renames don't break compile. */
    private static void handleTogglePlay(Minecraft client, UIFilmPanel panel)
    {
        try
        {
            Method toggle = findMethod(panel.getClass(), "togglePlayback", 0);
            if (toggle != null)
            {
                toggle.invoke(panel);
            }
            else
            {
                /* Fallback: poke runner.toggle(cursor) directly. */
                Object runner = invokeIfReturns(panel, "getRunner");
                if (runner == null) runner = readFieldDeep(panel, "runner");
                if (runner != null)
                {
                    Method runnerToggle = findMethod(runner.getClass(), "toggle", 1);
                    if (runnerToggle != null)
                    {
                        runnerToggle.invoke(runner, panel.getCursor());
                    }
                }
            }

            boolean playing = false;
            try { playing = panel.isRunning(); } catch (Throwable ignored) {}

            actionBar(client, playing
                ? "§a[bbstools] § ▶ playing"
                : "§e[bbstools] § ⏸ paused");
            BBSToolsMod.LOG.info("Editor shortcut Space: toggled play/pause → {}", playing ? "PLAY" : "PAUSE");
        }
        catch (Throwable t)
        {
            LOG.warn("Space toggle-play failed: {}", t.toString(), t);
            actionBar(client, "§c[bbstools] Space toggle failed — see log");
        }
    }

    /** Home: jump cursor to tick 0. */
    private static void handleJumpStart(Minecraft client, UIFilmPanel panel)
    {
        try
        {
            panel.setCursor(0);
            actionBar(client, "§a[bbstools] ⏮ cursor → tick 0");
            BBSToolsMod.LOG.info("Editor shortcut Home: cursor → 0");
        }
        catch (Throwable t)
        {
            LOG.warn("Home jump-to-start failed: {}", t.toString(), t);
        }
    }

    /** End: jump cursor to film end. Uses {@code
     *  film.camera.calculateDuration()} since the camera track is the
     *  film's authoritative end (matches what BBS uses for "end of
     *  film" everywhere). */
    private static void handleJumpEnd(Minecraft client, UIFilmPanel panel)
    {
        try
        {
            Object film = safeGetFilm(panel);
            if (film == null) return;

            Object camera = readFieldDeep(film, "camera");
            int endTick = 0;

            if (camera != null)
            {
                Method calc = findMethod(camera.getClass(), "calculateDuration", 0);
                if (calc != null)
                {
                    Object result = calc.invoke(camera);
                    if (result instanceof Number n) endTick = n.intValue();
                }
            }

            /* Subtract 1 so the cursor lands on the last playable tick
             *  rather than one past the end — mirrors how DAWs / NLEs
             *  treat the End key. Floor at 0 for empty films. */
            int target = Math.max(0, endTick - 1);
            panel.setCursor(target);

            actionBar(client, "§a[bbstools] ⏭ cursor → tick " + target);
            BBSToolsMod.LOG.info("Editor shortcut End: cursor → {} (camera duration={})", target, endTick);
        }
        catch (Throwable t)
        {
            LOG.warn("End jump-to-end failed: {}", t.toString(), t);
        }
    }

    /* === BBS introspection helpers ============================ */

    /** Locate the BBS film panel via the dashboard. */
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

    /** Null-safe wrapper around {@code UIFilmPanel.getData()}. */
    private static Object safeGetFilm(UIFilmPanel panel)
    {
        try { return panel.getData(); } catch (Throwable t) { return null; }
    }

    /** Returns {@code true} when a BBS UIScreen is the active MC screen
     *  (so the user is in the editor, not in-world). We accept ANY
     *  non-null screen here on purpose — BBS opens overlays as
     *  child screens too, and we still want Space/Home/End to behave
     *  consistently across them. The film-panel gate above filters
     *  out "screen is open but no film loaded yet". */
    private static boolean isBBSScreenOpen(Minecraft client)
    {
        Screen current = client.gui.screen();
        if (current == null) return false;

        /* Best-effort: confirm it's a BBS UIScreen by class name so
         *  vanilla chat / settings screens don't trip the shortcuts. */
        Class<?> c = current.getClass();
        while (c != null && c != Object.class)
        {
            String n = c.getName();
            if (n.startsWith("mchorse.bbs_mod.")) return true;
            c = c.getSuperclass();
        }
        return false;
    }

    /** Heuristic: if a focused element on the current BBS screen is a
     *  textbox-like widget, swallow Space so the user can type spaces
     *  into clip titles, marker labels, etc. We don't know BBS's
     *  internal focus model without binding tightly, so we walk a few
     *  well-known UIScreen fields and look for the substring "text"
     *  in the focused element's class name. Safe fallback returns
     *  false (we'd rather miss the guard than swallow the shortcut). */
    private static boolean isLikelyTypingInTextbox(Minecraft client)
    {
        try
        {
            Screen screen = client.gui.screen();
            if (screen == null) return false;

            /* Walk every declared field on the screen + superclasses
             *  looking for one literally named "focused" or "activeText"
             *  or "currentText". BBS-CML's UIScreen exposes a focused
             *  UIElement. */
            Class<?> cls = screen.getClass();
            while (cls != null && cls != Object.class)
            {
                for (Field f : cls.getDeclaredFields())
                {
                    String fn = f.getName().toLowerCase();
                    if (!fn.contains("focus") && !fn.contains("text")) continue;
                    try
                    {
                        f.setAccessible(true);
                        Object val = f.get(screen);
                        if (val == null) continue;
                        String tn = val.getClass().getName().toLowerCase();
                        if (tn.contains("text") || tn.contains("textbox") || tn.contains("textfield"))
                        {
                            return true;
                        }
                    }
                    catch (Throwable ignored) {}
                }
                cls = cls.getSuperclass();
            }
        }
        catch (Throwable ignored) {}
        return false;
    }

    /** Walks the three BBS clip-editor sub-panels and returns
     *  {@code {selectedClip, ownerClipsContainer, editorFieldName}}
     *  for whichever editor has a selection. Returns null if nothing
     *  is selected anywhere. */
    private static Object[] findSelectedClipAndContainer(UIFilmPanel panel)
    {
        String[] editorFields = { "cameraEditor", "actionEditor", "screenEditor" };
        Object film = safeGetFilm(panel);

        for (String fieldName : editorFields)
        {
            Object editor = readFieldDeep(panel, fieldName);
            if (editor == null) continue;

            Object clip;
            try
            {
                Method getClip = findMethod(editor.getClass(), "getClip", 0);
                if (getClip == null) continue;
                clip = getClip.invoke(editor);
            }
            catch (Throwable t) { continue; }

            if (clip == null) continue;

            /* Resolve the Clips container that owns this clip. Camera
             *  + screen are direct fields on Film. Action clips live
             *  per-replay so we walk the replay list and find the one
             *  whose .actions contains our clip. */
            Object container = resolveContainer(film, fieldName, clip);
            if (container == null) continue;

            return new Object[] { clip, container, fieldName };
        }

        return null;
    }

    /** Find the Clips container the given clip lives in. */
    private static Object resolveContainer(Object film, String editorName, Object clip)
    {
        if (film == null) return null;

        if ("cameraEditor".equals(editorName)) return readFieldDeep(film, "camera");
        if ("screenEditor".equals(editorName)) return readFieldDeep(film, "screen");

        /* actionEditor: hunt through replays.getList()[i].actions */
        if ("actionEditor".equals(editorName))
        {
            Object replays = readFieldDeep(film, "replays");
            if (replays == null) return null;

            try
            {
                Method getList = findMethod(replays.getClass(), "getList", 0);
                if (getList == null) return null;

                Object listObj = getList.invoke(replays);
                if (!(listObj instanceof List<?> list)) return null;

                for (Object replay : list)
                {
                    Object actions = readFieldDeep(replay, "actions");
                    if (actions == null) continue;
                    if (containerHoldsClip(actions, clip)) return actions;
                }
            }
            catch (Throwable t)
            {
                LOG.debug("resolveContainer (actions) failed: {}", t.toString());
            }
        }

        return null;
    }

    private static boolean containerHoldsClip(Object container, Object clip)
    {
        try
        {
            Method get = findMethod(container.getClass(), "get", 0);
            if (get == null) return false;
            Object listObj = get.invoke(container);
            if (!(listObj instanceof List<?> list)) return false;
            for (Object c : list) if (c == clip) return true;
        }
        catch (Throwable ignored) {}
        return false;
    }

    /* === Tiny reflection toolkit ============================== */

    private static int readIntValue(Object target, String fieldName) throws Exception
    {
        Object valueInt = readFieldDeep(target, fieldName);
        if (valueInt == null)
        {
            throw new IllegalStateException("Field " + fieldName + " not found on " + target.getClass().getName());
        }

        Method get = valueInt.getClass().getMethod("get");
        Object boxed = get.invoke(valueInt);
        if (boxed instanceof Number n) return n.intValue();
        throw new IllegalStateException(fieldName + " did not return a Number (got " + boxed + ")");
    }

    private static void writeIntValue(Object target, String fieldName, int value) throws Exception
    {
        Object valueInt = readFieldDeep(target, fieldName);
        if (valueInt == null) return;

        Method set = findMethod(valueInt.getClass(), "set", 1);
        if (set == null) return;

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

    private static Object readFieldDeep(Object target, String name)
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

    private static Method findMethod(Class<?> cls, String name, int paramCount)
    {
        Class<?> c = cls;
        while (c != null && c != Object.class)
        {
            for (Method m : c.getDeclaredMethods())
            {
                if (!m.getName().equals(name)) continue;
                if (m.getParameterCount() != paramCount) continue;
                m.setAccessible(true);
                return m;
            }
            c = c.getSuperclass();
        }
        return null;
    }

    private static void invokeIfPresent(Object target, String name)
    {
        try
        {
            Method m = findMethod(target.getClass(), name, 0);
            if (m != null) m.invoke(target);
        }
        catch (Throwable t)
        {
            LOG.debug("invokeIfPresent({}) failed: {}", name, t.toString());
        }
    }

    private static Object invokeIfReturns(Object target, String name)
    {
        try
        {
            Method m = findMethod(target.getClass(), name, 0);
            if (m != null) return m.invoke(target);
        }
        catch (Throwable ignored) {}
        return null;
    }

    /* === Input helpers ======================================== */

    private static boolean drain(KeyMapping kb)
    {
        boolean any = false;
        if (kb == null) return false;
        while (kb.consumeClick()) any = true;
        return any;
    }

    private static boolean isKeyHeld(long handle, int key)
    {
        try { return InputConstants.isKeyDown(net.minecraft.client.Minecraft.getInstance().getWindow(), key); } catch (Throwable t) { return false; }
    }

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
