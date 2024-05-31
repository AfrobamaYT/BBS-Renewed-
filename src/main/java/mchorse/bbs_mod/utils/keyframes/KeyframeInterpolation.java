package mchorse.bbs_mod.utils.keyframes;

import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interps;
import mchorse.bbs_mod.utils.interps.Lerps;

import java.util.ArrayList;
import java.util.List;

public class KeyframeInterpolation
{
    public static final List<IInterp> INTERPOLATIONS = new ArrayList<>();

    static
    {
        for (Interps value : Interps.values())
        {
            INTERPOLATIONS.add(value);
        }

        INTERPOLATIONS.remove(Interps.CUBIC);
        INTERPOLATIONS.remove(Interps.CIRCULAR);
    }

    public static double interpolate(Keyframe a, Keyframe b, double x)
    {
        IInterp interp = a.getInterpolation();

        if (interp == Interps.BEZIER)
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

            return Lerps.bezier(0, y1, y2, 1, Lerps.bezierX(x1, x2, x, e)) * h + a.getValue();
        }

        if (interp == null)
        {
            return a.getValue();
        }

        return interp.interpolate(IInterp.context
            .set(a.prev.getValue(), a.getValue(), b.getValue(), b.next.getValue(), x));
    }

    public static boolean isBezier(IInterp interp)
    {
        return interp == Interps.BEZIER;
    }
}
