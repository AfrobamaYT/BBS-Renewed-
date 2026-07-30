// Coded by Afrobama/Claude — Phase 4: Auto-attach preview to camera replays
package com.afrobama.bbstools.client.campreview;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.forms.AnchorForm;
import mchorse.bbs_mod.forms.forms.Form;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Blender-style auto-attach: polls the currently-active film's replay
 * list each client tick and diffs against a per-replay preview-binding
 * map. State transitions handled:
 *
 * <ul>
 *   <li><b>New replay with AnchorForm</b> → spawn tracker-mode preview.</li>
 *   <li><b>Existing replay's form changed to AnchorForm</b> → spawn preview.</li>
 *   <li><b>Existing replay's form changed away from AnchorForm</b> → remove preview.</li>
 *   <li><b>Replay deleted from film</b> → remove its preview.</li>
 *   <li><b>Film switched/closed</b> → drop all auto-spawned previews.</li>
 * </ul>
 *
 * <p>Manual previews (added via {@code /bbstools campreview add} or
 * Ctrl+Shift+P keybind) are NOT touched by this watcher — only previews
 * we ourselves spawned via auto-attach are owned here. We track that
 * via the {@code AUTO_BOUND} weak-keyed map (Replay → preview-UUID).</p>
 *
 * <p>Polling cost: O(replays) per tick on the client thread, no GL
 * calls — negligible. Polling consolidates ADD/REMOVE/FORM-CHANGE
 * detection in one place and avoids invasive replay-list mixins.</p>
 *
 * @deprecated will be removed in Phase 11 — see new clip-driven design
 */
@Deprecated
public final class CameraReplayWatcher
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/cam-preview/auto-attach");

    /** Replay → UUID of the auto-spawned preview that's tracking it.
     *  WeakHashMap so a forgotten replay (no other refs) eventually
     *  drops from the map even if we miss its removal event. */
    private static final WeakHashMap<Replay, UUID> AUTO_BOUND = new WeakHashMap<>();

    private CameraReplayWatcher() {}

    public static void register()
    {
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
        LOG.info("Camera-replay watcher registered (auto-attach previews to AnchorForm replays)");
    }

    private static void tick()
    {
        try
        {
            Film film = currentFilm();
            if (film == null || film.replays == null)
            {
                if (!AUTO_BOUND.isEmpty()) clearAll();
                return;
            }

            List<Replay> replays = film.replays.getList();
            if (replays == null) return;

            Set<Replay> currentSet = new HashSet<>(replays);

            for (Replay r : replays)
            {
                if (r == null) continue;

                boolean isCamera = isCameraReplay(r);
                boolean isBound = AUTO_BOUND.containsKey(r);

                if (isCamera && !isBound)
                {
                    spawnPreviewFor(r);
                }
                else if (!isCamera && isBound)
                {
                    unbindPreviewFor(r);
                }
            }

            /* Reverse pass: any binding whose replay is no longer in the
             *  film's list (deleted by user) gets its preview removed. */
            Iterator<Map.Entry<Replay, UUID>> it = AUTO_BOUND.entrySet().iterator();
            while (it.hasNext())
            {
                Map.Entry<Replay, UUID> e = it.next();
                if (!currentSet.contains(e.getKey()))
                {
                    CameraPreviewRegistry.remove(e.getValue());
                    it.remove();
                }
            }
        }
        catch (Throwable t)
        {
            LOG.debug("watcher tick failed: {}", t.toString());
        }
    }

    private static boolean isCameraReplay(Replay r)
    {
        try
        {
            Form form = r.form.get();
            return form instanceof AnchorForm;
        }
        catch (Throwable t) { return false; }
    }

    private static void spawnPreviewFor(Replay r)
    {
        try
        {
            String label = safeName(r);
            CameraPreview p = CameraPreviewRegistry.addTracking(label, r);
            AUTO_BOUND.put(r, p.id);

            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getWindow() != null)
            {
                CameraPreviewRegistry.autoLayout(
                    mc.getWindow().getGuiScaledWidth(),
                    mc.getWindow().getGuiScaledHeight());
            }

            LOG.info("Auto-spawned preview '{}' tracking camera replay", label);
        }
        catch (Throwable t)
        {
            LOG.debug("spawnPreviewFor failed: {}", t.toString());
        }
    }

    private static void unbindPreviewFor(Replay r)
    {
        try
        {
            UUID previewId = AUTO_BOUND.remove(r);
            if (previewId != null)
            {
                CameraPreviewRegistry.remove(previewId);
                LOG.info("Auto-removed preview — replay's form is no longer AnchorForm");
            }
        }
        catch (Throwable ignored) {}
    }

    private static void clearAll()
    {
        try
        {
            for (UUID id : AUTO_BOUND.values())
            {
                CameraPreviewRegistry.remove(id);
            }
            AUTO_BOUND.clear();
        }
        catch (Throwable ignored) {}
    }

    /** Find the film the user is currently editing or playing.
     *  Preference order: active playback controller's film → open
     *  UIFilmPanel's film. Mirrors EditorCamHandler's film-discovery
     *  chain so auto-attach behaves consistently with editor cam. */
    private static Film currentFilm()
    {
        try
        {
            List<BaseFilmController> ctls = BBSModClient.getFilms().getControllers();
            if (ctls != null && !ctls.isEmpty())
            {
                BaseFilmController ctl = ctls.get(ctls.size() - 1);
                if (ctl != null) return ctl.film;
            }
        }
        catch (Throwable ignored) {}

        try
        {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || !(mc.gui.screen() instanceof mchorse.bbs_mod.ui.framework.UIScreen)) return null;
            mchorse.bbs_mod.ui.framework.UIScreen screen = (mchorse.bbs_mod.ui.framework.UIScreen) mc.gui.screen();
            mchorse.bbs_mod.ui.framework.UIBaseMenu menu = screen.getMenu();
            if (menu == null) return null;
            mchorse.bbs_mod.ui.framework.UIBaseMenu.UIRootElement root = menu.getRoot();
            if (root == null) return null;
            List<mchorse.bbs_mod.ui.film.UIFilmPanel> panels =
                root.getChildren(mchorse.bbs_mod.ui.film.UIFilmPanel.class);
            if (panels != null && !panels.isEmpty()) return panels.get(0).getData();
        }
        catch (Throwable ignored) {}

        return null;
    }

    private static String safeName(Replay r)
    {
        try
        {
            String n = r.getName();
            if (n != null && !n.isEmpty()) return n;
        }
        catch (Throwable ignored) {}
        return "Camera " + (AUTO_BOUND.size() + 1);
    }

}
