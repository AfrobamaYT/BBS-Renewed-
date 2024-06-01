package mchorse.bbs_mod.utils.interps;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.utils.CollectionUtils;

import java.util.Map;

public class Interpolation extends BaseValue
{
    private final Map<String, IInterp> map;

    private IInterp interp = Interps.LINEAR;
    private double v1;
    private double v2;
    private double v3;
    private double v4;

    public Interpolation(String id, Map<String, IInterp> map)
    {
        super(id);

        this.map = map;
    }

    public double interpolate(InterpContext context)
    {
        return this.interp.interpolate(context.extra(this.v1, this.v2, this.v3, this.v4));
    }

    public IInterp getInterp()
    {
        return this.interp;
    }

    public void setInterp(IInterp interp)
    {
        this.preNotifyParent();
        this.interp = interp;
        this.postNotifyParent();
    }

    public double getV1()
    {
        return this.v1;
    }

    public void setV1(double v1)
    {
        this.preNotifyParent();
        this.v1 = v1;
        this.postNotifyParent();
    }

    public double getV2()
    {
        return this.v2;
    }

    public void setV2(double v2)
    {
        this.preNotifyParent();
        this.v2 = v2;
        this.postNotifyParent();
    }

    public double getV3()
    {
        return this.v3;
    }

    public void setV3(double v3)
    {
        this.preNotifyParent();
        this.v3 = v3;
        this.postNotifyParent();
    }

    public double getV4()
    {
        return this.v4;
    }

    public void setV4(double v4)
    {
        this.preNotifyParent();
        this.v4 = v4;
        this.postNotifyParent();
    }

    @Override
    public BaseType toData()
    {
        ListType list = new ListType();

        list.addString(CollectionUtils.getKey(this.map, this.interp));
        list.addDouble(this.v1);
        list.addDouble(this.v2);
        list.addDouble(this.v3);
        list.addDouble(this.v4);

        return list;
    }

    @Override
    public void fromData(BaseType data)
    {
        if (data.isList())
        {
            ListType list = data.asList();

            if (list.size() >= 5)
            {
                String key = list.getString(0);
                IInterp interp = this.map.get(key);

                if (interp != null)
                {
                    this.interp = interp;
                }

                this.v1 = list.getDouble(1);
                this.v2 = list.getDouble(2);
                this.v3 = list.getDouble(3);
                this.v4 = list.getDouble(4);
            }
        }
    }
}