package mchorse.bbs_mod.utils.keyframes;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interps;

public class Keyframe extends BaseValue
{
    public Keyframe prev;
    public Keyframe next;

    private long tick;
    private double value;

    private IInterp interp = Interps.LINEAR;

    private float rx = 5;
    private float ry;
    private float lx = 5;
    private float ly;

    public Keyframe(String id, long tick, double value)
    {
        this(id);

        this.tick = tick;
        this.value = value;
    }

    public Keyframe(String id)
    {
        super(id);

        this.prev = this;
        this.next = this;
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

    public double getValue()
    {
        return this.value;
    }

    public void setValue(double value)
    {
        this.preNotifyParent();
        this.value = value;
        this.postNotifyParent();
    }

    public IInterp getInterpolation()
    {
        return this.interp;
    }

    public void setInterpolation(IInterp interp)
    {
        this.preNotifyParent();
        this.interp = interp;
        this.postNotifyParent();
    }

    public float getRx()
    {
        return this.rx;
    }

    public void setRx(float rx)
    {
        this.preNotifyParent();
        this.rx = rx;
        this.postNotifyParent();
    }

    public float getRy()
    {
        return this.ry;
    }

    public void setRy(float ry)
    {
        this.preNotifyParent();
        this.ry = ry;
        this.postNotifyParent();
    }

    public float getLx()
    {
        return this.lx;
    }

    public void setLx(float lx)
    {
        this.preNotifyParent();
        this.lx = lx;
        this.postNotifyParent();
    }

    public float getLy()
    {
        return this.ly;
    }

    public void setLy(float ly)
    {
        this.preNotifyParent();
        this.ly = ly;
        this.postNotifyParent();
    }

    public double interpolateTicks(Keyframe frame, double ticks)
    {
        return KeyframeInterpolation.interpolate(this, frame, (ticks - this.tick) / (frame.tick - this.tick));
    }

    public double interpolate(Keyframe frame, double x)
    {
        return KeyframeInterpolation.interpolate(this, frame, x);
    }

    public Keyframe copy()
    {
        Keyframe frame = new Keyframe("", this.tick, this.value);

        frame.copy(this);

        return frame;
    }

    public void copy(Keyframe keyframe)
    {
        this.tick = keyframe.tick;
        this.value = keyframe.value;
        this.interp = keyframe.interp;
        this.lx = keyframe.lx;
        this.ly = keyframe.ly;
        this.rx = keyframe.rx;
        this.ry = keyframe.ry;
    }

    @Override
    public BaseType toData()
    {
        MapType data = new MapType();

        data.putLong("tick", this.tick);
        data.putDouble("value", this.value);

        if (this.interp != Interps.LINEAR)
        {
            int index = KeyframeInterpolation.INTERPOLATIONS.indexOf(this.interp);

            if (index >= 0)
            {
                data.putInt("interp", index);
            }
        }
        if (this.rx != 5) data.putFloat("rx", this.rx);
        if (this.ry != 0) data.putFloat("ry", this.ry);
        if (this.lx != 5) data.putFloat("lx", this.lx);
        if (this.ly != 0) data.putFloat("ly", this.ly);

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
        if (map.has("value")) this.value = map.getDouble("value");
        if (map.has("interp"))
        {
            int index = map.getInt("interp");

            if (CollectionUtils.inRange(KeyframeInterpolation.INTERPOLATIONS, index))
            {
                this.interp = KeyframeInterpolation.INTERPOLATIONS.get(index);
            }
        }
        if (map.has("rx")) this.rx = map.getFloat("rx");
        if (map.has("ry")) this.ry = map.getFloat("ry");
        if (map.has("lx")) this.lx = map.getFloat("lx");
        if (map.has("ly")) this.ly = map.getFloat("ly");
    }
}
