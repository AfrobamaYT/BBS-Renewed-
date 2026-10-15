// Coded by Afrobama/Claude
package com.afrobama.bbstools.client.editorcam;

import com.afrobama.bbstools.client.keybinds.BBSToolsKeys;
import com.mojang.blaze3d.platform.InputConstants;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Owns the editor-cam state + F8 hotkey loop.
 *
 * <p>F8 cycles forward through ALL replays of the most recent BBS film
 * controller. Each press swaps the player's view camera (via
 * {@link EditorCamController} added to BBS's camera-controller stack)
 * to the next replay in the list. After the last replay the cycle
 * returns to the player's normal view. Shift+F8 cycles BACKWARDS
 * through the same list.</p>
 *
 * <p>The controller reads the chosen replay's keyframes at the current
 * film scrub-tick — so as the user scrubs / plays / edits keyframes,
 * the editor cam follows that motion automatically.</p>
 *
 * <p>Why this is workflow-critical: animating an actor (e.g. Herobrine)
 * placed far from the user's position is painful when the camera is
 * the player. This lets the user pick any replay placed near the
 * subject and instantly view from that replay's perspective without
 * flying or interrupting their session.</p>
 */
public final class EditorCamHandler
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/editor-cam");

    private static final EditorCamController CONTROLLER = new EditorCamController();

    private static int cycleIndex = -1;       /* -1 = inactive (player view) */
    private static boolean controllerAdded;
    /** Identity of the last film we cycled in. If the user switches to
     *  a different film mid-cycle, the saved cycleIndex would point at
     *  whatever-was-there in the OLD film and now refer to the wrong
     *  replay (or nothing). Reset cycle when this changes. */
    private static int lastFilmIdentity;
    /** Last film snapshot captured while the dashboard was open so an
     *  in-world F8 (no playback, dashboard closed) still has a film
     *  reference to cycle from. */
    private static Film lastEditedFilm;

    /** Edge-detection state for the BBS KeyCombo polling. We hold these
     *  per-combo so the press fires once per key-down transition,
     *  matching vanilla {@code wasPressed()} semantics. */
    private static boolean wasPressedCycleOnly;
    private static boolean wasPressedCycleAll;

    private EditorCamHandler() {}

    public static void register()
    {
        /* Force-load BBSToolsKeys so the EDITOR_CAM_CYCLE / _ALL combos
         *  exist in BBS's keybind editor + KeybindOverrides system. */
        BBSToolsKeys.touch();

        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
        LOG.info("Editor-cam handler registered (F8 cycles forward through all replays; Shift+F8 cycles backward). Rebindable in BBS keybind editor.");
    }

    private static void tick()
    {
        try
        {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getWindow() == null) return;

            /* Keep lastEditedFilm fresh while a dashboard screen is open
             *  so in-world F8 (no playback) still has a film to cycle. */
            if (mc.gui.screen() != null)
            {
                captureEditedFilm();
            }

            long handle = mc.getWindow().handle();

            /* Check the SHIFT-modifier combo first — its key list
             *  contains the F8-equivalent main key + Shift. If both
             *  combo's keys are down, treat as Shift+F8. */
            boolean shiftCurrentlyDown = comboCurrentlyHeld(handle, BBSToolsKeys.EDITOR_CAM_CYCLE_ALL);
            boolean baseCurrentlyDown = comboCurrentlyHeld(handle, BBSToolsKeys.EDITOR_CAM_CYCLE);

            /* Edge-detect: fire on the off→on transition only.
             *  Shift+F8 reverses cycle direction; plain F8 advances. */
            if (shiftCurrentlyDown && !wasPressedCycleAll)
            {
                cycleStep(-1);
            }
            else if (baseCurrentlyDown && !wasPressedCycleOnly && !shiftCurrentlyDown)
            {
                /* Base combo press only fires when the all-replays
                 *  combo is NOT pressed simultaneously — otherwise both
                 *  fire on the same Shift+F8 press. */
                cycleStep(+1);
            }

            wasPressedCycleAll = shiftCurrentlyDown;
            wasPressedCycleOnly = baseCurrentlyDown;
        }
        catch (Throwable t)
        {
            LOG.debug("editor-cam tick failed: {}", t.toString());
        }
    }

    /** True when every key in the combo's key list is currently held
     *  down on the GLFW window. */
    private static boolean comboCurrentlyHeld(long windowHandle, KeyCombo combo)
    {
        if (combo == null || combo.keys == null || combo.keys.isEmpty()) return false;
        for (Integer k : combo.keys)
        {
            if (k == null) return false;
            if (!InputConstants.isKeyDown(k)) return false;
        }
        return true;
    }

    private static void cycleStep(int direction)
    {
        /* Film identity: switching films invalidates the saved
         *  cycleIndex (it now refers to a different list). */
        int filmId = currentFilmIdentity();
        if (filmId != lastFilmIdentity)
        {
            lastFilmIdentity = filmId;
            cycleIndex = -1;
            /* Also drop the active controller — its captured Replay
             *  pointer is from the OLD film and would crash on
             *  keyframes lookup. */
            removeController();
        }

        List<Replay> replays = collectReplays();

        if (replays.isEmpty())
        {
            chat("§e[BBSTools] §cNo replays in current film — open a film with at least one replay first.");
            return;
        }

        /* Cycle states: -1 (player view) ↔ 0 ↔ 1 ↔ ... ↔ size-1.
         *  Forward wraps size-1 → -1 → 0. Backward wraps -1 → size-1.
         *  At either boundary, drop back to player view (cycleIndex = -1). */
        if (direction > 0)
        {
            cycleIndex = (cycleIndex >= replays.size() - 1) ? -1 : cycleIndex + 1;
        }
        else
        {
            cycleIndex = (cycleIndex <= -1) ? replays.size() - 1 : cycleIndex - 1;
        }

        if (cycleIndex == -1)
        {
            removeController();
            chat("§6[BBSTools] §fEditor-cam: §a→ player view §7(F8 next, Shift+F8 prev)");
            com.afrobama.bbstools.sonden.Meldesonde.HALTER.aktiv.aktion("editor-cam", 
                "cycle → player view");
            return;
        }

        Replay r = replays.get(cycleIndex);
        CONTROLLER.setReplay(r);
        addController();
        chat("§6[BBSTools] §fEditor-cam: §a→ replay '" + safeName(r) + "' §7("
            + (cycleIndex + 1) + "/" + replays.size() + ", F8 next, Shift+F8 prev)");
        com.afrobama.bbstools.sonden.Meldesonde.HALTER.aktiv.aktion("editor-cam", 
            "cycle → replay '" + safeName(r) + "' (" + (cycleIndex + 1) + "/" + replays.size() + ")");
    }

    private static void addController()
    {
        try
        {
            if (controllerAdded) return;
            BBSModClient.getCameraController().add(CONTROLLER);
            controllerAdded = true;
        }
        catch (Throwable t)
        {
            LOG.warn("addController failed: {}", t.toString());
        }
    }

    private static void removeController()
    {
        try
        {
            if (!controllerAdded) return;
            BBSModClient.getCameraController().remove(CONTROLLER);
            controllerAdded = false;
        }
        catch (Throwable t)
        {
            LOG.warn("removeController failed: {}", t.toString());
        }
    }

    /** Cheap identity hash of the active film — used to invalidate the
     *  cycle when the user switches films. System.identityHashCode is
     *  fine because we only care about reference-equality (a different
     *  Film instance = different list). */
    private static int currentFilmIdentity()
    {
        try
        {
            List<BaseFilmController> ctls = BBSModClient.getFilms().getControllers();
            if (ctls != null && !ctls.isEmpty())
            {
                BaseFilmController ctl = ctls.get(ctls.size() - 1);
                return System.identityHashCode(ctl.film);
            }
            /* Mirror collectReplays' fallback chain so identity-based
             *  reset actually fires when the user switches films
             *  while in dashboard / in-world-without-playback. */
            Film edited = findEditedFilm();
            if (edited != null) return System.identityHashCode(edited);
            if (lastEditedFilm != null) return System.identityHashCode(lastEditedFilm);
            return 0;
        }
        catch (Throwable t) { return 0; }
    }

    /** Resolve the replays a user is currently working with. Walks the
     *  same chain F8 cycle uses (playing controller → edited film in
     *  dashboard → last-snapshotted edited film) so dock + editor-cam
     *  always agree on what "the current film" means. Public so the
     *  Phase 10 Replay Dock can reuse the resolution rather than
     *  duplicating the fallback logic. */
    public static List<Replay> collectReplays()
    {
        List<Replay> out = new ArrayList<>();
        Film film = null;

        /* Prefer the currently-PLAYING controller's film. */
        try
        {
            List<BaseFilmController> ctls = BBSModClient.getFilms().getControllers();
            if (ctls != null && !ctls.isEmpty())
            {
                BaseFilmController ctl = ctls.get(ctls.size() - 1);
                if (ctl != null) film = ctl.film;
            }
        }
        catch (Throwable ignored) {}

        /* Fall back to the currently-EDITED film in the dashboard's
         *  UIFilmPanel. User is normally in edit-mode (not playback)
         *  when pressing F8, so getControllers() is empty there. */
        if (film == null)
        {
            film = findEditedFilm();
        }

        /* Final fallback: the last-snapshotted film captured while the
         *  user had it open in the dashboard. Covers the case where
         *  the user closes the dashboard and presses F8 in-world
         *  without active playback. */
        if (film == null && lastEditedFilm != null)
        {
            film = lastEditedFilm;
        }

        if (film == null) return out;

        try
        {
            for (Replay r : film.replays.getList())
            {
                if (r == null) continue;
                out.add(r);
            }
        }
        catch (Throwable ignored) {}
        return out;
    }

    /** Per-tick snapshot from the open UIFilmPanel: keeps lastEditedFilm
     *  fresh so an in-world F8 has the same film the user was just
     *  editing. */
    private static void captureEditedFilm()
    {
        try
        {
            Film f = findEditedFilm();
            if (f != null) lastEditedFilm = f;
        }
        catch (Throwable ignored) {}
    }

    /** Walk the current MC screen's UI tree to find an open UIFilmPanel,
     *  return its film. Returns null if dashboard isn't open or no panel. */
    private static Film findEditedFilm()
    {
        try
        {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || !(mc.gui.screen() instanceof mchorse.bbs_mod.ui.framework.UIScreen)) return null;
            mchorse.bbs_mod.ui.framework.UIScreen screen = (mchorse.bbs_mod.ui.framework.UIScreen) mc.gui.screen();
            mchorse.bbs_mod.ui.framework.UIBaseMenu menu = screen.getMenu();
            if (menu == null) return null;
            mchorse.bbs_mod.ui.framework.UIBaseMenu.UIRootElement root = menu.getRoot();
            if (root == null) return null;
            java.util.List<mchorse.bbs_mod.ui.film.UIFilmPanel> panels =
                root.getChildren(mchorse.bbs_mod.ui.film.UIFilmPanel.class);
            if (panels == null || panels.isEmpty()) return null;
            return panels.get(0).getData();
        }
        catch (Throwable ignored) { return null; }
    }

    /** A replay is "actor-style" if it has a RENDERABLE Form attached.
     *  Camera-style replays are pure position/rotation keyframe
     *  carriers — they either have no Form OR have an invisible
     *  placeholder Form (AnchorForm).
     *
     *  <p>NOTE: we deliberately ignore the {@code actor} ValueBoolean
     *  here. BBS's default for new replays is {@code actor=true} even
     *  for camera-only replays the user creates without picking a
     *  form — so gating on actor=true would incorrectly exclude
     *  camera replays the user already had (the issue they reported:
     *  "ich hatte die ganze zeit schon ein kamera replay drinne aber
     *  die error nachricht kommt trotzdem"). The form-only check is
     *  the correct semantic.</p> */
    private static boolean isActorReplay(Replay r)
    {
        try
        {
            /* Honor explicit actor=false override first — user can mark
             *  any replay as camera-style by unchecking "actor". */
            try
            {
                Object actor = r.actor.get();
                if (Boolean.FALSE.equals(actor)) return false;
            }
            catch (Throwable ignored) {}

            if (r.form != null)
            {
                Object f = r.form.get();
                if (f == null) return false;
                /* AnchorForm = invisible placeholder = camera-style. */
                if (f instanceof mchorse.bbs_mod.forms.forms.AnchorForm) return false;
                return true;
            }
        }
        catch (Throwable ignored) {}
        return false;
    }

    /** Display name of a replay with all the null/throw guards needed
     *  for chat lines and overlay rows. Public so the Phase 10 Replay
     *  Dock and any future overlay can reuse the helper rather than
     *  re-inventing it. (n2 from the Phase 10 review.) */
    public static String safeName(Replay r)
    {
        if (r == null) return "(null)";
        try
        {
            String n = r.getName();
            return (n == null || n.isEmpty()) ? "(unnamed)" : n;
        }
        catch (Throwable t) { return "(?)"; }
    }

    private static void chat(String msg)
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

    public static boolean isActive() { return controllerAdded; }
    public static int getCycleIndex() { return cycleIndex; }
    public static EditorCamController getController() { return CONTROLLER; }

    /** Focus editor-cam on a specific {@link Replay} — used by the
     *  Phase 10 Replay Dock so the user can jump straight to a clip
     *  without F8-cycling. Falls back to player view when the target
     *  is null or not in the current film. */
    public static void cycleTo(Replay target)
    {
        if (target == null) { removeController(); return; }
        try
        {
            List<Replay> replays = collectReplays();
            int idx = -1;
            for (int i = 0; i < replays.size(); i++)
            {
                if (replays.get(i) == target) { idx = i; break; }
            }
            if (idx < 0) { removeController(); return; }
            cycleIndex = idx;
            /* Refresh the film-identity tracker so the next cycleStep
             *  doesn't see this jump as a film switch and wipe the
             *  cycleIndex back to -1 (bug C6 from the Phase 10 review). */
            lastFilmIdentity = currentFilmIdentity();
            CONTROLLER.setReplay(target);
            addController();
            chat("§6[BBSTools] §fEditor-cam: §a→ replay '" + safeName(target) + "'");
        }
        catch (Throwable t)
        {
            LOG.warn("cycleTo failed", t);
        }
    }

}
