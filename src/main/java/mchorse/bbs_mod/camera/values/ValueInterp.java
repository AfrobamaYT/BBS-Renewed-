package mchorse.bbs_mod.camera.values;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.StringType;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interps;

public class ValueInterp extends BaseValueBasic<IInterp>
{
    public ValueInterp(String id)
    {
        super(id, Interps.LINEAR);
    }

    @Override
    public BaseType toData()
    {
        return new StringType(this.value.getKey());
    }

    @Override
    public void fromData(BaseType data)
    {
        this.value = Interps.get(data.asString());
    }
}