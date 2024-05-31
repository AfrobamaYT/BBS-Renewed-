package mchorse.bbs_mod.utils.keyframes.generic.factories;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.IntType;
import mchorse.bbs_mod.utils.interps.IInterp;

public class IntegerKeyframeFactory implements IGenericKeyframeFactory<Integer>
{
    @Override
    public Integer fromData(BaseType data)
    {
        return data.isNumeric() ? data.asNumeric().intValue() : 0;
    }

    @Override
    public BaseType toData(Integer value)
    {
        return new IntType(value);
    }

    @Override
    public Integer copy(Integer value)
    {
        return value;
    }

    @Override
    public Integer interpolate(Integer a, Integer b, IInterp interpolation, float x)
    {
        return (int) interpolation.interpolate(a.intValue(), b.intValue(), x);
    }
}