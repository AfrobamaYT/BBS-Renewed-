package mchorse.bbs_mod.utils.iris;

import net.irisshaders.iris.texture.pbr.PBRTextureHolder;
import net.minecraft.client.texture.AbstractTexture;
import org.jetbrains.annotations.NotNull;

public class IrisPBRTextureHolder implements PBRTextureHolder
{
    private AbstractTexture normal;
    private AbstractTexture specular;

    public IrisPBRTextureHolder(AbstractTexture normal, AbstractTexture specular)
    {
        this.normal = normal;
        this.specular = specular;
    }

    @Override
    public @NotNull AbstractTexture normalTexture()
    {
        return this.normal;
    }

    @Override
    public @NotNull AbstractTexture specularTexture()
    {
        return this.specular;
    }
}