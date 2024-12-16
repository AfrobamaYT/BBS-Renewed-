package mchorse.bbs_mod.utils.resources;

import mchorse.bbs_mod.resources.ISourcePack;
import mchorse.bbs_mod.resources.Link;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Optional;

public class MinecraftSourcePack implements ISourcePack
{
    private final ResourceManager manager;

    public MinecraftSourcePack()
    {
        this.manager = MinecraftClient.getInstance().getResourceManager();
    }

    @Override
    public String getPrefix()
    {
        return "minecraft";
    }

    @Override
    public boolean hasAsset(Link link)
    {
        return this.manager.getResource(new Identifier(link.toString())).isPresent();
    }

    @Override
    public InputStream getAsset(Link link) throws IOException
    {
        Optional<Resource> resource = this.manager.getResource(new Identifier(link.toString()));

        if (resource.isPresent())
        {
            return resource.get().getInputStream();
        }

        return null;
    }

    @Override
    public File getFile(Link link)
    {
        return null;
    }

    @Override
    public Link getLink(File file)
    {
        return null;
    }

    @Override
    public void getLinksFromPath(Collection<Link> links, Link link, boolean recursive)
    {
        /* ¯\_(ツ)_/¯ */
    }
}