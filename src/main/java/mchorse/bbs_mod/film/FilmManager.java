package mchorse.bbs_mod.film;

import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.manager.BaseManager;
import mchorse.bbs_mod.utils.manager.storage.CompressedDataStorage;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class FilmManager extends BaseManager<Film>
{
    private Map<String, Film> cache = new HashMap<>();

    public FilmManager(Supplier<File> folder)
    {
        super(folder);

        this.storage = new CompressedDataStorage();
    }

    @Override
    protected Film createData(String id, MapType mapType)
    {
        Film film = new Film();

        if (mapType != null)
        {
            film.fromData(mapType);
        }

        return film;
    }

    @Override
    protected String getExtension()
    {
        return ".dat";
    }

    /* Cache implementation */

    @Override
    public Film load(String id)
    {
        if (this.cache.containsKey(id))
        {
            return this.cache.get(id);
        }

        Film film = super.load(id);

        this.cache.put(id, film);

        return film;
    }

    @Override
    public boolean save(String id, MapType data)
    {
        this.cache.put(id, this.create(id, data));

        return super.save(id, data);
    }

    @Override
    public boolean rename(String from, String to)
    {
        Film remove = this.cache.remove(from);

        if (remove != null)
        {
            this.cache.put(to, remove);
        }

        return super.rename(from, to);
    }

    @Override
    public boolean delete(String name)
    {
        this.cache.remove(name);

        return super.delete(name);
    }

    public void reset()
    {
        this.cache.clear();
    }
}