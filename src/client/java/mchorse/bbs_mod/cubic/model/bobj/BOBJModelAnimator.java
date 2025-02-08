package mchorse.bbs_mod.cubic.model.bobj;

import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.cubic.MolangHelper;
import mchorse.bbs_mod.cubic.data.animation.Animation;
import mchorse.bbs_mod.cubic.data.animation.AnimationChannel;
import mchorse.bbs_mod.cubic.data.animation.AnimationPart;
import mchorse.bbs_mod.cubic.data.animation.AnimationVector;
import mchorse.bbs_mod.math.molang.expressions.MolangExpression;
import mchorse.bbs_mod.utils.Axis;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.interps.Lerps;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Vector3d;

import java.util.List;

public class BOBJModelAnimator
{
    private static Vector3d p = new Vector3d();
    private static Vector3d s = new Vector3d();
    private static Vector3d r = new Vector3d();

    public static double getValue(MolangExpression value, MolangHelper.Component component, Axis axis)
    {
        double out = value.get();

        if (component == MolangHelper.Component.SCALE)
        {
            out = out - 1;
        }

        return out;
    }

    public static double interpolate(AnimationVector vector, MolangHelper.Component component, Axis axis, double factor)
    {
        IInterp interpolation = Interpolations.LINEAR;
        double start = getValue(vector.getStart(axis), component, axis);
        double destination = getValue(vector.getEnd(axis), component, axis);
        double pre = start;
        double post = destination;

        if (vector.next != null) interpolation = vector.next.interp;
        if (vector.interp == Interpolations.CONST) interpolation = vector.interp;

        if (vector.prev != null) pre = getValue(vector.prev.getStart(axis), component, axis);
        if (vector.next != null) post = getValue(vector.next.getEnd(axis), component, axis);

        return interpolation.interpolate(IInterp.context.set(pre, start, destination, post, factor));
    }

    public static Vector3d interpolateList(Vector3d vector, AnimationChannel channel, float frame, MolangHelper.Component component)
    {
        return interpolate(vector, channel, frame, component);
    }

    public static Vector3d interpolate(Vector3d output, AnimationChannel channel, float frame, MolangHelper.Component component)
    {
        List<AnimationVector> keyframes = channel.keyframes;

        if (keyframes.isEmpty())
        {
            output.set(0, 0, 0);

            return output;
        }

        AnimationVector first = keyframes.get(0);

        if (frame < first.time * 20)
        {
            output.x = getValue(first.getStart(Axis.X), component, Axis.X);
            output.y = getValue(first.getStart(Axis.Y), component, Axis.Y);
            output.z = getValue(first.getStart(Axis.Z), component, Axis.Z);

            return output;
        }

        double duration = first.time * 20;

        for (AnimationVector vector : keyframes)
        {
            double length = vector.getLengthInTicks();

            if (frame >= duration && frame < duration + length)
            {
                double factor = (frame - duration) / length;

                output.x = interpolate(vector, component, Axis.X, factor);
                output.y = interpolate(vector, component, Axis.Y, factor);
                output.z = interpolate(vector, component, Axis.Z, factor);

                return output;
            }

            duration += length;
        }

        AnimationVector last = keyframes.get(keyframes.size() - 1);

        output.x = getValue(last.getStart(Axis.X), component, Axis.X);
        output.y = getValue(last.getStart(Axis.Y), component, Axis.Y);
        output.z = getValue(last.getStart(Axis.Z), component, Axis.Z);

        return output;
    }

    public static void animate(BOBJModel model, Animation animation, float frame, float blend, boolean skipInitial)
    {
        for (BOBJBone orderedBone : model.getArmature().orderedBones)
        {
            animateGroup(orderedBone, animation, frame, blend, skipInitial);
        }
    }

    private static void animateGroup(BOBJBone group, Animation animation, float frame, float blend, boolean skipInitial)
    {
        boolean applied = false;

        AnimationPart part = animation.parts.get(group.name);

        if (part != null)
        {
            applyGroupAnimation(group, part, frame, blend);

            applied = true;
        }

        if (!applied && !skipInitial)
        {
            Transform initial = Transform.DEFAULT;
            Transform current = group.transform;

            current.translate.lerp(initial.translate, blend);
            current.scale.lerp(initial.scale, blend);
            current.rotate.lerp(initial.rotate, blend);
        }
    }

    private static void applyGroupAnimation(BOBJBone group, AnimationPart animation, float frame, float blend)
    {
        Vector3d position = interpolateList(p, animation.position, frame, MolangHelper.Component.POSITION);
        Vector3d scale = interpolateList(s, animation.scale, frame, MolangHelper.Component.SCALE);
        Vector3d rotation = interpolateList(r, animation.rotation, frame, MolangHelper.Component.ROTATION);

        Transform initial = Transform.DEFAULT;
        Transform current = group.transform;

        current.translate.x = Lerps.lerp(current.translate.x, (float) position.x + initial.translate.x, blend);
        current.translate.y = Lerps.lerp(current.translate.y, (float) position.y + initial.translate.y, blend);
        current.translate.z = Lerps.lerp(current.translate.z, (float) position.z + initial.translate.z, blend);

        current.scale.x = Lerps.lerp(current.scale.x, (float) scale.x + initial.scale.x, blend);
        current.scale.y = Lerps.lerp(current.scale.y, (float) scale.y + initial.scale.y, blend);
        current.scale.z = Lerps.lerp(current.scale.z, (float) scale.z + initial.scale.z, blend);

        current.rotate.x = Lerps.lerp(current.rotate.x, (float) rotation.x + initial.rotate.x, blend);
        current.rotate.y = Lerps.lerp(current.rotate.y, (float) rotation.y + initial.rotate.y, blend);
        current.rotate.z = Lerps.lerp(current.rotate.z, (float) rotation.z + initial.rotate.z, blend);
    }
}
