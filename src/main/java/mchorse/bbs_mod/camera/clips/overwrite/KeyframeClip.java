package mchorse.bbs_mod.camera.clips.overwrite;

import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.clips.CameraClip;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;
import mchorse.bbs_mod.utils.keyframes.generic.GenericKeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.generic.factories.KeyframeFactories;

/**
 * Keyframe fixture
 * 
 * This fixture provides a much flexible control over camera, allowing setting 
 * up different transitions between points with different easing.
 */
public class KeyframeClip extends CameraClip
{
    public final GenericKeyframeChannel<Double> x = new GenericKeyframeChannel<>("x", KeyframeFactories.DOUBLE);
    public final GenericKeyframeChannel<Double> y = new GenericKeyframeChannel<>("y", KeyframeFactories.DOUBLE);
    public final GenericKeyframeChannel<Double> z = new GenericKeyframeChannel<>("z", KeyframeFactories.DOUBLE);
    public final GenericKeyframeChannel<Double> yaw = new GenericKeyframeChannel<>("yaw", KeyframeFactories.DOUBLE);
    public final GenericKeyframeChannel<Double> pitch = new GenericKeyframeChannel<>("pitch", KeyframeFactories.DOUBLE);
    public final GenericKeyframeChannel<Double> roll = new GenericKeyframeChannel<>("roll", KeyframeFactories.DOUBLE);
    public final GenericKeyframeChannel<Double> fov = new GenericKeyframeChannel<>("fov", KeyframeFactories.DOUBLE);

    public GenericKeyframeChannel<Double>[] channels;

    public KeyframeClip()
    {
        super();

        this.channels = new GenericKeyframeChannel[] {this.x, this.y, this.z, this.yaw, this.pitch, this.roll, this.fov};

        for (GenericKeyframeChannel<Double> channel : this.channels)
        {
            this.add(channel);
        }
    }

    @Override
    public void fromCamera(Camera camera)
    {
        Position pos = new Position(camera);

        this.x.insert(0, pos.point.x);
        this.y.insert(0, pos.point.y);
        this.z.insert(0, pos.point.z);
        this.yaw.insert(0, (double) pos.angle.yaw);
        this.pitch.insert(0, (double) pos.angle.pitch);
        this.roll.insert(0, (double) pos.angle.roll);
        this.fov.insert(0, (double) pos.angle.fov);
    }

    @Override
    public void applyClip(ClipContext context, Position position)
    {
        float t = context.relativeTick + context.transition;

        if (!this.x.isEmpty()) position.point.x = this.x.interpolate(t);
        if (!this.y.isEmpty()) position.point.y = this.y.interpolate(t);
        if (!this.z.isEmpty()) position.point.z = this.z.interpolate(t);
        if (!this.yaw.isEmpty()) position.angle.yaw = this.yaw.interpolate(t).floatValue();
        if (!this.pitch.isEmpty()) position.angle.pitch = this.pitch.interpolate(t).floatValue();
        if (!this.roll.isEmpty()) position.angle.roll = this.roll.interpolate(t).floatValue();
        if (!this.fov.isEmpty()) position.angle.fov = this.fov.interpolate(t).floatValue();
    }

    @Override
    public Clip create()
    {
        return new KeyframeClip();
    }

    @Override
    protected void breakDownClip(Clip original, int offset)
    {
        super.breakDownClip(original, offset);

        for (GenericKeyframeChannel<Double> channel : this.channels)
        {
            channel.moveX(-offset);
        }
    }
}