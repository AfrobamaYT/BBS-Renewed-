package mchorse.bbs_mod.utils.iris;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.resource.ResourceManager;

import java.io.IOException;

public class IrisTextureWrapper extends AbstractTexture
{
    public final Link link;

    public IrisTextureWrapper(Link link)
    {
        this.link = link;
    }

    @Override
    public void load(ResourceManager manager) throws IOException
    {}

    @Override
    public int getGlId()
    {
        Texture texture = BBSModClient.getTextures().getTexture(this.link);

        return texture == null ? -1 : texture.id;
    }

    @Override
    public void close()
    {
        BBSModClient.getTextures().delete(this.link);
    }
}