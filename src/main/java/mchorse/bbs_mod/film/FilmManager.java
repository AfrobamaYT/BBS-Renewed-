package mchorse.bbs_mod.film;

import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.manager.BaseManager;
import mchorse.bbs_mod.utils.manager.storage.AtomicCompressedDataStorage;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.util.function.Supplier;

public class FilmManager extends BaseManager<Film>
{
    public FilmManager(Supplier<File> folder)
    {
        super(folder);

        this.backUps = true;
        this.storage = new AtomicCompressedDataStorage();
    }

    /** An older queued full-film save must not erase a newly committed receipt. */
    @Override
    public synchronized boolean save(String id, MapType data)
    {
        try
        {
            File file = this.getFile(id);
            if (file.exists())
            {
                String previous = this.storage.load(file).getString("bridge_journal");
                if (!previous.isEmpty())
                {
                    JsonObject old = JsonParser.parseString(previous).getAsJsonObject();
                    String incoming = data.getString("bridge_journal");
                    if (incoming.isEmpty())
                    {
                        throw new IllegalStateException("stale save lacks the durable film journal");
                    }
                    JsonObject next = JsonParser.parseString(incoming).getAsJsonObject();
                    if (!old.get("token").equals(next.get("token"))
                        || old.get("generation").getAsLong() > next.get("generation").getAsLong())
                    {
                        throw new IllegalStateException("stale save would roll back the durable film journal");
                    }
                }
            }
            return super.save(id, data);
        }
        catch (Exception exception)
        {
            exception.printStackTrace();
            return false;
        }
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
}