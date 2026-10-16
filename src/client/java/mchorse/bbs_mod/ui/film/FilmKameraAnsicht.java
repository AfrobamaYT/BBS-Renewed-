package mchorse.bbs_mod.ui.film;

import com.mojang.blaze3d.pipeline.TextureTarget;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.clips.CameraClipContext;
import mchorse.bbs_mod.camera.clips.misc.TrackerClientClip;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.utils.clips.Clip;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.DeltaTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Blender's Animation workspace: a second view through the film camera while the main viewport stays
 * free for posing. Each frame the view is on screen, the world is rendered once more from the camera
 * at the playhead into a target with the film's aspect, through
 * {@link com.afrobama.blendertransform.ZweitKamera} -- the pass the preview-camera clip uses (render
 * after the main level, restore its state afterwards).
 *
 * <p>Costs a world render per frame, so it runs only while {@link UIFilmKameraAnsicht} was drawn in
 * the last quarter second, never during a video export, and the target is capped at
 * {@link #MAX_BREITE} pixels.</p>
 */
public final class FilmKameraAnsicht
{
    private static final Logger LOG = LoggerFactory.getLogger("bbs/camera-view");
    private static final int MAX_BREITE = 960;

    private static TextureTarget ziel;
    private static long sichtbarBis;
    private static int wunschB;
    private static int wunschH;
    private static boolean kaputt;
    private static CameraClipContext kontext;
    private static final Position STAND = new Position();
    /** Set while ZweitKamera renders for this view, so the world pass knows whose pass it is */
    private static UIFilmPanel durchgang;

    private FilmKameraAnsicht()
    {}

    /** From the view's render: it is on screen, at this size in screen pixels */
    public static void gesehen(int breite, int hoehe)
    {
        sichtbarBis = System.currentTimeMillis() + 250L;
        wunschB = breite;
        wunschH = hoehe;
    }

    public static TextureTarget ziel()
    {
        return ziel;
    }

    public static boolean kaputt()
    {
        return kaputt;
    }

    /** After the main level, from GameRendererPreviewPrePassLogik */
    public static void zeichnen(DeltaTracker delta)
    {
        if (kaputt || System.currentTimeMillis() > sichtbarBis || BBSModClient.getVideoRecorder().isRecording()
            || mchorse.bbs_mod.ui.framework.UIScreen.getCurrentMenu() != BBSModClient.getDashboardIfCreated())
        {
            return;
        }

        UIFilmPanel panel = BBSModClient.getDashboard() != null
            && BBSModClient.getDashboard().panels.panel instanceof UIFilmPanel p ? p : null;

        if (panel == null || panel.getData() == null || panel.getRunner() == null)
        {
            return;
        }

        bereitstellen();

        Position p = kamera(panel, delta);
        /* Minecraft's yaw is BBS's minus 180 degrees (CameraController.getYaw); roll is not carried by the pass */
        double[] pose = {p.point.x, p.point.y, p.point.z, p.angle.yaw - 180D, p.angle.pitch};

        try
        {
            durchgang = panel;
            com.afrobama.blendertransform.ZweitKamera.zeichnen(delta, ziel, pose, p.angle.fov);
        }
        catch (Throwable t)
        {
            kaputt = true; /* one loud failure, then a plain message in the view instead of a crash loop */
            LOG.error("Camera view: world pass failed, view switched off until restart", t);
        }
        finally
        {
            durchgang = null;
        }
    }

    /**
     * From both world passes in place of the screen's renderInWorld (BBSRendering#renderCoolStuff draws,
     * the COLLECT_SUBMITS pass in BBSModClient submits shadows and name tags): true = skip the screen.
     * In any second camera pass the editor overlays stay out -- they would capture the bone gizmo and
     * the main view's projection from the wrong camera. In this view's own pass the actors are drawn
     * plainly instead, since in the editor they come only through renderInWorld.
     */
    public static boolean nurFiguren(LevelRenderContext context)
    {
        if (!com.afrobama.blendertransform.ZweitKamera.aktiv())
        {
            return false;
        }

        if (durchgang != null && durchgang.getController().editorController != null)
        {
            durchgang.getController().editorController.renderPlain(context);
        }

        return true;
    }

    /**
     * The film camera at the playhead, evaluated like FilmCameraExporter does. Not the runner's own
     * position: in a free main view the runner is not the active controller and its position stays at
     * the last camera-mode frame (measured: turning the camera 180 degrees left the view unchanged).
     * Tracker clips set TrackerClientClip's statics while they evaluate -- kept and restored.
     */
    private static Position kamera(UIFilmPanel panel, DeltaTracker delta)
    {
        if (!(kontext instanceof KameraBildKontext))
        {
            kontext = new KameraBildKontext();
        }

        int tick = panel.getCursor();
        float teil = panel.getRunner().isRunning() ? delta.getGameTimeDeltaPartialTick(false) : 0F;
        boolean hand = TrackerClientClip.renderHand;
        boolean hud = TrackerClientClip.renderHud;
        IEntity wer = TrackerClientClip.currentEntity;

        kontext.clips = panel.getData().camera;
        kontext.entities = panel.getRunner().getContext().entities; /* tracking clips follow the live actors */

        try
        {
            kontext.clipData.clear();
            kontext.setup(tick, teil);

            for (Clip clip : kontext.clips.getClips(tick))
            {
                kontext.apply(clip, STAND);
            }

            kontext.currentLayer = 0;
        }
        finally
        {
            TrackerClientClip.renderHand = hand;
            TrackerClientClip.renderHud = hud;
            TrackerClientClip.currentEntity = wer;
        }

        return STAND;
    }

    /**
     * A target with the export's aspect, as large as the view allows (capped). Not BBSRendering's video
     * size: that is the viewport's (measured 1468x696 for a 1280x720 film), which stretched the frame.
     */
    private static void bereitstellen()
    {
        float seite = (float) BBSSettings.videoWidth.get() / Math.max(1, BBSSettings.videoHeight.get());
        int b = Math.max(16, Math.min(MAX_BREITE, Math.min(wunschB, Math.round(wunschH * seite))));
        int h = Math.max(16, Math.round(b / seite));

        if (ziel != null && ziel.width == b && ziel.height == h)
        {
            return;
        }

        if (ziel != null)
        {
            ziel.destroyBuffers();
        }

        ziel = new TextureTarget("bbs-camera-view", b, h,
            com.mojang.renderpearl.api.GpuFormat.RGBA8_UNORM, com.mojang.renderpearl.api.GpuFormat.D32_FLOAT);
    }
}
