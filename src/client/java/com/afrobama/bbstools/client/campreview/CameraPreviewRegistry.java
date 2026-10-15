// Coded by Afrobama/Claude — BBS Renewed Camera Preview Phase 1
package com.afrobama.bbstools.client.campreview;

import mchorse.bbs_mod.BBSModClient;
import com.afrobama.bbstools.sonden.Messregister;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Singleton registry for all active camera-preview windows. Owns the
 * world-render hook that fills each preview's framebuffer from its
 * camera pose, and the lifecycle hook that disposes framebuffers when
 * the client shuts down.
 *
 * <p><b>Phase 1 scope:</b> manual-pose previews only. The framebuffer
 * is rendered by capturing the main-framebuffer at a moment when the
 * world is fully drawn ({@code LevelRenderEvents.END_MAIN}). Phase 2 adds a
 * proper re-entry into {@code GameRenderer.renderWorld} per preview so
 * each shows its own POV; tracker-mode binds to the keyframe interpolator.</p>
 */
public final class CameraPreviewRegistry
{
    private static final Logger LOG = LoggerFactory.getLogger("bbs/cam-preview");

    private static final CopyOnWriteArrayList<CameraPreview> PREVIEWS = new CopyOnWriteArrayList<>();

    private CameraPreviewRegistry() {}

    public static List<CameraPreview> list()
    {
        return PREVIEWS;
    }

    public static CameraPreview find(UUID id)
    {
        for (CameraPreview p : PREVIEWS)
        {
            if (p.id.equals(id)) return p;
        }
        return null;
    }

    /** Add a manually-positioned preview at the player's current pose. */
    public static CameraPreview addAtPlayer(String name)
    {
        Minecraft mc = Minecraft.getInstance();
        CameraPreview p = new CameraPreview(name);

        if (mc != null && mc.player != null)
        {
            p.posX = mc.player.getX();
            p.posY = mc.player.getEyeY();
            p.posZ = mc.player.getZ();
            p.yaw = mc.player.getYRot();
            p.pitch = mc.player.getXRot();
        }

        PREVIEWS.add(p);
        LOG.info("Camera preview '{}' added at ({}, {}, {})", name, p.posX, p.posY, p.posZ);
        return p;
    }

    /** Add a tracker-mode preview that follows a replay's keyframes. */
    public static CameraPreview addTracking(String name, Replay replay)
    {
        CameraPreview p = new CameraPreview(name);
        p.mode = CameraPreview.Mode.TRACKER;
        p.trackedReplay = replay;
        PREVIEWS.add(p);
        LOG.info("Camera preview '{}' added tracking replay", name);
        return p;
    }

    public static boolean remove(UUID id)
    {
        Iterator<CameraPreview> it = PREVIEWS.iterator();
        while (it.hasNext())
        {
            CameraPreview p = it.next();
            if (p.id.equals(id))
            {
                p.disposeFramebuffer();
                PREVIEWS.remove(p);
                return true;
            }
        }
        return false;
    }

    public static void clear()
    {
        for (CameraPreview p : PREVIEWS) p.disposeFramebuffer();
        PREVIEWS.clear();
    }

    /** Coded by Afrobama/Claude — Phase 3 auto-layout. Tiles every
     *  enabled preview's window into a grid that fits the current
     *  screen. Goal: 1 preview = bottom-right, 2 = side-by-side,
     *  3-4 = 2x2, 5-6 = 3x2, 7-9 = 3x3. Uses the framebuffer aspect
     *  (16:9 default) to size each tile. */
    public static void autoLayout(int screenWidth, int screenHeight)
    {
        java.util.List<CameraPreview> active = new ArrayList<>();
        for (CameraPreview p : PREVIEWS) if (p.enabled) active.add(p);
        int n = active.size();
        if (n == 0) return;

        int cols, rows;
        if (n == 1) { cols = 1; rows = 1; }
        else if (n == 2) { cols = 2; rows = 1; }
        else if (n <= 4) { cols = 2; rows = 2; }
        else if (n <= 6) { cols = 3; rows = 2; }
        else { cols = 3; rows = (n + 2) / 3; }

        int margin = 8;
        int tileW = (screenWidth - margin * (cols + 1)) / cols;
        /* Lock to 16:9 then clamp to vertical fit. */
        int tileH = tileW * 9 / 16;
        int totalH = rows * tileH + margin * (rows + 1);
        if (totalH > screenHeight)
        {
            tileH = (screenHeight - margin * (rows + 1)) / rows;
            tileW = tileH * 16 / 9;
        }

        for (int i = 0; i < n; i++)
        {
            int row = i / cols;
            int col = i % cols;
            CameraPreview p = active.get(i);
            p.windowX = margin + col * (tileW + margin);
            p.windowY = margin + row * (tileH + margin);
            p.windowW = tileW;
            p.windowH = tileH;
        }
        LOG.info("Auto-layout: {} preview(s) tiled into {}x{} grid", n, cols, rows);
    }

    /** Cycle a preview's framebuffer resolution through small/medium/large.
     *  Resolution change requires framebuffer reallocation, which happens
     *  lazily on next render via ensureFramebuffer(). */
    public static void cycleResolution(CameraPreview p)
    {
        if (p.fbWidth == 160) { p.fbWidth = 320; p.fbHeight = 180; }
        else if (p.fbWidth == 320) { p.fbWidth = 640; p.fbHeight = 360; }
        else if (p.fbWidth == 640) { p.fbWidth = 1280; p.fbHeight = 720; }
        else { p.fbWidth = 160; p.fbHeight = 90; }
        /* Force reallocation on next render. */
        p.disposeFramebuffer();
    }

    public static void setTracker(CameraPreview p, Replay replay)
    {
        p.trackedReplay = replay;
        p.mode = (replay == null) ? CameraPreview.Mode.MANUAL : CameraPreview.Mode.TRACKER;
    }

    /** Resolve a preview's pose AT THE CURRENT FRAME, including tracker
     *  interpolation. Returns a pose array {x, y, z, yaw, pitch}. */
    public static double[] resolvePose(CameraPreview p)
    {
        if (p.mode == CameraPreview.Mode.TRACKER && p.trackedReplay != null)
        {
            try
            {
                int tick = currentFilmTick();
                Replay r = p.trackedReplay;
                double x = r.keyframes.x.interpolate(tick) + p.trackOffsetX;
                double y = r.keyframes.y.interpolate(tick) + p.trackOffsetY;
                double z = r.keyframes.z.interpolate(tick) + p.trackOffsetZ;
                float yaw = r.keyframes.yaw.interpolate(tick).floatValue() + p.trackOffsetYaw;
                float pitch = r.keyframes.pitch.interpolate(tick).floatValue() + p.trackOffsetPitch;
                double[] pose = { x, y, z, yaw, pitch };
                cachePose(p, pose);
                return pose;
            }
            catch (Throwable t)
            {
                /* Phase 8 hardening: when keyframe interp fails AND we
                 *  have a previously-cached pose, return that instead of
                 *  jumping to the manual fallback (which would be wrong
                 *  for a tracker preview — pos defaults to 0,0,0). */
                if (p.hasResolvedPose)
                {
                    return p.lastResolvedPose.clone();
                }
            }
        }
        double[] pose = { p.posX, p.posY, p.posZ, p.yaw, p.pitch };
        cachePose(p, pose);
        return pose;
    }

    private static void cachePose(CameraPreview p, double[] pose)
    {
        System.arraycopy(pose, 0, p.lastResolvedPose, 0, 5);
        p.hasResolvedPose = true;
    }

    private static int currentFilmTick()
    {
        try
        {
            List<BaseFilmController> ctls = BBSModClient.getFilms().getControllers();
            if (ctls != null && !ctls.isEmpty()) return ctls.get(ctls.size() - 1).getTick();
        }
        catch (Throwable ignored) {}
        return 0;
    }

    /** Allocate (or resize, if dimensions changed) the framebuffer for
     *  a preview. Must run on the render thread. */
    public static void ensureFramebuffer(CameraPreview p)
    {
        if (p.framebuffer != null
            && p.framebuffer.width == p.fbWidth
            && p.framebuffer.height == p.fbHeight)
        {
            return;
        }
        p.disposeFramebuffer();

        try
        {
            p.framebuffer = new TextureTarget("bbstools-kameravorschau",
                    p.fbWidth, p.fbHeight,
                    true, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM);
            /* 26.2: setClearColor ist entfallen. Die Loeschfarbe wird nicht mehr am Ziel
             * geparkt, sondern beim Loeschen mitgegeben -- das tut CameraPreviewRenderHook
             * (siehe dort den 26.2-Ersatz fuer bindWrite + clearColor + clear). Hier ist
             * also nichts zu ersetzen, die Zeile ist ersatzlos weg. */
        }
        catch (Throwable t)
        {
            LOG.warn("Framebuffer alloc failed for preview '{}': {}", p.name, t.toString());
        }
    }

    public static void register()
    {
        /* On shutdown / client stop, free GPU resources. */
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> clear());

        /* Phase 1 stub: register a render hook that ensures framebuffers
         *  are allocated. Actual world re-render is wired in
         *  CameraPreviewRenderHook (separate file) to keep this class focused
         *  on state mgmt. */
        // PHASE NICHT BELEGT GLEICHWERTIG: alt war LAST/END/AFTER_SETUP. 26.2 hat dafuer kein benanntes Gegenstueck -- ob diese Phase an derselben Stelle liegt, ist am laufenden Bild zu pruefen (Export gegen Fenster).
        LevelRenderEvents.END_MAIN.register(CameraPreviewRenderHook::onWorldRenderLast);

        Messregister.HALTER.aktiv.text("CameraPreview", out ->
        {
            out.append("previews=").append(PREVIEWS.size()).append('\n');
            for (CameraPreview p : PREVIEWS)
            {
                out.append(" - '").append(p.name).append("' mode=").append(p.mode);
                if (p.mode == CameraPreview.Mode.MANUAL)
                {
                    out.append(String.format(" pos=(%.1f, %.1f, %.1f) yaw=%.1f pitch=%.1f",
                        p.posX, p.posY, p.posZ, p.yaw, p.pitch));
                }
                else
                {
                    out.append(" tracking=")
                        .append(p.trackedReplay == null ? "null" : safeName(p.trackedReplay));
                }
                out.append(" window=(").append(p.windowX).append(',').append(p.windowY)
                    .append(' ').append(p.windowW).append('x').append(p.windowH).append(')');
                out.append(" fb=").append(p.framebuffer == null ? "null" : "alloc").append('\n');
            }
        });

        LOG.info("Camera-preview registry ready (Phase 1: state + framebuffer lifecycle).");
    }

    private static String safeName(Replay r)
    {
        try
        {
            String n = r.getName();
            return (n == null || n.isEmpty()) ? "(unnamed)" : n;
        }
        catch (Throwable t) { return "(?)"; }
    }

}
