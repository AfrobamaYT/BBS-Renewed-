package mchorse.bbs_mod.utils.keyframes;

import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.interps.Lerps;

public class BezierUtils
{
    public static double get(double aValue, double bValue, float aTick, float bTick, float aRx, float aRy, float bLx, float bLy, float x)
    {
        if (x <= 0) return aValue;
        if (x >= 1) return bValue;

        /* Transform input to 0..1 */
        double w = bTick - aTick;
        double h = bValue - aValue;

        /* In case if there is no slope whatsoever */
        if (h == 0) h = 0.00001;

        double x1 = aRx / w;
        double y1 = aRy / h;
        double x2 = (w - bLx) / w;
        double y2 = (h + bLy) / h;
        /* Invert the time curve accurately enough to match authored Blender FCurves. X inversion
         * is independent of value-range height; the old height-scaled 5e-4 tolerance produced
         * visible ~4e-4 scalar errors and made the exact same handles evaluate differently. */
        double e = 0.0000001;
        x1 = MathUtils.clamp(x1, 0, 1);
        x2 = MathUtils.clamp(x2, 0, 1);

        return Lerps.bezier(0, y1, y2, 1, Lerps.bezierX(x1, x2, x, e)) * h + aValue;
    }
}
