package mchorse.bbs_mod.utils.keyframes.generic.factories;

import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.utils.interps.IInterp;
import org.joml.Vector4f;

public class Vector4fKeyframeFactory implements IGenericKeyframeFactory<Vector4f>
{
    private Vector4f i = new Vector4f();

    @Override
    public Vector4f fromData(BaseType data)
    {
        return data.isList() ? DataStorageUtils.vector4fFromData(data.asList()) : new Vector4f();
    }

    @Override
    public BaseType toData(Vector4f value)
    {
        return DataStorageUtils.vector4fToData(value);
    }

    @Override
    public Vector4f copy(Vector4f value)
    {
        return new Vector4f(value);
    }

    @Override
    public Vector4f interpolate(Vector4f a, Vector4f b, IInterp interpolation, float x)
    {
        float factor = interpolation.interpolate(0, 1, x);

        a.lerp(b, factor, this.i);

        return this.i;
    }
}