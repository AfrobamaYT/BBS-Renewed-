package net.minecraft.util;

import org.joml.Vector3f;
import org.joml.Vector4f;

/** BBS-Shim fuer Minecraft vor 1.21.2: die von BBS genutzten Farbhilfen von 1.21.4s ARGB. */
public final class ARGB
{
    private ARGB()
    {}

    public static int alpha(int argb) { return argb >>> 24; }
    public static int red(int argb) { return argb >> 16 & 255; }
    public static int green(int argb) { return argb >> 8 & 255; }
    public static int blue(int argb) { return argb & 255; }

    public static int color(int alpha, int red, int green, int blue)
    {
        return (alpha & 255) << 24 | (red & 255) << 16 | (green & 255) << 8 | blue & 255;
    }

    public static int colorFromFloat(float alpha, float red, float green, float blue)
    {
        return color(Math.round(alpha * 255F), Math.round(red * 255F), Math.round(green * 255F), Math.round(blue * 255F));
    }

    public static int multiply(int a, int b)
    {
        return color(alpha(a) * alpha(b) / 255, red(a) * red(b) / 255, green(a) * green(b) / 255, blue(a) * blue(b) / 255);
    }

    public static int toABGR(int argb)
    {
        return argb & 0xFF00FF00 | (argb & 0xFF0000) >> 16 | (argb & 255) << 16;
    }

    public static Vector3f vector3fFromRGB24(int rgb)
    {
        return new Vector3f(red(rgb) / 255F, green(rgb) / 255F, blue(rgb) / 255F);
    }

    public static Vector4f vector4fFromARGB32(int argb)
    {
        return new Vector4f(red(argb) / 255F, green(argb) / 255F, blue(argb) / 255F, alpha(argb) / 255F);
    }
}
