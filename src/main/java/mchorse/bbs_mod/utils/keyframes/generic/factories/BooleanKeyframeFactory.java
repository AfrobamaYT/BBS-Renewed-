package mchorse.bbs_mod.utils.keyframes.generic.factories;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ByteType;
import mchorse.bbs_mod.utils.math.IInterpolation;

public class BooleanKeyframeFactory implements IGenericKeyframeFactory<Boolean>
{
    @Override
    public Boolean fromData(BaseType data)
    {
        return data.isNumeric() && data.asNumeric().boolValue();
    }

    @Override
    public BaseType toData(Boolean value)
    {
        return new ByteType(value);
    }

    @Override
    public Boolean copy(Boolean value)
    {
        return value;
    }

    @Override
    public Boolean interpolate(Boolean a, Boolean b, IInterpolation interpolation, float x)
    {
        return a;
    }
}