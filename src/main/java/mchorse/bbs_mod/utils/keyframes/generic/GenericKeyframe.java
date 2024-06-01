package mchorse.bbs_mod.utils.keyframes.generic;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.settings.values.ValueGroup;
import mchorse.bbs_mod.utils.interps.Interpolation;
import mchorse.bbs_mod.utils.interps.Interps;
import mchorse.bbs_mod.utils.keyframes.generic.factories.IGenericKeyframeFactory;

public class GenericKeyframe <T> extends ValueGroup
{
    private long tick;

    /**
     * Forced duration that would be used instead of the difference
     * between two keyframes, if not 0
     */
    private int duration;

    private T value;
    private final Interpolation interp = new Interpolation("interp", Interps.MAP);

    private final IGenericKeyframeFactory<T> factory;

    public GenericKeyframe(String id, IGenericKeyframeFactory<T> factory, long tick, T value)
    {
        this(id, factory);

        this.tick = tick;
        this.value = value;
    }

    public GenericKeyframe(String id, IGenericKeyframeFactory<T> factory)
    {
        super(id);

        this.factory = factory;
    }

    public IGenericKeyframeFactory<T> getFactory()
    {
        return this.factory;
    }

    public long getTick()
    {
        return this.tick;
    }

    public void setTick(long tick)
    {
        this.preNotifyParent();
        this.tick = tick;
        this.postNotifyParent();
    }

    public int getDuration()
    {
        return this.duration;
    }

    public void setDuration(int duration)
    {
        this.preNotifyParent();
        this.duration = duration;
        this.postNotifyParent();
    }

    public T getValue()
    {
        return this.value;
    }

    public void setValue(T value)
    {
        this.preNotifyParent();
        this.value = value;
        this.postNotifyParent();
    }

    public Interpolation getInterpolation()
    {
        return this.interp;
    }

    public void copy(GenericKeyframe<T> keyframe)
    {
        this.tick = keyframe.tick;
        this.duration = keyframe.duration;
        this.value = this.factory.copy(keyframe.value);
        this.interp.copy(keyframe.interp);
    }

    @Override
    public BaseType toData()
    {
        MapType data = new MapType();

        data.putLong("tick", this.tick);
        data.putInt("duration", this.duration);
        data.put("value", this.factory.toData(this.value));
        data.put("interp", this.interp.toData());

        return data;
    }

    @Override
    public void fromData(BaseType data)
    {
        if (!data.isMap())
        {
            return;
        }

        MapType map = data.asMap();

        if (map.has("tick")) this.tick = map.getLong("tick");
        if (map.has("duration")) this.duration = map.getInt("duration");
        if (map.has("value")) this.value = this.factory.fromData(map.get("value"));
        if (map.has("interp")) this.interp.fromData(map.get("interp"));
    }
}