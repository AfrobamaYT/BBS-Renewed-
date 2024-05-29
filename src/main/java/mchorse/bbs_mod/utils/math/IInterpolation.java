package mchorse.bbs_mod.utils.math;

public interface IInterpolation
{
    public float interpolate(float a, float b, float x);

    public double interpolate(double a, double b, double x);

    public String getKey();

    public int getKeyCode();
}
