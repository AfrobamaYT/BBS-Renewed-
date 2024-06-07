package mchorse.bbs_mod.utils.keyframes;

import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;

/**
 * Keyframe segment.
 *
 * This structure contains all the necessary data to create an interpolated
 * value.
 */
public class KeyframeSegment <T>
{
    public final Keyframe<T> a;
    public final Keyframe<T> b;

    public final Keyframe<T> preA;
    public final Keyframe<T> postB;
    public int duration;
    public float offset;
    public float x;

    public static <T> T interpolate(Keyframe<T> a, Keyframe<T> b, float x)
    {
        IKeyframeFactory<T> factory = a.getFactory();

        return factory.copy(factory.interpolate(a.getValue(), a.getValue(), b.getValue(), b.getValue(), a.getInterpolation().wrap(), x));
    }

    public KeyframeSegment(Keyframe<T> a, Keyframe<T> b)
    {
        this.a = a;
        this.b = b;

        KeyframeChannel<T> channel = (KeyframeChannel<T>) a.getParent();
        int index = channel.getKeyframes().indexOf(a);

        if (index >= 0 && a != b)
        {
            Keyframe<T> preA = channel.get(index - 1);
            Keyframe<T> postB = channel.get(index + 2);

            this.preA = preA == null ? a : preA;
            this.postB = postB == null ? b : postB;
        }
        else
        {
            this.preA = a;
            this.postB = b;
        }
    }

    public void setup(float ticks)
    {
        int forcedDuration = this.a.getDuration();

        this.duration = forcedDuration > 0 ? forcedDuration : (int) (this.b.getTick() - this.a.getTick());
        this.offset = ticks - this.a.getTick();
        this.x = this.duration == 0 ? 0F : this.offset / (float) this.duration;
    }

    public T createInterpolated()
    {
        IKeyframeFactory<T> factory = this.a.getFactory();

        if (this.isSame())
        {
            return factory.copy(this.a.getValue());
        }

        return factory.copy(factory.interpolate(this.preA.getValue(), this.a.getValue(), this.b.getValue(), this.postB.getValue(), this.a.getInterpolation().wrap(), this.x));
    }

    public boolean isSame()
    {
        return this.a == this.b;
    }

    public Keyframe<T> getClosest()
    {
        return this.x > 0.5F ? this.b : this.a;
    }
}