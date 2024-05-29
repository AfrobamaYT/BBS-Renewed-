package mchorse.bbs_mod.camera.data;

import mchorse.bbs_mod.utils.math.IInterpolation;
import mchorse.bbs_mod.utils.math.Interpolation;

import java.util.HashMap;
import java.util.Map;

public class InterpolationType
{
    public static Map<String, IInterpolation> MAP = new HashMap<>();

    static
    {
        for (Interpolation value : Interpolation.values())
        {
            MAP.put(((IInterpolation) value).getKey(), value);
        }
    }
}
