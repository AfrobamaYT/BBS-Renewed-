package mchorse.bbs_mod.camera.values;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.StringType;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.math.IInterpolation;
import mchorse.bbs_mod.utils.math.Interpolation;

public class ValueInterpolationType extends BaseValueBasic<IInterpolation>
{
    public ValueInterpolationType(String id)
    {
        super(id, Interpolation.HERMITE);
    }

    @Override
    public BaseType toData()
    {
        return new StringType(CollectionUtils.getKey(Interpolation.MAP, this.value));
    }

    @Override
    public void fromData(BaseType base)
    {
        if (base.isString())
        {
            this.value = Interpolation.MAP.get(base.asString());
        }
    }
}