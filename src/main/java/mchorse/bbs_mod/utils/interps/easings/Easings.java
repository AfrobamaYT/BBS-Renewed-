package mchorse.bbs_mod.utils.interps.easings;

public class Easings
{
    public static final IEasing CONST = (x) -> 0;
    public static final IEasing LINEAR = (x) -> x;
    public static final IEasing QUADRATIC = (x) -> x * x;
    public static final IEasing CUBIC = (x) -> x * x * x;
    public static final IEasing QUARTIC = (x) -> x * x * x * x;
    public static final IEasing QUINTIC = (x) -> x * x * x * x * x;
    public static final IEasing EXP = (x) -> Math.pow(2D, 10D * (x - 1D));
    public static final IEasing BACK = (x) ->
    {
        final double c1 = 1.70158D;
        final double c3 = c1 + 1;

        return c3 * x * x * x - c1 * x * x;
    };
    public static final IEasing ELASTIC = Easings::elasticIn;
    public static final IEasing BOUNCE = (x) -> 1D - bounceOut(1D - x);
    public static final IEasing SINE = (x) -> 1D - Math.sin(((1D - x) * Math.PI) / 2);
    public static final IEasing CIRCLE = (x) -> 1D - Math.sqrt(1D - Math.pow(x, 2));

    /* Helper methods */

    public static double remapExp(double x)
    {
        return (x - 0.001D) * (1D / 0.999D);
    }

    public static double elasticIn(double x)
    {
        final double c4 = (2 * Math.PI) / 3;

        return -Math.pow(2, 10 * x - 10) * Math.sin((x * 10 - 10.75) * c4);
    }

    public static double bounceOut(double x)
    {
        final double n1 = 7.5625;
        final double d1 = 2.75;
        double factor;

        if (x < 1 / d1)
        {
            factor = n1 * x * x;
        }
        else if (x < 2 / d1)
        {
            factor = n1 * (x -= 1.5 / d1) * x + 0.75;
        }
        else if (x < 2.5 / d1)
        {
            factor = n1 * (x -= 2.25 / d1) * x + 0.9375;
        }
        else
        {
            factor = n1 * (x -= 2.625 / d1) * x + 0.984375;
        }

        return factor;
    }

    /* Easing factories */

    public static IEasing out(IEasing easing)
    {
        return (x) -> 1D - easing.calculate(1D - x);
    }

    public static IEasing inOut(IEasing easing)
    {
        return (x) ->
        {
            if (x < 0.5D)
            {
                return easing.calculate(x * 2) / 2D;
            }

            double newX = (x - 0.5D) * 2;
            double newY = 1D - easing.calculate(1D - newX);

            return newY / 2D + 0.5D;
        };
    }

    public static IEasing expOut()
    {
        return (x) -> remapExp(1D - EXP.calculate(1D - x));
    }

    public static IEasing expInOut()
    {
        return (x) ->
        {
            if (x < 0.5D)
            {
                return EXP.calculate(x * 2) / 2D;
            }

            double newX = (x - 0.5D) * 2;
            double newY = remapExp(1D - EXP.calculate(1D - newX));

            return newY / 2D + 0.5D;
        };
    }
}