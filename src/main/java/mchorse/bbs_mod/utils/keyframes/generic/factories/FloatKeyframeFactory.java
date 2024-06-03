package mchorse.bbs_mod.utils.keyframes.generic.factories;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.FloatType;
import mchorse.bbs_mod.utils.interps.IInterp;

public class FloatKeyframeFactory implements IGenericKeyframeFactory<Float>
{
    @Override
    public Float fromData(BaseType data)
    {
        return data.isNumeric() ? data.asNumeric().floatValue() : 0F;
    }

    @Override
    public BaseType toData(Float value)
    {
        return new FloatType(value);
    }

    @Override
    public Float copy(Float value)
    {
        return value;
    }

    @Override
    public Float interpolate(Float preA, Float a, Float b, Float postB, IInterp interpolation, float x)
    {
        return (float) interpolation.interpolate(IInterp.context.set(preA, a, b, postB, x));
    }
}