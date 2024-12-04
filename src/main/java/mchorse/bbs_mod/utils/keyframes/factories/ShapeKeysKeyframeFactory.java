package mchorse.bbs_mod.utils.keyframes.factories;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.obj.shapes.ShapeKeys;
import mchorse.bbs_mod.utils.interps.IInterp;

public class ShapeKeysKeyframeFactory implements IKeyframeFactory<ShapeKeys>
{
    @Override
    public ShapeKeys fromData(BaseType data)
    {
        ShapeKeys keys = new ShapeKeys();

        keys.fromData(data.asMap());

        return keys;
    }

    @Override
    public BaseType toData(ShapeKeys value)
    {
        return value.toData();
    }

    @Override
    public ShapeKeys createEmpty()
    {
        return new ShapeKeys();
    }

    @Override
    public ShapeKeys copy(ShapeKeys value)
    {
        return value.copy();
    }

    @Override
    public ShapeKeys interpolate(ShapeKeys preA, ShapeKeys a, ShapeKeys b, ShapeKeys postB, IInterp interpolation, float x)
    {
        return a;
    }
}