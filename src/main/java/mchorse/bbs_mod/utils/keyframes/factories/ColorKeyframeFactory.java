package mchorse.bbs_mod.utils.keyframes.factories;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.IntType;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.interps.IInterp;

public class ColorKeyframeFactory implements IKeyframeFactory<Color>
{
    private final ThreadLocal<Color> output = ThreadLocal.withInitial(Color::new);

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
    public Color createEmpty()
    {
        return new Color().set(Colors.WHITE);
    }

    @Override
    public Color copy(Color value)
    {
        return value.copy();
    }

    @Override
    public Color interpolate(Color preA, Color a, Color b, Color postB, IInterp interpolation, float x)
    {
        Color i = this.output.get();
        i.r = MathUtils.clamp((float) interpolation.interpolate(IInterp.context.set(preA.r, a.r, b.r, postB.r, x)), 0F, 1F);
        i.g = MathUtils.clamp((float) interpolation.interpolate(IInterp.context.set(preA.g, a.g, b.g, postB.g, x)), 0F, 1F);
        i.b = MathUtils.clamp((float) interpolation.interpolate(IInterp.context.set(preA.b, a.b, b.b, postB.b, x)), 0F, 1F);
        i.a = MathUtils.clamp((float) interpolation.interpolate(IInterp.context.set(preA.a, a.a, b.a, postB.a, x)), 0F, 1F);

        return i;
    }
}
