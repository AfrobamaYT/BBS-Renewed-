package mchorse.bbs_mod.utils.keyframes.generic.factories;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.StringType;
import mchorse.bbs_mod.utils.math.IInterpolation;

public class StringKeyframeFactory implements IGenericKeyframeFactory<String>
{
    @Override
    public String fromData(BaseType data)
    {
        return data.isString() ? data.asString() : "";
    }

    @Override
    public BaseType toData(String value)
    {
        return new StringType(value);
    }

    @Override
    public String copy(String value)
    {
        return value;
    }

    @Override
    public String interpolate(String a, String b, IInterpolation interpolation, float x)
    {
        return a;
    }
}