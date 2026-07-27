package mchorse.bbs_mod.camera.controller;

import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.clips.CameraClipContext;
import mchorse.bbs_mod.camera.clips.misc.AudioClientClip;
import mchorse.bbs_mod.camera.clips.misc.TrackerClientClip;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.Clips;

public abstract class CameraWorkCameraController implements ICameraController
{
    protected CameraClipContext context;
    protected Position position = new Position();

    public CameraWorkCameraController()
    {
        this.context = new CameraClipContext();
    }

    public CameraWorkCameraController setWork(Clips clips)
    {
        this.context.clips = clips;

        return this;
    }

    public CameraClipContext getContext()
    {
        return this.context;
    }

    public Position getPosition()
    {
        return this.position;
    }

    protected void apply(Camera camera, int ticks, float transition)
    {
        if (camera != null)
        {
            this.position.set(camera);
        }

        this.context.clipData.clear();
        this.context.setup(ticks, transition);

        /*
         * afrobama 2026-07-28 — hand/HUD-lebenszyklus pro frame.
         * QUELLE: cml-fresh/.../camera/controller/CameraWorkCameraController.java:49-77.
         *
         * ⛔ DIESER BLOCK FEHLTE IN FS KOMPLETT — und er ist die gemeinsame wurzel von
         * drei gemeldeten bugs. resetRenderFlags() setzt controllerRanThisFrame; ohne den
         * schalter steigt beginLevelRendering() in seiner ERSTEN zeile aus und setzt
         * renderHand/renderHud gleich wieder auf false zurueck. Folge (alles gemessen):
         *   handShowProgress/hudShowProgress bleiben 0.0  -> keine einblend-animation
         *   levelRenderingActive bleibt false             -> kein hand-state-swap
         *   scaleChanged bleibt false                     -> hotbar behaelt viewport-skalierung
         * Der nutzer sah: hand statisch, HUD zu klein, und bei beiden flags gar nichts.
         *
         * MUSS VOR der clip-schleife stehen: die clips setzen in applyClip() die flags
         * renderHand/renderHud, der reset raeumt den VORIGEN frame ab.
         */
        TrackerClientClip.resetRenderFlags();
        TrackerClientClip.currentTickDelta = transition;

        for (Clip clip : this.context.clips.getClips(ticks))
        {
            this.context.apply(clip, this.position);
        }

        /* Bob + sprint-FOV NACH der clip-schleife: erst dort steht fest, welche entity
         * getrackt wird und ob die hand ueberhaupt gezeichnet werden soll. */
        IEntity bobEntity = TrackerClientClip.currentEntity;

        if (bobEntity != null)
        {
            TrackerClientClip.updateSprintFov(bobEntity.isSprinting(), ticks);

            /* Beim schleichen kein bob — sonst wackelt die hand, waehrend der koerper steht. */
            if (TrackerClientClip.renderHand && !bobEntity.isSneaking())
            {
                TrackerClientClip.updateBob(
                    (float) bobEntity.getPrevX(), (float) bobEntity.getPrevZ(),
                    (float) bobEntity.getX(), (float) bobEntity.getZ(), ticks);
            }
            else
            {
                TrackerClientClip.clearBobState();
            }
        }
        else
        {
            TrackerClientClip.clearBobState();
            TrackerClientClip.resetSprintFov();
        }

        /* Die urspruengliche GUI-skalierung merken, solange sie noch gilt — waehrend des
         * HUD-durchgangs wird sie ueberschrieben, und ohne diesen wert bleibt die hotbar
         * auf der viewport-groesse haengen (zu klein). */
        if (TrackerClientClip.renderHud)
        {
            TrackerClientClip.captureOriginalGuiScale();
        }

        AudioClientClip.manageSounds(this.context);

        /* After sound management, since applyLast() re-runs context.setup() */
        this.applyEditedClipEnd(ticks);

        this.context.currentLayer = 0;

        if (camera != null)
        {
            this.position.apply(camera);
        }
    }

    /**
     * Hook for letting the clip that's currently being edited render its final
     * state (at {@code relativeTick == duration}) when the cursor sits on the
     * clip's exclusive end boundary, which {@link Clips#getClips(int)} excludes.
     * No-op during playback; only the editor preview controller overrides it.
     */
    protected void applyEditedClipEnd(int ticks)
    {}

    @Override
    public int getPriority()
    {
        return 10;
    }
}