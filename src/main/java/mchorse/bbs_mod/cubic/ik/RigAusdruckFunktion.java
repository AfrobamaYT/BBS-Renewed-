package mchorse.bbs_mod.cubic.ik;

import java.util.List;

/**
 * Die Funktionen von Blenders einfachem Treiber-Ausdruck ({@code expr_pylike_eval.cc}): min, max,
 * radians, degrees, abs, fabs, floor, ceil, trunc, int, round, sin, cos, tan, asin, acos, atan,
 * atan2, exp, log (1 oder 2 Argumente), sqrt, pow, fmod, lerp, clamp (1 oder 3), smoothstep.
 * {@code null} heisst: Name oder Stelligkeit unbekannt.
 */
final class RigAusdruckFunktion
{
    private RigAusdruckFunktion()
    {}

    private static double a(List<RigAusdruckKnoten> args, int i, double[] vars)
    {
        return args.get(i).wert(vars);
    }

    static RigAusdruckKnoten funktion(String n, List<RigAusdruckKnoten> args)
    {
        int k = args.size();

        if ("min".equals(n) || "max".equals(n))
        {
            if (k == 0)
            {
                return null;
            }

            boolean min = "min".equals(n);

            return vars ->
            {
                double r = a(args, 0, vars);

                for (int i = 1; i < args.size(); i++)
                {
                    double v = a(args, i, vars);

                    r = min ? Math.min(r, v) : Math.max(r, v);
                }

                return r;
            };
        }

        if (k == 1)
        {
            return switch (n)
            {
                case "radians" -> vars -> Math.toRadians(a(args, 0, vars));
                case "degrees" -> vars -> Math.toDegrees(a(args, 0, vars));
                case "abs", "fabs" -> vars -> Math.abs(a(args, 0, vars));
                case "floor" -> vars -> Math.floor(a(args, 0, vars));
                case "ceil" -> vars -> Math.ceil(a(args, 0, vars));
                case "trunc", "int" -> vars -> (double) (long) a(args, 0, vars);
                case "round" -> vars -> Math.rint(a(args, 0, vars));
                case "sin" -> vars -> Math.sin(a(args, 0, vars));
                case "cos" -> vars -> Math.cos(a(args, 0, vars));
                case "tan" -> vars -> Math.tan(a(args, 0, vars));
                case "asin" -> vars -> Math.asin(a(args, 0, vars));
                case "acos" -> vars -> Math.acos(a(args, 0, vars));
                case "atan" -> vars -> Math.atan(a(args, 0, vars));
                case "exp" -> vars -> Math.exp(a(args, 0, vars));
                case "log" -> vars -> Math.log(a(args, 0, vars));
                case "sqrt" -> vars -> Math.sqrt(a(args, 0, vars));
                case "clamp" -> vars -> Math.max(0D, Math.min(1D, a(args, 0, vars)));
                default -> null;
            };
        }

        if (k == 2)
        {
            return switch (n)
            {
                case "atan2" -> vars -> Math.atan2(a(args, 0, vars), a(args, 1, vars));
                case "pow" -> vars -> Math.pow(a(args, 0, vars), a(args, 1, vars));
                case "fmod" -> vars -> a(args, 0, vars) % a(args, 1, vars);
                case "log" -> vars -> Math.log(a(args, 0, vars)) / Math.log(a(args, 1, vars));
                default -> null;
            };
        }

        if (k == 3)
        {
            return switch (n)
            {
                case "lerp" -> vars ->
                {
                    double x = a(args, 0, vars), y = a(args, 1, vars), t = a(args, 2, vars);

                    return x * (1D - t) + y * t;
                };
                case "clamp" -> vars ->
                {
                    double x = a(args, 0, vars), lo = a(args, 1, vars), hi = a(args, 2, vars);

                    return Math.max(lo, Math.min(hi, x));
                };
                case "smoothstep" -> vars ->
                {
                    double e0 = a(args, 0, vars), e1 = a(args, 1, vars), x = a(args, 2, vars);
                    double t = e1 == e0 ? 0D : Math.max(0D, Math.min(1D, (x - e0) / (e1 - e0)));

                    return t * t * (3D - 2D * t);
                };
                default -> null;
            };
        }

        return null;
    }
}
