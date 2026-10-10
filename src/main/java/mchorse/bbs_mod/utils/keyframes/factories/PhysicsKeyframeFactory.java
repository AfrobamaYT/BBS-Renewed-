package mchorse.bbs_mod.utils.keyframes.factories;

import mchorse.bbs_mod.cubic.physics.PhysicsControl;
import mchorse.bbs_mod.cubic.physics.PhysicsControls;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

import java.util.HashSet;
import java.util.Set;

public class PhysicsKeyframeFactory implements IKeyframeFactory<PhysicsControls>
{
    private final ThreadLocal<Set<String>> keys = ThreadLocal.withInitial(HashSet::new);
    private final ThreadLocal<PhysicsControls> output = ThreadLocal.withInitial(PhysicsControls::new);

    @Override
    public PhysicsControls fromData(BaseType data)
    {
        PhysicsControls controls = new PhysicsControls();

        if (data.isMap())
        {
            controls.fromData(data.asMap());
        }

        return controls;
    }

    @Override
    public BaseType toData(PhysicsControls value)
    {
        return value.toData();
    }

    @Override
    public PhysicsControls createEmpty()
    {
        return new PhysicsControls();
    }

    @Override
    public PhysicsControls copy(PhysicsControls value)
    {
        return value.copy();
    }

    @Override
    public PhysicsControls interpolate(Keyframe<PhysicsControls> preA, Keyframe<PhysicsControls> a, Keyframe<PhysicsControls> b, Keyframe<PhysicsControls> postB, IInterp interpolation, float x)
    {
        if (interpolation.has(Interpolations.AUTO) || interpolation.has(Interpolations.AUTO_CLAMPED))
        {
            PhysicsControls preAp = preA.getValue();
            PhysicsControls ap = a.getValue();
            PhysicsControls bp = b.getValue();
            PhysicsControls postBp = postB.getValue();

            Set<String> keys = this.collect(preAp, ap, bp, postBp);
            PhysicsControls i = this.output.get();

            boolean clamped = interpolation.has(Interpolations.AUTO_CLAMPED);
            float pt = preA.getTick();
            float at = a.getTick();
            float bt = b.getTick();
            float qt = postB.getTick();

            for (String key : keys)
            {
                i.get(key).autoLerp(preAp.get(key), ap.get(key), bp.get(key), postBp.get(key), pt, at, bt, qt, clamped, x);
            }

            return i;
        }

        return IKeyframeFactory.super.interpolate(preA, a, b, postB, interpolation, x);
    }

    @Override
    public PhysicsControls interpolate(PhysicsControls preA, PhysicsControls a, PhysicsControls b, PhysicsControls postB, IInterp interpolation, float x)
    {
        Set<String> keys = this.collect(preA, a, b, postB);
        PhysicsControls i = this.output.get();

        for (String key : keys)
        {
            i.get(key).lerp(preA.get(key), a.get(key), b.get(key), postB.get(key), interpolation, x);
        }

        return i;
    }

    private Set<String> collect(PhysicsControls preA, PhysicsControls a, PhysicsControls b, PhysicsControls postB)
    {
        Set<String> keys = this.keys.get();
        keys.clear();

        if (preA != a && preA != null) keys.addAll(preA.controls.keySet());
        if (a != null) keys.addAll(a.controls.keySet());
        if (b != null) keys.addAll(b.controls.keySet());
        if (postB != b && postB != null) keys.addAll(postB.controls.keySet());

        for (PhysicsControl value : this.output.get().controls.values())
        {
            value.identity();
        }

        return keys;
    }
}
