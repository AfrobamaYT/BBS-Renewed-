package mchorse.bbs_mod.utils.keyframes.generic;

import mchorse.bbs_mod.utils.keyframes.generic.factories.IGenericKeyframeFactory;

public class GenericKeyframeSegment <T>
{
    public final GenericKeyframe<T> a;
    public final GenericKeyframe<T> b;

    public final GenericKeyframe<T> preA;
    public final GenericKeyframe<T> postB;
    public int duration;
    public float offset;
    public float x;

    public GenericKeyframeSegment(GenericKeyframe<T> a, GenericKeyframe<T> b)
    {
        this.a = a;
        this.b = b;

        GenericKeyframeChannel<T> channel = (GenericKeyframeChannel<T>) a.getParent();
        int index = channel.getKeyframes().indexOf(a);

        if (index >= 0 && a != b)
        {
            GenericKeyframe<T> preA = channel.get(index - 1);
            GenericKeyframe<T> postB = channel.get(index + 2);

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
        IGenericKeyframeFactory<T> factory = this.a.getFactory();

        return factory.copy(factory.interpolate(this.preA.getValue(), this.a.getValue(), this.b.getValue(), this.postB.getValue(), this.a.getInterpolation().wrap(), this.x));
    }

    public boolean isSame()
    {
        return this.a == this.b;
    }

    public GenericKeyframe<T> getClosest()
    {
        return this.x > 0.5F ? this.b : this.a;
    }
}