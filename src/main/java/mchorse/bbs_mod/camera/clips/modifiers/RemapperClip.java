package mchorse.bbs_mod.camera.clips.modifiers;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.clips.CameraClip;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;
import mchorse.bbs_mod.utils.keyframes.generic.GenericKeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.generic.factories.KeyframeFactories;

public class RemapperClip extends CameraClip
{
    public final GenericKeyframeChannel<Double> channel = new GenericKeyframeChannel<>("channel", KeyframeFactories.DOUBLE);

    public RemapperClip()
    {
        super();

        this.add(this.channel);

        this.channel.insert(0, 0D);
        this.channel.insert(BBSSettings.getDefaultDuration(), 1D);
    }

    @Override
    public void applyClip(ClipContext context, Position position)
    {
        double factor = this.channel.interpolate(context.relativeTick + context.transition);
        int duration = this.duration.get();

        System.out.println(factor);

        factor *= duration;
        factor = MathUtils.clamp(factor, 0, duration - 0.0001F);

        context.applyUnderneath(this.tick.get() + (int) factor, (float) (factor % 1), position);
    }

    @Override
    public Clip create()
    {
        return new RemapperClip();
    }

    @Override
    protected void breakDownClip(Clip original, int offset)
    {
        super.breakDownClip(original, offset);

        this.channel.moveX(-offset);
    }
}