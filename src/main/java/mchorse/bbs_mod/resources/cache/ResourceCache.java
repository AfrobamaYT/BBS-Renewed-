package mchorse.bbs_mod.resources.cache;

import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.IMapSerializable;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ResourceCache implements IMapSerializable
{
    public List<ResourceEntry> cache = new ArrayList<>();

    public ResourceCache()
    {}

    public ResourceCache(List<ResourceEntry> cache)
    {
        this.cache = cache;
    }

    public ResourceEntry get(String path)
    {
        for (ResourceEntry resourceEntry : this.cache)
        {
            if (resourceEntry.path().equals(path))
            {
                return resourceEntry;
            }
        }

        return null;
    }

    public boolean has(String path)
    {
        return this.get(path) != null;
    }

    public void read(File file)
    {
        try
        {
            BaseType read = DataToString.read(file);

            if (read != null && read.isMap())
            {
                this.fromData(read.asMap());
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    public void write(File file)
    {
        DataToString.writeSilently(file, this.toData(), true);
    }

    @Override
    public void toData(MapType data)
    {
        ListType list = new ListType();

        for (ResourceEntry pair : this.cache)
        {
            MapType type = new MapType();

            type.putString("path", pair.path());
            type.putLong("lastModified", pair.lastModified());
            list.add(type);
        }

        data.put("cache", list);
    }

    @Override
    public void fromData(MapType data)
    {
        this.cache.clear();

        ListType list = data.getList("cache");

        for (BaseType type : list)
        {
            if (!type.isMap())
            {
                continue;
            }

            MapType map = type.asMap();
            ResourceEntry pair = new ResourceEntry(
                map.getString("path"),
                map.getLong("lastModified")
            );

            this.cache.add(pair);
        }
    }
}