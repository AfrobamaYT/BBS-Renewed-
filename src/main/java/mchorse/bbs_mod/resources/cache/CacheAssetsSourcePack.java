package mchorse.bbs_mod.resources.cache;

import mchorse.bbs_mod.resources.ISourcePack;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.resources.packs.ExternalAssetsSourcePack;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

public class CacheAssetsSourcePack implements ISourcePack
{
    private File folder;
    private ResourceCache cache;

    public CacheAssetsSourcePack(File folder, ResourceCache cache)
    {
        this.folder = folder;
        this.cache = cache;
    }

    public File getFolder()
    {
        return this.folder;
    }

    @Override
    public String getPrefix()
    {
        return "assets";
    }

    @Override
    public boolean hasAsset(Link link)
    {
        return this.cache.has(link.path);
    }

    @Override
    public InputStream getAsset(Link link) throws IOException
    {
        return new FileInputStream(this.getFile(link));
    }

    @Override
    public File getFile(Link link)
    {
        return new File(this.folder, link.path);
    }

    @Override
    public Link getLink(File file)
    {
        String fullPath = this.folder.getAbsolutePath();
        String filePath = file.getAbsolutePath();

        if (filePath.startsWith(fullPath))
        {
            String path = filePath.substring(fullPath.length());

            if (path.charAt(0) == '/' || path.charAt(0) == '\\')
            {
                path = path.substring(1);
            }

            return new Link(this.getPrefix(), path.replaceAll("\\\\", "/"));
        }

        return null;
    }

    @Override
    public void getLinksFromPath(Collection<Link> links, Link link, boolean recursive)
    {
        File folder = this.getFile(link);

        if (folder.isDirectory())
        {
            String path = link.path;

            if (path.endsWith("/"))
            {
                path = path.substring(0, path.length() - 1);
            }

            ExternalAssetsSourcePack.getLinksFromPathRecursively(folder, links, link, path, recursive ? 9999 : 1);
        }
    }
}