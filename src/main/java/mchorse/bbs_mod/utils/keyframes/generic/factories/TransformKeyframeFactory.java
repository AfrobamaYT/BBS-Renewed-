package mchorse.bbs_mod.utils.keyframes.generic.factories;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.pose.Transform;

public class TransformKeyframeFactory implements IGenericKeyframeFactory<Transform>
{
    private Transform i = new Transform();

    @Override
    public Transform fromData(BaseType data)
    {
        Transform transform = new Transform();

        if (data.isMap())
        {
            transform.fromData(data.asMap());
        }

        return transform;
    }

    @Override
    public BaseType toData(Transform value)
    {
        return value.toData();
    }

    @Override
    public Transform copy(Transform value)
    {
        return value.copy();
    }

    @Override
    public Transform interpolate(Transform a, Transform b, IInterp interpolation, float x)
    {
        float factor = interpolation.interpolate(0, 1, x);

        this.i.copy(a);
        this.i.lerp(b, factor);

        return this.i;
    }
}