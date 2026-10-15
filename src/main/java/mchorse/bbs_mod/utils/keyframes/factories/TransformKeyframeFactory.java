package mchorse.bbs_mod.utils.keyframes.factories;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.pose.Transform;

public class TransformKeyframeFactory implements IKeyframeFactory<Transform>
{
    private final ThreadLocal<Transform> output = ThreadLocal.withInitial(Transform::new);

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
    public Transform createEmpty()
    {
        return new Transform();
    }

    @Override
    public Transform copy(Transform value)
    {
        return value.copy();
    }

    @Override
    public Transform interpolate(Keyframe<Transform> preA, Keyframe<Transform> a, Keyframe<Transform> b, Keyframe<Transform> postB, IInterp interpolation, float x)
    {
        if (interpolation.has(Interpolations.AUTO) || interpolation.has(Interpolations.AUTO_CLAMPED))
        {
            Transform i = this.output.get();
            i.autoLerp(
                preA.getValue(), a.getValue(), b.getValue(), postB.getValue(),
                preA.getTick(), a.getTick(), b.getTick(), postB.getTick(),
                interpolation.has(Interpolations.AUTO_CLAMPED), x
            );

            return i;
        }

        return IKeyframeFactory.super.interpolate(preA, a, b, postB, interpolation, x);
    }

    @Override
    public Transform interpolate(Transform preA, Transform a, Transform b, Transform postB, IInterp interpolation, float x)
    {
        Transform i = this.output.get();
        i.lerp(preA, a, b, postB, interpolation, x);

        return i;
    }
}
