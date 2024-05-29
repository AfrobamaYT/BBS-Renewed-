package mchorse.bbs_mod.utils.math;

public class InterpolationContext
{
    public double a;
    public double b;
    public double x;

    /* Extra variables */
    public double v1;
    public double v2;
    public double v3;
    public double v4;

    public InterpolationContext set(double a, double b, double x)
    {
        this.a = a;
        this.b = b;
        this.x = x;

        this.v1 = a;
        this.v2 = b;

        return this;
    }

    public InterpolationContext extra(double v1, double v2)
    {
        this.v1 = v1;
        this.v2 = v2;

        return this;
    }

    public InterpolationContext extra(double v1, double v2, double v3, double v4)
    {
        this.v1 = v1;
        this.v2 = v2;
        this.v3 = v3;
        this.v4 = v4;

        return this;
    }
}