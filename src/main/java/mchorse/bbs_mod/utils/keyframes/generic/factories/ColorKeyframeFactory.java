package mchorse.bbs_mod.utils.keyframes.generic.factories;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.IntType;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.interps.IInterp;

public class ColorKeyframeFactory implements IGenericKeyframeFactory<Color>
{
    private Color i = new Color();

    @Override
    public Color fromData(BaseType data)
    {
        if (!data.isNumeric())
        {
            return new Color();
        }

        return Color.rgba(data.asNumeric().intValue());
    }

    @Override
    public BaseType toData(Color value)
    {
        return new IntType(value.getARGBColor());
    }

    @Override
    public Color copy(Color value)
    {
        return value.copy();
    }

    @Override
    public Color interpolate(Color a, Color b, IInterp interpolation, float x)
    {
        this.i.r = interpolation.interpolate(a.r, b.r, x);
        this.i.g = interpolation.interpolate(a.g, b.g, x);
        this.i.b = interpolation.interpolate(a.b, b.b, x);
        this.i.a = interpolation.interpolate(a.a, b.a, x);

        return this.i;
    }
}