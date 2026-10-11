// Coded by Afrobama/Claude — Phase 11: clip-driven camera-source registry
package com.afrobama.bbstools.client.campreview;

import com.afrobama.bbstools.clip.BBSToolsAddCameraClip;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.clips.misc.TrackerClientClip;
import mchorse.bbs_mod.camera.clips.modifiers.TrackerClip;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.WorldFilmController;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Tracks which {@link BBSToolsAddCameraClip}s are currently active in
 * the playing film's camera timeline. Phase 12's pre-pass render mixin
 * iterates this list each frame and renders one preview viewport per
 * active clip.
 *
 * <p>Polled in {@code ClientTickEvents.END_CLIENT_TICK} so the active
 * set always lags at most one tick behind playback. Cheap O(clips)
 * scan — no GL state touched.</p>
 *
 * <p>Each {@link Active} entry holds the clip + the camera pose
 * resolved from its offset/angle properties applied to the target
 * replay's keyframes at the current film tick. The pose is what the
 * pre-pass mixin pushes into the singleton Camera before re-entering
 * renderWorld.</p>
 */
public final class ActiveCameraClipRegistry
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/cam-preview/active-clip");

    /** Per-active-clip entry: stable UUID for framebuffer lifecycle +
     *  the resolved {x, y, z, yaw, pitch} pose for this frame. */
    public static final class Active
    {
        public final UUID id;
        public final BBSToolsAddCameraClip clip;
        public final double[] pose = new double[5];
        public boolean poseValid;

        Active(UUID id, BBSToolsAddCameraClip clip)
        {
            this.id = id;
            this.clip = clip;
        }
    }

    /** Snapshot of clips that are active right now. CopyOnWrite to keep
     *  the render thread safe while the tick thread writes the next
     *  snapshot. */
    private static volatile List<Active> currentActive = Collections.emptyList();

    /** Persistent UUID per Clip object so framebuffers track the same
     *  clip across activation gaps. WeakHashMap so dropped clips don't
     *  pin themselves alive. */
    private static final java.util.WeakHashMap<BBSToolsAddCameraClip, UUID> CLIP_IDS = new java.util.WeakHashMap<>();

    private ActiveCameraClipRegistry() {}

    public static List<Active> snapshot()
    {
        return currentActive;
    }

    public static void register()
    {
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
        LOG.info("Active-camera-clip registry ready (Phase 11)");
    }

    private static int lastDiagState = -1;

    private static void tick()
    {
        try
        {
            int state = 0;
            String note = "";

            BaseFilmController playback = currentController();
            Film film;
            int tick;
            WorldFilmController worldCtl = null;

            if (playback instanceof WorldFilmController world)
            {
                film = world.film;
                tick = Math.max(0, world.getTick());
                worldCtl = world;
                state = 1;
                note = "playback@tick=" + tick;
            }
            else
            {
                mchorse.bbs_mod.ui.film.UIFilmPanel panel = findEditedFilmPanel();
                if (panel == null)
                {
                    state = 2;
                    note = "no playback + no UIFilmPanel found";
                    diagOnce(state, note);
                    if (!currentActive.isEmpty()) currentActive = Collections.emptyList();
                    return;
                }
                film = panel.getData();
                tick = Math.max(0, panel.getCursor());
                state = 3;
                note = "edit-mode panel@tick=" + tick;
            }

            if (film == null || film.camera == null)
            {
                state = 4;
                note = "film null or no camera timeline (film=" + (film == null ? "null" : "ok") + ")";
                diagOnce(state, note);
                if (!currentActive.isEmpty()) currentActive = Collections.emptyList();
                return;
            }

            List<Clip> clipsNow = film.camera.getClips(tick);
            if (clipsNow == null || clipsNow.isEmpty())
            {
                state = 5;
                note = "no clips at tick=" + tick + " on camera timeline (total clips on cam track=" + film.camera.get().size() + ")";
                diagOnce(state, note);
                if (!currentActive.isEmpty()) currentActive = Collections.emptyList();
                return;
            }

            /* Diag the clip types so we can see if the cursor IS in a clip
             *  but it's not the Add-Camera type we expect. */
            int addCameraCount = 0;
            StringBuilder types = new StringBuilder();
            for (Clip c : clipsNow)
            {
                if (types.length() > 0) types.append(",");
                types.append(c.getClass().getSimpleName());
                if (c instanceof BBSToolsAddCameraClip) addCameraCount++;
            }

            List<Active> next = new ArrayList<>(clipsNow.size());
            for (Clip c : clipsNow)
            {
                if (!(c instanceof BBSToolsAddCameraClip cam)) continue;
                if (!cam.enabled.get()) continue;

                UUID id = CLIP_IDS.computeIfAbsent(cam, k -> UUID.randomUUID());
                Active a = new Active(id, cam);
                resolvePose(a, worldCtl, film, tick);
                next.add(a);
            }

            currentActive = next;

            state = next.isEmpty() ? 6 : 7;
            note = "tick=" + tick + " clipsAtCursor=" + clipsNow.size()
                + " types=[" + types + "] addCameraCount=" + addCameraCount
                + " active=" + next.size();
            diagOnce(state, note);
        }
        catch (Throwable t)
        {
            LOG.debug("active-clip tick failed: {}", t.toString());
        }
    }

    /** Log once per state transition so spam stays low — only when
     *  something changes about what the registry sees. */
    private static void diagOnce(int state, String note)
    {
        if (state == lastDiagState) return;
        lastDiagState = state;
        LOG.info("[diag] state={} {}", state, note);
    }

    /** Walk the current MC screen's UI tree to find the open
     *  UIFilmPanel. Mirrors EditorCamHandler's same-named helper —
     *  this is the canonical "currently-edited film" lookup. */
    private static mchorse.bbs_mod.ui.film.UIFilmPanel findEditedFilmPanel()
    {
        try
        {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc == null || !(mc.gui.screen() instanceof mchorse.bbs_mod.ui.framework.UIScreen)) return null;
            mchorse.bbs_mod.ui.framework.UIScreen screen = (mchorse.bbs_mod.ui.framework.UIScreen) mc.gui.screen();
            mchorse.bbs_mod.ui.framework.UIBaseMenu menu = screen.getMenu();
            if (menu == null) return null;
            mchorse.bbs_mod.ui.framework.UIBaseMenu.UIRootElement root = menu.getRoot();
            if (root == null) return null;
            List<mchorse.bbs_mod.ui.film.UIFilmPanel> panels =
                root.getChildren(mchorse.bbs_mod.ui.film.UIFilmPanel.class);
            if (panels != null && !panels.isEmpty()) return panels.get(0);
        }
        catch (Throwable ignored) {}
        return null;
    }

    /** Orbit-around-target pose resolution. {@code clip.angle.y} =
     *  azimuth (horizontal orbit angle around target), {@code clip.angle.x}
     *  = elevation (vertical orbit angle), {@code clip.offset} magnitude
     *  = orbit distance from target. The camera position is computed
     *  to sit on the orbit sphere and looks at the target so the replay
     *  stays centered in the alt-tile no matter which slider value is
     *  set. Matches user expectation (Phase 15 v10): "rotating shouldn't
     *  rotate the replays — only the camera moves around them". */
    private static void resolvePose(Active a, WorldFilmController world, Film film, int tick)
    {
        try
        {
            int selectorIdx = a.clip.selector.get();

            /* Source A: playback controller has the live entity map. */
            IEntity target = null;
            if (world != null)
            {
                try { target = world.getEntities().get(selectorIdx); }
                catch (Throwable ignored) {}
            }

            double targetX, targetY, targetZ;
            boolean haveTarget = false;

            if (target != null)
            {
                targetX = target.getX();
                targetY = target.getY();
                targetZ = target.getZ();
                haveTarget = true;
            }
            else
            {
                /* Edit mode: read the target replay's keyframes. */
                try
                {
                    if (film.replays != null
                        && selectorIdx >= 0
                        && selectorIdx < film.replays.getList().size())
                    {
                        mchorse.bbs_mod.film.replays.Replay replay = film.replays.getList().get(selectorIdx);
                        if (replay != null)
                        {
                            targetX = replay.keyframes.x.interpolate(tick);
                            targetY = replay.keyframes.y.interpolate(tick);
                            targetZ = replay.keyframes.z.interpolate(tick);
                            haveTarget = true;
                        }
                        else { targetX = targetY = targetZ = 0; }
                    }
                    else { targetX = targetY = targetZ = 0; }
                }
                catch (Throwable ignored)
                {
                    targetX = targetY = targetZ = 0;
                }
            }

            if (!haveTarget)
            {
                /* No target — fall back to clip.offset as world pos +
                 *  clip.angle as raw yaw/pitch so the dock shows
                 *  SOMETHING (Phase 11 legacy path). */
                a.pose[0] = a.clip.offset.get().x;
                a.pose[1] = a.clip.offset.get().y;
                a.pose[2] = a.clip.offset.get().z;
                a.pose[3] = (float) a.clip.angle.get().y;
                a.pose[4] = (float) a.clip.angle.get().x;
                a.poseValid = true;
                return;
            }

            /* Orbit-around-target math. */
            double ox = a.clip.offset.get().x;
            double oy = a.clip.offset.get().y;
            double oz = a.clip.offset.get().z;
            double distance = Math.sqrt(ox * ox + oy * oy + oz * oz);
            if (distance < 0.01D) distance = 3D;   // sensible default

            double azimuthRad = Math.toRadians(a.clip.angle.get().y);
            double elevRad = Math.toRadians(a.clip.angle.get().x);

            double horiz = distance * Math.cos(elevRad);
            double camX = targetX + horiz * Math.sin(azimuthRad);
            double camY = targetY + distance * Math.sin(elevRad);
            double camZ = targetZ + horiz * Math.cos(azimuthRad);

            /* Camera looks at target — compute the yaw/pitch needed. */
            double dx = targetX - camX;
            double dy = targetY - camY;
            double dz = targetZ - camZ;
            double horizDist = Math.sqrt(dx * dx + dz * dz);
            double camYaw = Math.toDegrees(Math.atan2(-dx, dz));
            double camPitch = Math.toDegrees(Math.atan2(-dy, horizDist));

            a.pose[0] = camX;
            a.pose[1] = camY;
            a.pose[2] = camZ;
            a.pose[3] = camYaw;
            a.pose[4] = camPitch;
            a.poseValid = true;
        }
        catch (Throwable ignored)
        {
            a.poseValid = false;
        }
    }

    private static BaseFilmController currentController()
    {
        try
        {
            List<BaseFilmController> ctls = BBSModClient.getFilms().getControllers();
            if (ctls != null && !ctls.isEmpty())
            {
                return ctls.get(ctls.size() - 1);
            }
        }
        catch (Throwable ignored) {}
        return null;
    }

}
