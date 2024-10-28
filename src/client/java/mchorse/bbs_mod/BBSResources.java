package mchorse.bbs_mod;

import mchorse.bbs_mod.network.ClientNetwork;
import mchorse.bbs_mod.resources.cache.CacheAssetsSourcePack;
import mchorse.bbs_mod.resources.cache.ResourceCache;
import mchorse.bbs_mod.resources.cache.ResourceEntry;
import mchorse.bbs_mod.utils.watchdog.WatchDog;
import net.minecraft.client.MinecraftClient;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

public class BBSResources
{
    private static CacheAssetsSourcePack local;
    private static File server;
    private static WatchDog watchDog;

    private static Set<String> requested = new HashSet<>();

    public static Set<String> getRequested()
    {
        return requested;
    }

    public static void resetResources()
    {
        MinecraftClient.getInstance().execute(() ->
        {
            BBSModClient.getModels().reload();
            BBSModClient.getTextures().delete();
            BBSModClient.getSounds().deleteSounds();

            BBSModClient.getFormCategories().setup();
        });
    }

    public static void init(File bbs)
    {
        server = new File(bbs, "server");
        server.mkdirs();

        setupWatchdog(false);

        BBSModClient.getFormCategories().setup();
    }

    public static void setupWatchdog(boolean sender)
    {
        File assetsFolder = BBSMod.getAssetsFolder();

        watchDog = new WatchDog(assetsFolder, false, (runnable) -> MinecraftClient.getInstance().execute(runnable));
        watchDog.register(BBSModClient.getTextures());
        watchDog.register(BBSModClient.getModels());
        watchDog.register(BBSModClient.getSounds());
        watchDog.register(BBSModClient.getFormCategories());

        if (sender)
        {
            watchDog.register(new BBSResourceListener());
        }

        watchDog.start();
    }

    public static void setup(MinecraftClient client, String id, ResourceCache cache)
    {
        requested.clear();

        File folder = new File(server, id);
        File cacheFile = new File(server, id + ".cache.json");
        ResourceCache oldCache = new ResourceCache();

        if (cacheFile.exists())
        {
            oldCache.read(cacheFile);
        }

        folder.mkdirs();

        local = new CacheAssetsSourcePack(folder, cache);

        BBSMod.getDynamicSourcePack().setSecondary(local);

        stopWatchdog();
        setupWatchdog(true);

        for (ResourceEntry newEntry : cache.cache)
        {
            ResourceEntry entry = oldCache.get(newEntry.path());

            if (entry == null || newEntry.lastModified() > entry.lastModified())
            {
                requested.add(newEntry.path());
            }
        }

        cache.write(cacheFile);

        client.execute(() ->
        {
            for (String path : requested)
            {
                ClientNetwork.sendRequestAsset(path, 0);
            }

            if (requested.isEmpty())
            {
                resetResources();
            }
        });
    }

    public static void reset()
    {
        if (local != null)
        {
            local = null;

            BBSMod.getDynamicSourcePack().setSecondary(null);

            stopWatchdog();
            setupWatchdog(false);
        }
    }

    public static void stopWatchdog()
    {
        if (watchDog != null)
        {
            watchDog.stop();
            watchDog = null;
        }
    }
}