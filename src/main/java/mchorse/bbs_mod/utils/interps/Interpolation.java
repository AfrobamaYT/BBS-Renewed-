package mchorse.bbs_mod.utils.interps;

import mchorse.bbs_mod.data.IDataSerializable;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.utils.CollectionUtils;

import java.util.Map;

public class Interpolation implements IDataSerializable
{
    public IInterp interp = Interps.LINEAR;
    public double v1;
    public double v2;
    public double v3;
    public double v4;

    private final Map<String, IInterp> map;

    public Interpolation(Map<String, IInterp> map)
    {
        this.map = map;
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