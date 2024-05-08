package mchorse.bbs_mod.utils.iris;

import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.StringUtils;
import net.irisshaders.iris.targets.backed.NativeImageBackedSingleColorTexture;
import net.irisshaders.iris.texture.pbr.PBRType;
import net.irisshaders.iris.texture.pbr.loader.PBRTextureLoader;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.resource.ResourceManager;

public class IrisTextureWrapperLoader implements PBRTextureLoader
{
    public NativeImageBackedSingleColorTexture defaultNormalTexture;
    public NativeImageBackedSingleColorTexture defaultSpecularTexture;

    @Override
    public void load(AbstractTexture abstractTexture, ResourceManager resourceManager, PBRTextureConsumer pbrTextureConsumer)
    {
        if (this.defaultSpecularTexture == null)
        {
            this.defaultNormalTexture = new NativeImageBackedSingleColorTexture(PBRType.NORMAL.getDefaultValue());
            this.defaultSpecularTexture = new NativeImageBackedSingleColorTexture(PBRType.SPECULAR.getDefaultValue());
        }

        if (abstractTexture instanceof IrisTextureWrapper wrapper)
        {
            Link key = wrapper.texture;
            Link normalKey = new Link(key.source, StringUtils.removeExtension(key.path) + "_n.png");
            Link specularKey = new Link(key.source, StringUtils.removeExtension(key.path) + "_s.png");

            pbrTextureConsumer.acceptNormalTexture(new IrisTextureWrapper(normalKey, this.defaultNormalTexture));
            pbrTextureConsumer.acceptSpecularTexture(new IrisTextureWrapper(specularKey, this.defaultSpecularTexture));
        }
    }
}
