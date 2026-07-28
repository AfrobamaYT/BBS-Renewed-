package mchorse.bbs_mod.camera.clips;

import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;
import mchorse.bbs_mod.utils.interps.Lerps;

public abstract class CameraClip extends Clip
{
    /**
     * Whether this clip drives the camera's position/angle (true) or only draws an overlay
     * on top of the frame (false).
     *
     * ⛔ MERGE 2026-07-27: aus dem CML-upstream. Dessen screen-clips (ColorClip, VignetteClip,
     * LetterboxClip, GrainClip, CinematicClip, EyeClip, ScreenNodeClip, BossBarClip, ImageClip,
     * VideoClip, HotbarClip) sind post-effekte auf der kamera-timeline und ueberschreiben das
     * mit false — sie bewegen die kamera nicht. Ohne diese basis-methode kompiliert die
     * gesamte post-FX-schicht nicht.
     */
    public boolean isPositionClip()
    {
        return true;
    }

    public void shutdown(ClipContext context)
    {}

    public void fromCamera(Camera camera)
    {}

    /**
     * Evaluate this clip one tick past its nominal end ("the last frame").
     *
     * ⛔ INVARIANTE: das hier prueft {@link #enabled} ABSICHTLICH NICHT — es ist eine reine
     * rechen-hilfe, die auch an einem ausgeschalteten clip ein ergebnis liefern muss
     * (UIDollyClip.reverse, DollyToPathConverter rechnen damit beim editieren). Wer diese
     * methode auf dem PLAYBACK-/preview-pfad ruft, muss enabled SELBST pruefen — sonst
     * wirkt ein deaktivierter clip auf die kamera (war genau der fall in
     * RunnerCameraController.applyEditedClipEnd).
     */
    public void applyLast(ClipContext context, Position position)
    {
        int duration = this.duration.get();

        this.applyClip(context.setup(this.tick.get() + duration, duration, 0, 0), position);
    }

    public void apply(ClipContext context, Position position)
    {
        if (!this.enabled.get())
        {
            return;
        }

        float factor = this.envelope.factorEnabled(this.duration.get(), context.relativeTick + context.transition);

        if (factor == 1)
        {
            this.applyClip(context, position);
        }
        else
        {
            Position temporary = new Position();

            temporary.set(position);
            this.applyClip(context, temporary);

            position.point.x = Lerps.lerp(position.point.x, temporary.point.x, factor);
            position.point.y = Lerps.lerp(position.point.y, temporary.point.y, factor);
            position.point.z = Lerps.lerp(position.point.z, temporary.point.z, factor);

            position.angle.yaw = (float) Lerps.lerpYaw(position.angle.yaw, temporary.angle.yaw, factor);
            position.angle.pitch = Lerps.lerp(position.angle.pitch, temporary.angle.pitch, factor);
            position.angle.roll = Lerps.lerp(position.angle.roll, temporary.angle.roll, factor);
            position.angle.fov = Lerps.lerp(position.angle.fov, temporary.angle.fov, factor);
        }
    }

    protected abstract void applyClip(ClipContext context, Position position);
}