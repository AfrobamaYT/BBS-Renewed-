package mchorse.bbs_mod.utils.keyframes.factories;

import mchorse.bbs_mod.cubic.ik.IKControl;
import mchorse.bbs_mod.cubic.ik.IKControls;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

import java.util.HashSet;
import java.util.Set;

public class IKKeyframeFactory implements IKeyframeFactory<IKControls>
{
    private static final IKControl IDENTITY = new IKControl();
    private final ThreadLocal<Set<String>> keys = ThreadLocal.withInitial(HashSet::new);
    private final ThreadLocal<IKControls> output = ThreadLocal.withInitial(IKControls::new);

    @Override
    public IKControls fromData(BaseType data)
    {
        IKControls controls = new IKControls();

        if (data.isMap())
        {
            controls.fromData(data.asMap());
        }

        return controls;
    }

    @Override
    public BaseType toData(IKControls value)
    {
        return value.toData();
    }

    @Override
    public IKControls createEmpty()
    {
        return new IKControls();
    }

    @Override
    public IKControls copy(IKControls value)
    {
        return value.copy();
    }

    @Override
    public IKControls interpolate(Keyframe<IKControls> preA, Keyframe<IKControls> a, Keyframe<IKControls> b, Keyframe<IKControls> postB, IInterp interpolation, float x)
    {
        if (interpolation.has(Interpolations.AUTO) || interpolation.has(Interpolations.AUTO_CLAMPED))
        {
            IKControls preAp = preA.getValue();
            IKControls ap = a.getValue();
            IKControls bp = b.getValue();
            IKControls postBp = postB.getValue();

            Set<String> keys = this.collect(preAp, ap, bp, postBp);
            IKControls i = this.output.get();

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
    public IKControls interpolate(IKControls preA, IKControls a, IKControls b, IKControls postB, IInterp interpolation, float x)
    {
        Set<String> keys = this.collect(preA, a, b, postB);
        IKControls i = this.output.get();

        for (String key : keys)
        {
            i.get(key).lerp(read(preA, key), read(a, key), read(b, key), read(postB, key), interpolation, x);
        }

        return i;
    }

    private static IKControl read(IKControls value, String key)
    {
        return value == null ? IDENTITY : value.controls.getOrDefault(key, IDENTITY);
    }

    private Set<String> collect(IKControls preA, IKControls a, IKControls b, IKControls postB)
    {
        Set<String> keys = this.keys.get();
        keys.clear();

        if (preA != a && preA != null) keys.addAll(preA.controls.keySet());
        if (a != null) keys.addAll(a.controls.keySet());
        if (b != null) keys.addAll(b.controls.keySet());
        if (postB != b && postB != null) keys.addAll(postB.controls.keySet());

        for (IKControl value : this.output.get().controls.values())
        {
            value.identity();
        }

        return keys;
    }
}
