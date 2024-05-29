package mchorse.bbs_mod.cubic.data.animation;

import mchorse.bbs_mod.cubic.MolangHelper;
import mchorse.bbs_mod.utils.Axis;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.math.IInterpolation;
import mchorse.bbs_mod.utils.math.Interpolation;

import java.util.HashMap;
import java.util.Map;

public class AnimationInterpolation
{
    public static final Map<String, IInterpolation> GECKO_LIB_NAMES = new HashMap<>();

    static
    {
        GECKO_LIB_NAMES.put("linear", Interpolation.LINEAR);
        GECKO_LIB_NAMES.put("catmullrom", Interpolation.HERMITE);
        GECKO_LIB_NAMES.put("step", Interpolation.CONST);
        GECKO_LIB_NAMES.put("easeInSine", Interpolation.SINE_IN);
        GECKO_LIB_NAMES.put("easeOutSine", Interpolation.SINE_OUT);
        GECKO_LIB_NAMES.put("easeInOutSine", Interpolation.SINE_INOUT);
        GECKO_LIB_NAMES.put("easeInQuad", Interpolation.QUAD_IN);
        GECKO_LIB_NAMES.put("easeOutQuad", Interpolation.QUAD_OUT);
        GECKO_LIB_NAMES.put("easeInOutQuad", Interpolation.QUAD_INOUT);
        GECKO_LIB_NAMES.put("easeInCubic", Interpolation.CUBIC_IN);
        GECKO_LIB_NAMES.put("easeOutCubic", Interpolation.CUBIC_OUT);
        GECKO_LIB_NAMES.put("easeInOutCubic", Interpolation.CUBIC_INOUT);
        GECKO_LIB_NAMES.put("easeInQuart", Interpolation.QUART_IN);
        GECKO_LIB_NAMES.put("easeOutQuart", Interpolation.QUART_OUT);
        GECKO_LIB_NAMES.put("easeInOutQuart", Interpolation.QUART_INOUT);
        GECKO_LIB_NAMES.put("easeInQuint", Interpolation.QUINT_IN);
        GECKO_LIB_NAMES.put("easeOutQuint", Interpolation.QUINT_OUT);
        GECKO_LIB_NAMES.put("easeInOutQuint", Interpolation.QUINT_INOUT);
        GECKO_LIB_NAMES.put("easeInExpo", Interpolation.EXP_IN);
        GECKO_LIB_NAMES.put("easeOutExpo", Interpolation.EXP_OUT);
        GECKO_LIB_NAMES.put("easeInOutExpo", Interpolation.EXP_INOUT);
        GECKO_LIB_NAMES.put("easeInCirc", Interpolation.CIRCLE_IN);
        GECKO_LIB_NAMES.put("easeOutCirc", Interpolation.CIRCLE_OUT);
        GECKO_LIB_NAMES.put("easeInOutCirc", Interpolation.CIRCLE_INOUT);
        GECKO_LIB_NAMES.put("easeInBack", Interpolation.BACK_IN);
        GECKO_LIB_NAMES.put("easeOutBack", Interpolation.BACK_OUT);
        GECKO_LIB_NAMES.put("easeInOutBack", Interpolation.BACK_INOUT);

        /* These are inverted (i.e. in and out swapped places)
         * because that's how the GeckoLib plugin shows */
        GECKO_LIB_NAMES.put("easeInElastic", Interpolation.ELASTIC_OUT);
        GECKO_LIB_NAMES.put("easeOutElastic", Interpolation.ELASTIC_IN);
        GECKO_LIB_NAMES.put("easeInOutElastic", Interpolation.ELASTIC_INOUT);
        GECKO_LIB_NAMES.put("easeInBounce", Interpolation.BOUNCE_OUT);
        GECKO_LIB_NAMES.put("easeOutBounce", Interpolation.BOUNCE_IN);
        GECKO_LIB_NAMES.put("easeInOutBounce", Interpolation.BOUNCE_INOUT);
    }

    public static IInterpolation byName(String easing)
    {
        return GECKO_LIB_NAMES.getOrDefault(easing, Interpolation.LINEAR);
    }

    public static String toName(IInterpolation interp)
    {
        return CollectionUtils.getKey(GECKO_LIB_NAMES, interp);
    }

    public static double interpolate(AnimationVector vector, MolangHelper.Component component, Axis axis, double factor)
    {
        IInterpolation interpolation = Interpolation.LINEAR;
        double start = MolangHelper.getValue(vector.getStart(axis), component, axis);
        double destination = MolangHelper.getValue(vector.getEnd(axis), component, axis);
        double pre = start;
        double post = destination;

        if (vector.next != null) interpolation = vector.next.interp;
        if (vector.prev != null) pre = MolangHelper.getValue(vector.prev.getStart(axis), component, axis);
        if (vector.next != null) post = MolangHelper.getValue(vector.next.getEnd(axis), component, axis);

        return interpolation.interpolate(IInterpolation.context.set(start, destination, factor).extra(pre, post));
    }
}