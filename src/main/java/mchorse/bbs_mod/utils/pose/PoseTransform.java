package mchorse.bbs_mod.utils.pose;

import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.interps.AutoBezier;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Lerps;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;

public class PoseTransform extends Transform
{
    private static PoseTransform DEFAULT = new PoseTransform();

    public float fix;
    public final Color color = new Color().set(Colors.WHITE);
    public float lighting;
    /** Keyframeable custom properties of this bone, not shared model state. */
    public final Map<String, Float> rigProperties = new HashMap<>();

    @Override
    public void identity()
    {
        super.identity();

        this.fix = 0F;
        this.color.set(Colors.WHITE);
        this.lighting = 0F;
        this.rigProperties.clear();
    }

    @Override
    public void lerp(Transform transform, float a)
    {
        if (transform instanceof PoseTransform pose)
        {
            this.fix = Lerps.lerp(this.fix, pose.fix, a);

            this.color.r = Lerps.lerp(this.color.r, pose.color.r, a);
            this.color.g = Lerps.lerp(this.color.g, pose.color.g, a);
            this.color.b = Lerps.lerp(this.color.b, pose.color.b, a);
            this.color.a = Lerps.lerp(this.color.a, pose.color.a, a);

            this.lighting = Lerps.lerp(this.lighting, pose.lighting, a);
            this.blendRig(this, pose,
                key -> Lerps.lerp(rigValue(this, key, pose), rigValue(pose, key, this), a));
        }

        super.lerp(transform, a);
    }

    @Override
    public void lerp(Transform preA, Transform a, Transform b, Transform postB, IInterp interp, float x)
    {
        super.lerp(preA, a, b, postB, interp, x);

        if (preA instanceof PoseTransform preA1)
        {
            PoseTransform a1 = (PoseTransform) a;
            PoseTransform b1 = (PoseTransform) b;
            PoseTransform postB1 = (PoseTransform) postB;

            this.fix = (float) interp.interpolate(IInterp.context.set(preA1.fix, a1.fix, b1.fix, postB1.fix, x));

            this.color.set(
                (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(preA1.color.r, a1.color.r, b1.color.r, postB1.color.r, x)), 0F, 1F),
                (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(preA1.color.g, a1.color.g, b1.color.g, postB1.color.g, x)), 0F, 1F),
                (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(preA1.color.b, a1.color.b, b1.color.b, postB1.color.b, x)), 0F, 1F),
                (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(preA1.color.a, a1.color.a, b1.color.a, postB1.color.a, x)), 0F, 1F)
            );

            this.lighting = (float) interp.interpolate(IInterp.context.set(preA1.lighting, a1.lighting, b1.lighting, postB1.lighting, x));
            this.blendRig(a1, b1, key -> interp.interpolate(IInterp.context.set(
                rigValue(preA1, key, a1), rigValue(a1, key, b1),
                rigValue(b1, key, a1), rigValue(postB1, key, b1), x)));
        }
    }

    @Override
    public void autoLerp(Transform preA, Transform a, Transform b, Transform postB, float pt, float at, float bt, float qt, boolean clamped, float x)
    {
        super.autoLerp(preA, a, b, postB, pt, at, bt, qt, clamped, x);

        if (preA instanceof PoseTransform preA1)
        {
            PoseTransform a1 = (PoseTransform) a;
            PoseTransform b1 = (PoseTransform) b;
            PoseTransform postB1 = (PoseTransform) postB;

            this.fix = (float) AutoBezier.get(preA1.fix, a1.fix, b1.fix, postB1.fix, pt, at, bt, qt, clamped, x);

            this.color.set(
                (float) MathUtils.clamp(AutoBezier.get(preA1.color.r, a1.color.r, b1.color.r, postB1.color.r, pt, at, bt, qt, clamped, x), 0F, 1F),
                (float) MathUtils.clamp(AutoBezier.get(preA1.color.g, a1.color.g, b1.color.g, postB1.color.g, pt, at, bt, qt, clamped, x), 0F, 1F),
                (float) MathUtils.clamp(AutoBezier.get(preA1.color.b, a1.color.b, b1.color.b, postB1.color.b, pt, at, bt, qt, clamped, x), 0F, 1F),
                (float) MathUtils.clamp(AutoBezier.get(preA1.color.a, a1.color.a, b1.color.a, postB1.color.a, pt, at, bt, qt, clamped, x), 0F, 1F)
            );

            this.lighting = (float) AutoBezier.get(preA1.lighting, a1.lighting, b1.lighting, postB1.lighting, pt, at, bt, qt, clamped, x);
            this.blendRig(a1, b1, key -> AutoBezier.get(
                rigValue(preA1, key, a1), rigValue(a1, key, b1),
                rigValue(b1, key, a1), rigValue(postB1, key, b1), pt, at, bt, qt, clamped, x));
        }
    }

    @Override
    public boolean equals(Object obj)
    {
        boolean result = super.equals(obj);

        if (obj instanceof PoseTransform poseTransform)
        {
            result = result && this.fix == poseTransform.fix;
            result = result && this.color.equals(poseTransform.color);
            result = result && this.lighting == poseTransform.lighting;
            result = result && this.rigProperties.equals(poseTransform.rigProperties);
        }

        return result;
    }

    @Override
    public Transform copy()
    {
        PoseTransform transform = new PoseTransform();

        transform.copy(this);

        return transform;
    }

    @Override
    public void copy(Transform transform)
    {
        if (transform == this)
        {
            return;
        }

        this.rigProperties.clear();
        if (transform instanceof PoseTransform poseTransform)
        {
            this.fix = poseTransform.fix;
            this.color.copy(poseTransform.color);
            this.lighting = poseTransform.lighting;
            this.rigProperties.putAll(poseTransform.rigProperties);
        }

        super.copy(transform);
    }

    @Override
    public void add(Transform transform)
    {
        super.add(transform);

        if (transform instanceof PoseTransform pose)
        {
            this.fix += pose.fix;
            this.color.mul(pose.color);
            this.lighting += pose.lighting;
            this.rigProperties.putAll(pose.rigProperties);
        }
    }

    @Override
    public void toData(MapType data)
    {
        super.toData(data);

        data.putFloat("fix", this.fix);
        data.putInt("color", this.color.getARGBColor());
        data.putFloat("lighting", this.lighting);
        if (!this.rigProperties.isEmpty())
        {
            MapType properties = new MapType();
            this.rigProperties.forEach(properties::putFloat);
            data.put("rig", properties);
        }
    }

    @Override
    public void fromData(MapType data)
    {
        super.fromData(data);

        this.fix = data.getFloat("fix");
        this.color.set(data.getInt("color", Colors.WHITE));
        this.lighting = data.getFloat("lighting");
        this.rigProperties.clear();
        MapType properties = data.getMap("rig");

        for (String name : properties.keys())
        {
            this.rigProperties.put(name, properties.getFloat(name));
        }
    }

    private static float rigValue(PoseTransform pose, String key, PoseTransform fallback)
    {
        return pose.rigProperties.getOrDefault(key, fallback.rigProperties.getOrDefault(key, 0F));
    }

    private void blendRig(PoseTransform a, PoseTransform b, ToDoubleFunction<String> interpolation)
    {
        Set<String> keys = new HashSet<>(a.rigProperties.keySet());
        keys.addAll(b.rigProperties.keySet());
        Map<String, Float> result = new HashMap<>();

        for (String key : keys)
        {
            result.put(key, (float) interpolation.applyAsDouble(key));
        }

        /* Compute before clearing: this may alias one of the inputs. */
        this.rigProperties.clear();
        this.rigProperties.putAll(result);
    }

    @Override
    public boolean isDefault()
    {
        return this.equals(DEFAULT);
    }
}
