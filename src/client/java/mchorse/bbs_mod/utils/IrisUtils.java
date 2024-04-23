package mchorse.bbs_mod.utils;

import net.irisshaders.iris.api.v0.IrisApi;

public class IrisUtils
{
    public static boolean isShaderPackEnabled()
    {
        return IrisApi.getInstance().isShaderPackInUse();
    }

    public static boolean isShadowPass()
    {
        return IrisApi.getInstance().isRenderingShadowPass();
    }
}