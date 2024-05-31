package mchorse.bbs_mod.utils.keyframes.generic.factories;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.Transform;

import java.util.HashSet;
import java.util.Set;

public class PoseKeyframeFactory implements IGenericKeyframeFactory<Pose>
{
    private static Set<String> keys = new HashSet<>();

    private Pose i = new Pose();

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
    public Pose copy(Pose value)
    {
        return value.copy();
    }

    @Override
    public Pose interpolate(Pose a, Pose b, IInterp interpolation, float x)
    {
        float factor = interpolation.interpolate(0, 1, x);

        keys.clear();

        if (a != null)
        {
            keys.addAll(a.transforms.keySet());
        }

        if (b != null)
        {
            keys.addAll(b.transforms.keySet());
        }

        this.i.copy(a);

        for (String key : keys)
        {
            Transform transform = this.i.get(key);
            Transform t = b.get(key);

            transform.lerp(t, factor);
        }

        return this.i;
    }
}