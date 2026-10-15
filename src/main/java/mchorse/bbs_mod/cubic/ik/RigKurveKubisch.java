package mchorse.bbs_mod.cubic.ik;

/**
 * Blenders kubische Loesung fuer BEZIER-Stuetzpunkte: {@code findzero} (Nullstellen von B(t) - x),
 * {@code solve_cubic} aus {@code math_solvers.cc} mit Blenders Toleranzen und {@code berekeny}.
 */
final class RigKurveKubisch
{
    private RigKurveKubisch()
    {}

    /** {@code findzero}: Nullstellen von B(t) - x im Bereich 0..1, Blenders {@code solve_cubic}. */
    static int nullstellen(float x, float q0, float q1, float q2, float q3, double[] o)
    {
        double c0 = q0 - x;
        double c1 = 3D * (q1 - q0);
        double c2 = 3D * (q0 - 2D * q1 + q2);
        double c3 = q3 - q0 + 3D * (q1 - q2);

        return loeseKubisch(c0, c1, c2, c3, o);
    }

    /** Blenders {@code solve_cubic} aus {@code math_solvers.cc} — Loesungen in [0,1] mit Toleranz. */
    static int loeseKubisch(double c0, double c1, double c2, double c3, double[] o)
    {
        final double SMALL = -1.0E-10D;
        int nr = 0;

        if (c3 != 0D)
        {
            double a = c2 / c3;
            double b = c1 / c3;
            double c = c0 / c3;
            a = a / 3D;
            double p = b / 3D - a * a;
            double q = (2D * a * a * a - a * b + c) / 2D;
            double d = q * q + p * p * p;

            if (d > 0D)
            {
                double t = Math.sqrt(d);

                o[0] = Math.cbrt(-q + t) + Math.cbrt(-q - t) - a;

                if (o[0] >= SMALL && o[0] <= 1.000001D)
                {
                    return 1;
                }

                return 0;
            }

            if (d == 0D)
            {
                double t = Math.cbrt(-q);

                o[0] = 2D * t - a;

                if (o[0] >= SMALL && o[0] <= 1.000001D)
                {
                    nr++;
                }

                o[nr] = -t - a;

                if (o[nr] >= SMALL && o[nr] <= 1.000001D)
                {
                    return nr + 1;
                }

                return nr;
            }

            double phi = Math.acos(-q / Math.sqrt(-(p * p * p)));
            double t = Math.sqrt(-p);
            p = Math.cos(phi / 3D);
            q = Math.sqrt(3D - 3D * p * p);

            o[0] = 2D * t * p - a;

            if (o[0] >= SMALL && o[0] <= 1.000001D)
            {
                nr++;
            }

            o[nr] = -t * (p + q) - a;

            if (o[nr] >= SMALL && o[nr] <= 1.000001D)
            {
                nr++;
            }

            o[nr] = -t * (p - q) - a;

            if (o[nr] >= SMALL && o[nr] <= 1.000001D)
            {
                return nr + 1;
            }

            return nr;
        }

        double a = c2;
        double b = c1;
        double c = c0;

        if (a != 0D)
        {
            double p = b * b - 4D * a * c;

            if (p > 0D)
            {
                p = Math.sqrt(p);
                o[0] = (-b - p) / (2D * a);

                if (o[0] >= SMALL && o[0] <= 1.000001D)
                {
                    nr++;
                }

                o[nr] = (-b + p) / (2D * a);

                if (o[nr] >= SMALL && o[nr] <= 1.000001D)
                {
                    return nr + 1;
                }

                return nr;
            }

            if (p == 0D)
            {
                o[0] = -b / (2D * a);

                if (o[0] >= SMALL && o[0] <= 1.000001D)
                {
                    return 1;
                }
            }

            return 0;
        }

        if (b != 0D)
        {
            o[0] = -c / b;

            if (o[0] >= SMALL && o[0] <= 1.000001D)
            {
                return 1;
            }

            return 0;
        }

        if (c == 0D)
        {
            o[0] = 0D;

            return 1;
        }

        return 0;
    }

    /** {@code berekeny} fuer einen Parameter. */
    static double bezierWert(float f1, float f2, float f3, float f4, double t)
    {
        double c0 = f1;
        double c1 = 3D * (f2 - f1);
        double c2 = 3D * (f1 - 2D * f2 + f3);
        double c3 = f4 - f1 + 3D * (f2 - f3);

        return c0 + t * c1 + t * t * c2 + t * t * t * c3;
    }
}
