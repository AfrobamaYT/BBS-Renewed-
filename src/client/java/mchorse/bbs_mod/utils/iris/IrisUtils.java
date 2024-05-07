package mchorse.bbs_mod.utils.iris;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.texture.TextureManager;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.StringUtils;
import net.coderbot.iris.rendertarget.NativeImageBackedSingleColorTexture;
import net.coderbot.iris.texture.pbr.PBRTextureHolder;
import net.coderbot.iris.texture.pbr.PBRTextureManager;
import net.coderbot.iris.texture.pbr.PBRType;
import net.irisshaders.iris.api.v0.IrisApi;

import java.lang.reflect.Field;

public class IrisUtils
{
    private static PBRTextureHolder defaultHolder;
    private static NativeImageBackedSingleColorTexture defaultNormalTexture;
    private static NativeImageBackedSingleColorTexture defaultSpecularTexture;

    public static void trackTexture(Texture texture)
    {
        if (defaultHolder == null)
        {
            defaultHolder = PBRTextureManager.INSTANCE.getHolder(-69);
            defaultNormalTexture = new NativeImageBackedSingleColorTexture(PBRType.NORMAL.getDefaultValue());
            defaultSpecularTexture = new NativeImageBackedSingleColorTexture(PBRType.SPECULAR.getDefaultValue());
        }

        PBRTextureHolder holder = PBRTextureManager.INSTANCE.getHolder(texture.id);
        TextureManager textures = BBSModClient.getTextures();
        Texture error = textures.getError();

        if (holder == defaultHolder && texture != error)
        {
            Link key = CollectionUtils.getKey(textures.textures, texture);

            if (key != null)
            {
                Link normalKey = new Link(key.source, StringUtils.removeExtension(key.path) + "_n.png");
                Link specularKey = new Link(key.source, StringUtils.removeExtension(key.path) + "_s.png");

                Texture normalTexture = textures.getTexture(normalKey);
                Texture specularTexture = textures.getTexture(specularKey);

                IrisPBRTextureHolder newHolder = new IrisPBRTextureHolder(
                    normalTexture == error ? defaultNormalTexture : new IrisTextureWrapper(normalKey),
                    specularTexture == error ? defaultSpecularTexture : new IrisTextureWrapper(specularKey)
                );

                try
                {
                    Field holders = PBRTextureManager.class.getDeclaredField("holders");

                    holders.setAccessible(true);

                    Int2ObjectMap<PBRTextureHolder> holdersMap = (Int2ObjectMap<PBRTextureHolder>) holders.get(PBRTextureManager.INSTANCE);

                    holdersMap.put(texture.id, newHolder);
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
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