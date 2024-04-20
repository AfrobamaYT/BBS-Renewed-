package mchorse.bbs_mod.utils.keyframes.generic.factories;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.forms.properties.AnchorProperty;
import mchorse.bbs_mod.utils.math.IInterpolation;

public class AnchorKeyframeFactory implements IGenericKeyframeFactory<AnchorProperty.Anchor>
{
    @Override
    public AnchorProperty.Anchor fromData(BaseType data)
    {
        AnchorProperty.Anchor anchor = new AnchorProperty.Anchor();

        anchor.fromData(data.asMap());

        return anchor;
    }

    @Override
    public BaseType toData(AnchorProperty.Anchor value)
    {
        return value.toData();
    }

    @Override
    public AnchorProperty.Anchor copy(AnchorProperty.Anchor value)
    {
        AnchorProperty.Anchor anchor = new AnchorProperty.Anchor();

        anchor.actor = value.actor;
        anchor.attachment = value.attachment;

        return anchor;
    }

    @Override
    public AnchorProperty.Anchor interpolate(AnchorProperty.Anchor a, AnchorProperty.Anchor b, IInterpolation interpolation, float x)
    {
        return a;
    }
}