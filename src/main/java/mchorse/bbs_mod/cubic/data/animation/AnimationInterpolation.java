package mchorse.bbs_mod.cubic.data.animation;

import mchorse.bbs_mod.cubic.MolangHelper;
import mchorse.bbs_mod.utils.Axis;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.keyframes.KeyframeInterpolations;
import mchorse.bbs_mod.utils.math.IInterpolation;
import mchorse.bbs_mod.utils.math.Interpolation;
import mchorse.bbs_mod.utils.math.Interpolations;

import java.util.HashMap;
import java.util.Map;

public class AnimationInterpolation
{
    public static final Map<String, IInterpolation> geckoLibNames = new HashMap<>();

    static
    {
        geckoLibNames.put("linear", Interpolation.LINEAR);
        geckoLibNames.put("catmullrom", KeyframeInterpolations.HERMITE);
        geckoLibNames.put("step", KeyframeInterpolations.CONSTANT);
        geckoLibNames.put("easeInSine", Interpolation.SINE_IN);
        geckoLibNames.put("easeOutSine", Interpolation.SINE_OUT);
        geckoLibNames.put("easeInOutSine", Interpolation.SINE_INOUT);
        geckoLibNames.put("easeInQuad", Interpolation.QUAD_IN);
        geckoLibNames.put("easeOutQuad", Interpolation.QUAD_OUT);
        geckoLibNames.put("easeInOutQuad", Interpolation.QUAD_INOUT);
        geckoLibNames.put("easeInCubic", Interpolation.CUBIC_IN);
        geckoLibNames.put("easeOutCubic", Interpolation.CUBIC_OUT);
        geckoLibNames.put("easeInOutCubic", Interpolation.CUBIC_INOUT);
        geckoLibNames.put("easeInQuart", Interpolation.QUART_IN);
        geckoLibNames.put("easeOutQuart", Interpolation.QUART_OUT);
        geckoLibNames.put("easeInOutQuart", Interpolation.QUART_INOUT);
        geckoLibNames.put("easeInQuint", Interpolation.QUINT_IN);
        geckoLibNames.put("easeOutQuint", Interpolation.QUINT_OUT);
        geckoLibNames.put("easeInOutQuint", Interpolation.QUINT_INOUT);
        geckoLibNames.put("easeInExpo", Interpolation.EXP_IN);
        geckoLibNames.put("easeOutExpo", Interpolation.EXP_OUT);
        geckoLibNames.put("easeInOutExpo", Interpolation.EXP_INOUT);
        geckoLibNames.put("easeInCirc", Interpolation.CIRCLE_IN);
        geckoLibNames.put("easeOutCirc", Interpolation.CIRCLE_OUT);
        geckoLibNames.put("easeInOutCirc", Interpolation.CIRCLE_INOUT);
        geckoLibNames.put("easeInBack", Interpolation.BACK_IN);
        geckoLibNames.put("easeOutBack", Interpolation.BACK_OUT);
        geckoLibNames.put("easeInOutBack", Interpolation.BACK_INOUT);
        /* These are inverted (i.e. in and out swapped places)
         * because that's how the GeckoLib plugin shows */
        geckoLibNames.put("easeInElastic", Interpolation.ELASTIC_OUT);
        geckoLibNames.put("easeOutElastic", Interpolation.ELASTIC_IN);
        geckoLibNames.put("easeInOutElastic", Interpolation.ELASTIC_INOUT);
        geckoLibNames.put("easeInBounce", Interpolation.BOUNCE_OUT);
        geckoLibNames.put("easeOutBounce", Interpolation.BOUNCE_IN);
        geckoLibNames.put("easeInOutBounce", Interpolation.BOUNCE_INOUT);
    }

    private static double interpolateHermite(AnimationVector vector, MolangHelper.Component component, Axis axis, double factor)
    {
        double start = MolangHelper.getValue(vector.getStart(axis), component, axis);
        double destination = MolangHelper.getValue(vector.getEnd(axis), component, axis);

        double pre = start;
        double post = destination;

        if (vector.prev != null)
        {
            pre = MolangHelper.getValue(vector.prev.getStart(axis), component, axis);
        }

        if (vector.next != null)
        {
            post = MolangHelper.getValue(vector.next.getEnd(axis), component, axis);
        }

        return Interpolations.cubicHermite(pre, start, destination, post, factor);
    }

    public static IInterpolation byName(String easing)
    {
        return geckoLibNames.getOrDefault(easing, Interpolation.LINEAR);
    }

    public static double interpolate(AnimationVector vector, MolangHelper.Component component, Axis axis, double factor)
    {
        if (vector.next != null)
        {
            if (vector.next.interp == KeyframeInterpolations.HERMITE)
            {
                return interpolateHermite(vector, component, axis, factor);
            }

            factor = vector.next.interp.interpolate(0, 1, factor);
        }

        double start = MolangHelper.getValue(vector.getStart(axis), component, axis);
        double destination = MolangHelper.getValue(vector.getEnd(axis), component, axis);

        return Interpolations.lerp(start, destination, factor);
    }

    public static String toName(IInterpolation interp)
    {
        return CollectionUtils.getKey(geckoLibNames, interp);
    }
}