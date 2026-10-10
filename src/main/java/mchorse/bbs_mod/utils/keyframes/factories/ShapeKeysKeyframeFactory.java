package mchorse.bbs_mod.utils.keyframes.factories;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.obj.shapes.ShapeKeys;
import mchorse.bbs_mod.utils.interps.AutoBezier;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.BezierUtils;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

import java.util.HashSet;
import java.util.Set;

public class ShapeKeysKeyframeFactory implements IKeyframeFactory<ShapeKeys>
{
    private final ThreadLocal<Set<String>> keys = ThreadLocal.withInitial(HashSet::new);
    private final ThreadLocal<ShapeKeys> output = ThreadLocal.withInitial(ShapeKeys::new);

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
    public ShapeKeys interpolate(Keyframe<ShapeKeys> preA, Keyframe<ShapeKeys> a,
        Keyframe<ShapeKeys> b, Keyframe<ShapeKeys> postB, IInterp interpolation, float x)
    {
        ShapeKeys i = this.output.get();
        Set<String> keys = this.keys.get();
        i.shapeKeys.clear();
        keys.clear();

        addKeys(keys, preA.getValue());
        addKeys(keys, a.getValue());
        addKeys(keys, b.getValue());
        addKeys(keys, postB.getValue());

        for (String key : keys)
        {
            float before = value(preA.getValue(), key);
            float first = value(a.getValue(), key);
            float second = value(b.getValue(), key);
            float after = value(postB.getValue(), key);
            float result;

            if (interpolation.has(Interpolations.BEZIER))
            {
                result = (float) BezierUtils.get(first, second, a.getTick(), b.getTick(),
                    a.rx, a.ry, b.lx, b.ly, x);
            }
            else if (interpolation.has(Interpolations.AUTO)
                || interpolation.has(Interpolations.AUTO_CLAMPED))
            {
                result = (float) AutoBezier.get(before, first, second, after,
                    preA.getTick(), a.getTick(), b.getTick(), postB.getTick(),
                    interpolation.has(Interpolations.AUTO_CLAMPED), x);
            }
            else
            {
                result = (float) interpolation.interpolate(
                    IInterp.context.set(before, first, second, after, x));
            }

            i.shapeKeys.put(key, result);
        }

        return i;
    }

    @Override
    public ShapeKeys interpolate(ShapeKeys preA, ShapeKeys a, ShapeKeys b, ShapeKeys postB, IInterp interpolation, float x)
    {
        ShapeKeys i = this.output.get();
        Set<String> keys = this.keys.get();
        i.shapeKeys.clear();

        keys.clear();

        if (preA != a && preA != null) keys.addAll(preA.shapeKeys.keySet());
        if (a != null) keys.addAll(a.shapeKeys.keySet());
        if (b != null) keys.addAll(b.shapeKeys.keySet());
        if (postB != b && postB != null) keys.addAll(postB.shapeKeys.keySet());

        for (String key : keys)
        {
            Float fpreA = preA.shapeKeys.get(key);
            Float fa = a.shapeKeys.get(key);
            Float fb = b.shapeKeys.get(key);
            Float fpostB = postB.shapeKeys.get(key);

            i.shapeKeys.put(key, (float) interpolation.interpolate(
                IInterp.context.set(
                    fpreA == null ? 0F : fpreA,
                    fa == null ? 0F : fa,
                    fb == null ? 0F : fb,
                    fpostB == null ? 0F : fpostB, x
                )
            ));
        }

        return i;
    }

    private static void addKeys(Set<String> keys, ShapeKeys shapes)
    {
        if (shapes != null)
        {
            keys.addAll(shapes.shapeKeys.keySet());
        }
    }

    private static float value(ShapeKeys shapes, String key)
    {
        return shapes == null ? 0F : shapes.shapeKeys.getOrDefault(key, 0F);
    }
}
