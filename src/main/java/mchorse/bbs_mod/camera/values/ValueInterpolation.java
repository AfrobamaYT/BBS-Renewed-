package mchorse.bbs_mod.camera.values;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.StringType;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import mchorse.bbs_mod.utils.math.IInterpolation;
import mchorse.bbs_mod.utils.math.Interpolation;

public class ValueInterpolation extends BaseValueBasic<IInterpolation>
{
    public ValueInterpolation(String id)
    {
        super(id, Interpolation.LINEAR);
    }

    @Override
    public BaseType toData()
    {
        return new StringType(this.value.toString());
    }

    @Override
    public void fromData(BaseType data)
    {
        this.value = Interpolation.valueOf(data.asString());
    }
}