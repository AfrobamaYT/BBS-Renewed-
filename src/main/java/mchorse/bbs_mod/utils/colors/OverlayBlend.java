package mchorse.bbs_mod.utils.colors;

import mchorse.bbs_mod.utils.MathUtils;

/**
 * Two color overlays (RGB = color, A = strength) collapsed into one, so stacked poses and the form
 * and bone levels end in one texel: k = 1-(1-k1)(1-k2), c = (c1 k1 (1-k2) + c2 k2) / k. From BBS FS
 * f95662b30 (Wemppy4/bbs-fs, MIT).
 */
public final class OverlayBlend
{
    private OverlayBlend()
    {}

    /** Stack {@code top} over {@code base}, the result in {@code base} */
    public static void stack(Color base, Color top)
    {
        float k1 = MathUtils.clamp(base.a, 0F, 1F);
        float k2 = MathUtils.clamp(top.a, 0F, 1F);
        float k = 1F - (1F - k1) * (1F - k2);

        if (k <= 0F)
        {
            base.a = 0F;

            return;
        }

        base.r = (base.r * k1 * (1F - k2) + top.r * k2) / k;
        base.g = (base.g * k1 * (1F - k2) + top.g * k2) / k;
        base.b = (base.b * k1 * (1F - k2) + top.b * k2) / k;
        base.a = k;
    }
}
