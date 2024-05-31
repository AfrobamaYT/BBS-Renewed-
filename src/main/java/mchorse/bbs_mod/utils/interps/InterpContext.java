package mchorse.bbs_mod.utils.interps;

public class InterpContext
{
    public double a;
    public double b;
    public double x;

    /* Hermite */
    public double a0;
    public double b0;

    /* Extra variables */
    public double v1;
    public double v2;
    public double v3;
    public double v4;

    public InterpContext set(double a, double b, double x)
    {
        return this.set(a, a, b, b, x);
    }

    public InterpContext set(double a0, double a, double b, double b0, double x)
    {
        this.a0 = a0;
        this.a = a;
        this.b = b;
        this.b0 = b0;
        this.x = x;

        this.v1 = this.v2 = this.v3 = this.v4 = 0;

        return this;
    }

    public InterpContext extra(double v1, double v2, double v3, double v4)
    {
        this.v1 = v1;
        this.v2 = v2;
        this.v3 = v3;
        this.v4 = v4;

        return this;
    }
}