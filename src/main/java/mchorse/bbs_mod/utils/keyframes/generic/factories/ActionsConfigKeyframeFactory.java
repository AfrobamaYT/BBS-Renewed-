package mchorse.bbs_mod.utils.keyframes.generic.factories;

import mchorse.bbs_mod.cubic.animation.ActionsConfig;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.utils.math.IInterpolation;

public class ActionsConfigKeyframeFactory implements IGenericKeyframeFactory<ActionsConfig>
{
    @Override
    public ActionsConfig fromData(BaseType data)
    {
        ActionsConfig configs = new ActionsConfig();

        if (data.isMap())
        {
            configs.fromData(data.asMap());
        }

        return configs;
    }

    @Override
    public BaseType toData(ActionsConfig value)
    {
        return value.toData();
    }

    @Override
    public ActionsConfig copy(ActionsConfig value)
    {
        ActionsConfig configs = new ActionsConfig();

        configs.fromData(value.toData());

        return configs;
    }

    @Override
    public ActionsConfig interpolate(ActionsConfig a, ActionsConfig b, IInterpolation interpolation, float x)
    {
        return a;
    }
}