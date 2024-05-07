package mchorse.bbs_mod.utils;

import mchorse.bbs_mod.graphics.texture.Texture;
import net.coderbot.iris.texture.pbr.PBRTextureHolder;
import net.coderbot.iris.texture.pbr.PBRTextureManager;
import net.irisshaders.iris.api.v0.IrisApi;

public class IrisUtils
{
    private static PBRTextureHolder defaultHolder;

    public static void trackTexture(Texture texture)
    {
        if (defaultHolder == null)
        {
            defaultHolder = PBRTextureManager.INSTANCE.getHolder(-69);
        }

        PBRTextureHolder holder = PBRTextureManager.INSTANCE.getHolder(texture.id);

        if (holder == defaultHolder)
        {

        }
    }

    public static boolean isShaderPackEnabled()
    {
        return IrisApi.getInstance().isShaderPackInUse();
    }

    public static boolean isShadowPass()
    {
        return IrisApi.getInstance().isRenderingShadowPass();
    }
}