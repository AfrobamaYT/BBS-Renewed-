package mchorse.bbs_mod.utils.keyframes;

import mchorse.bbs_mod.utils.math.IInterpolation;
import mchorse.bbs_mod.utils.math.Interpolation;
import mchorse.bbs_mod.utils.math.Interpolations;
import mchorse.bbs_mod.utils.math.MathUtils;

import java.util.ArrayList;
import java.util.List;

public class KeyframeInterpolation
{
    public static final List<IInterpolation> INTERPOLATIONS = new ArrayList<>();

    static
    {
        for (Interpolation value : Interpolation.values())
        {
            INTERPOLATIONS.add(value);
        }

        INTERPOLATIONS.remove(Interpolation.CUBIC);
        INTERPOLATIONS.remove(Interpolation.CIRCULAR);
    }

    public static double interpolateK(Keyframe a, Keyframe b, double x)
    {
        IInterpolation interp = a.getInterpolation();

        if (interp == Interpolation.BEZIER)
        {
            if (x <= 0) return a.getValue();
            if (x >= 1) return b.getValue();

            /* Transform input to 0..1 */
            double w = b.getTick() - a.getTick();
            double h = b.getValue() - a.getValue();

            /* In case if there is no slope whatsoever */
            if (h == 0) h = 0.00001;

            double x1 = a.getRx() / w;
            double y1 = a.getRy() / h;
            double x2 = (w - b.getLx()) / w;
            double y2 = (h + b.getLy()) / h;
            double e = 0.0005;

            e = h == 0 ? e : Math.max(Math.min(e, 1 / h * e), 0.00001);
            x1 = MathUtils.clamp(x1, 0, 1);
            x2 = MathUtils.clamp(x2, 0, 1);

            return Interpolations.bezier(0, y1, y2, 1, Interpolations.bezierX(x1, x2, x, e)) * h + a.getValue();
        }

        if (interp == null)
        {
            return a.getValue();
        }

        return interp.interpolate(IInterpolation.context
            .set(a.getValue(), b.getValue(), x)
            .extra(a.prev.getValue(), b.next.getValue()));
    }

    public static boolean isBezier(IInterpolation interp)
    {
        return interp == Interpolation.BEZIER;
    }
}
