package mchorse.bbs_mod.utils.keyframes.generic.factories;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.utils.interps.IInterp;

public interface IGenericKeyframeFactory <T>
{
    public T fromData(BaseType data);

    public BaseType toData(T value);

    public T copy(T value);

    public T interpolate(T a, T b, IInterp interpolation, float x);
}