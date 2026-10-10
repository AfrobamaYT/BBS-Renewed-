package mchorse.bbs_mod.utils.keyframes.factories;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;

import java.util.HashSet;
import java.util.Set;

public class PoseKeyframeFactory implements IKeyframeFactory<Pose>
{
    private static final PoseTransform IDENTITY = new PoseTransform();
    private final ThreadLocal<Set<String>> keys = ThreadLocal.withInitial(HashSet::new);
    private final ThreadLocal<Pose> output = ThreadLocal.withInitial(Pose::new);

    @Override
    public Pose fromData(BaseType data)
    {
        Pose pose = new Pose();

        if (data.isMap())
        {
            pose.fromData(data.asMap());
        }

        return pose;
    }

    @Override
    public BaseType toData(Pose value)
    {
        return value.toData();
    }

    @Override
    public Pose createEmpty()
    {
        return new Pose();
    }

    @Override
    public Pose copy(Pose value)
    {
        return value.copy();
    }

    @Override
    public Pose interpolate(Keyframe<Pose> preA, Keyframe<Pose> a, Keyframe<Pose> b, Keyframe<Pose> postB, IInterp interpolation, float x)
    {
        if (interpolation.has(Interpolations.AUTO) || interpolation.has(Interpolations.AUTO_CLAMPED))
        {
            Pose preAp = preA.getValue();
            Pose ap = a.getValue();
            Pose bp = b.getValue();
            Pose postBp = postB.getValue();

            Set<String> keys = this.collect(preAp, ap, bp, postBp);
            Pose i = this.output.get();

            boolean clamped = interpolation.has(Interpolations.AUTO_CLAMPED);
            float pt = preA.getTick();
            float at = a.getTick();
            float bt = b.getTick();
            float qt = postB.getTick();

            for (String key : keys)
            {
                i.get(key).autoLerp(read(preAp, key), read(ap, key), read(bp, key), read(postBp, key), pt, at, bt, qt, clamped, x);
            }

            return i;
        }

        return IKeyframeFactory.super.interpolate(preA, a, b, postB, interpolation, x);
    }

    @Override
    public Pose interpolate(Pose preA, Pose a, Pose b, Pose postB, IInterp interpolation, float x)
    {
        Set<String> keys = this.collect(preA, a, b, postB);
        Pose i = this.output.get();

        for (String key : keys)
        {
            i.get(key).lerp(read(preA, key), read(a, key), read(b, key), read(postB, key), interpolation, x);
        }

        return i;
    }

    private static PoseTransform read(Pose value, String key)
    {
        /* Missing input bones are identity; evaluating a key must never author them. */
        return value == null ? IDENTITY : value.transforms.getOrDefault(key, IDENTITY);
    }

    private Set<String> collect(Pose preA, Pose a, Pose b, Pose postB)
    {
        Set<String> keys = this.keys.get();
        keys.clear();

        if (preA != a && preA != null) keys.addAll(preA.transforms.keySet());
        if (a != null) keys.addAll(a.transforms.keySet());
        if (b != null) keys.addAll(b.transforms.keySet());
        if (postB != b && postB != null) keys.addAll(postB.transforms.keySet());

        for (PoseTransform value : this.output.get().transforms.values())
        {
            value.identity();
        }

        return keys;
    }
}
