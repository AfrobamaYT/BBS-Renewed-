package mchorse.bbs_mod.cubic.data.animation;

import mchorse.bbs_mod.cubic.MolangHelper;
import mchorse.bbs_mod.utils.Axis;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interps;

import java.util.HashMap;
import java.util.Map;

public class AnimationInterpolation
{
    public static final Map<String, IInterp> GECKO_LIB_NAMES = new HashMap<>();

    static
    {
        GECKO_LIB_NAMES.put("linear", Interps.LINEAR);
        GECKO_LIB_NAMES.put("catmullrom", Interps.HERMITE);
        GECKO_LIB_NAMES.put("step", Interps.CONST);
        GECKO_LIB_NAMES.put("easeInSine", Interps.SINE_IN);
        GECKO_LIB_NAMES.put("easeOutSine", Interps.SINE_OUT);
        GECKO_LIB_NAMES.put("easeInOutSine", Interps.SINE_INOUT);
        GECKO_LIB_NAMES.put("easeInQuad", Interps.QUAD_IN);
        GECKO_LIB_NAMES.put("easeOutQuad", Interps.QUAD_OUT);
        GECKO_LIB_NAMES.put("easeInOutQuad", Interps.QUAD_INOUT);
        GECKO_LIB_NAMES.put("easeInCubic", Interps.CUBIC_IN);
        GECKO_LIB_NAMES.put("easeOutCubic", Interps.CUBIC_OUT);
        GECKO_LIB_NAMES.put("easeInOutCubic", Interps.CUBIC_INOUT);
        GECKO_LIB_NAMES.put("easeInQuart", Interps.QUART_IN);
        GECKO_LIB_NAMES.put("easeOutQuart", Interps.QUART_OUT);
        GECKO_LIB_NAMES.put("easeInOutQuart", Interps.QUART_INOUT);
        GECKO_LIB_NAMES.put("easeInQuint", Interps.QUINT_IN);
        GECKO_LIB_NAMES.put("easeOutQuint", Interps.QUINT_OUT);
        GECKO_LIB_NAMES.put("easeInOutQuint", Interps.QUINT_INOUT);
        GECKO_LIB_NAMES.put("easeInExpo", Interps.EXP_IN);
        GECKO_LIB_NAMES.put("easeOutExpo", Interps.EXP_OUT);
        GECKO_LIB_NAMES.put("easeInOutExpo", Interps.EXP_INOUT);
        GECKO_LIB_NAMES.put("easeInCirc", Interps.CIRCLE_IN);
        GECKO_LIB_NAMES.put("easeOutCirc", Interps.CIRCLE_OUT);
        GECKO_LIB_NAMES.put("easeInOutCirc", Interps.CIRCLE_INOUT);
        GECKO_LIB_NAMES.put("easeInBack", Interps.BACK_IN);
        GECKO_LIB_NAMES.put("easeOutBack", Interps.BACK_OUT);
        GECKO_LIB_NAMES.put("easeInOutBack", Interps.BACK_INOUT);

        /* These are inverted (i.e. in and out swapped places)
         * because that's how the GeckoLib plugin shows */
        GECKO_LIB_NAMES.put("easeInElastic", Interps.ELASTIC_OUT);
        GECKO_LIB_NAMES.put("easeOutElastic", Interps.ELASTIC_IN);
        GECKO_LIB_NAMES.put("easeInOutElastic", Interps.ELASTIC_INOUT);
        GECKO_LIB_NAMES.put("easeInBounce", Interps.BOUNCE_OUT);
        GECKO_LIB_NAMES.put("easeOutBounce", Interps.BOUNCE_IN);
        GECKO_LIB_NAMES.put("easeInOutBounce", Interps.BOUNCE_INOUT);
    }

    public static IInterp byName(String easing)
    {
        return GECKO_LIB_NAMES.getOrDefault(easing, Interps.LINEAR);
    }

    public static String toName(IInterp interp)
    {
        return CollectionUtils.getKey(GECKO_LIB_NAMES, interp);
    }

    public static double interpolate(AnimationVector vector, MolangHelper.Component component, Axis axis, double factor)
    {
        IInterp interpolation = Interps.LINEAR;
        double start = MolangHelper.getValue(vector.getStart(axis), component, axis);
        double destination = MolangHelper.getValue(vector.getEnd(axis), component, axis);
        double pre = start;
        double post = destination;

        if (vector.next != null) interpolation = vector.next.interp;
        if (vector.prev != null) pre = MolangHelper.getValue(vector.prev.getStart(axis), component, axis);
        if (vector.next != null) post = MolangHelper.getValue(vector.next.getEnd(axis), component, axis);

        return interpolation.interpolate(IInterp.context.set(pre, start, destination, post, factor));
    }
}