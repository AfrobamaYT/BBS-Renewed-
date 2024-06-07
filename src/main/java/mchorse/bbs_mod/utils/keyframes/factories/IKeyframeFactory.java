package mchorse.bbs_mod.utils.keyframes.factories;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.utils.interps.IInterp;

public interface IKeyframeFactory <T>
{
    public T fromData(BaseType data);

    public BaseType toData(T value);

    public T createEmpty();

    public T copy(T value);

    public T interpolate(T preA, T a, T b, T postB, IInterp interpolation, float x);

    public default double getY(T value, int index)
    {
        return index;
    }
}