package mchorse.bbs_mod.utils.math;

public interface IInterpolation
{
    public static final InterpolationContext context = new InterpolationContext();

    public default float interpolate(float a, float b, float x)
    {
        return (float) this.interpolate(context.set(a, b, x));
    }

    public default double interpolate(double a, double b, double x)
    {
        return this.interpolate(context.set(a, b, x));
    }

    public double interpolate(InterpolationContext context);

    public String getKey();

    public int getKeyCode();
}
