// Coded by Afrobama/Claude — BBS <-> DaVinci Resolve live link (task 79, docs/DAVINCI-LINK-PLAN.md).
package com.davincilink;

import com.blenderbridge.BridgeServer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.elements.overlay.UISoundOverlayPanel;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DaVinci's media (media pool and Power Bins) as BBS knows it.
 *
 * <p>The Resolve side sends {@code MEDIA} with every item it sees. BBS keeps that list in
 * {@code config/bbs/settings/davinci/media.json}: synced once, it stays usable without DaVinci,
 * and syncing again refreshes it (owner's decision 2026-10-11). The files themselves are copied
 * into BBS' assets only when a sound is actually used ({@link DavinciFilmSync#adopt}).</p>
 */
public final class DavinciMedia
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final String TYPE_MEDIA = "MEDIA";

    private static JsonArray items;
    private static long syncedAt;

    private DavinciMedia()
    {}

    public static boolean handles(String type)
    {
        return TYPE_MEDIA.equals(type);
    }

    public static void handle(BridgeServer server, String type, JsonObject fields)
    {
        JsonArray incoming = fields.getAsJsonArray("items");
        JsonObject stored = new JsonObject();

        stored.addProperty("synced_at", System.currentTimeMillis());
        stored.add("items", incoming);

        File file = cacheFile();
        File temporary = new File(file.getPath() + ".tmp");

        try
        {
            Files.createDirectories(file.getParentFile().toPath());
            Files.writeString(temporary.toPath(), GSON.toJson(stored), StandardCharsets.UTF_8);
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
        catch (IOException e)
        {
            LOGGER.error("[davincilink] could not store the media list in {}", file, e);
        }

        items = incoming;
        syncedAt = stored.get("synced_at").getAsLong();
        LOGGER.info("[davincilink] media list from DaVinci: {} items", incoming.size());
    }

    /** The last list DaVinci sent, from memory or from disk; empty if it never synced. */
    public static JsonArray items()
    {
        if (items == null)
        {
            File file = cacheFile();

            items = new JsonArray();

            if (file.isFile())
            {
                try
                {
                    JsonObject stored = JsonParser.parseString(Files.readString(file.toPath(), StandardCharsets.UTF_8)).getAsJsonObject();

                    items = stored.getAsJsonArray("items");
                    syncedAt = stored.get("synced_at").getAsLong();
                }
                catch (Exception e)
                {
                    LOGGER.error("[davincilink] media list {} is unreadable", file, e);
                }
            }
        }

        return items;
    }

    public static long syncedAt()
    {
        items();

        return syncedAt;
    }

    /** Listed in BBS' sound picker under this prefix, e.g. "DaVinci/Master/Musik/song.mp3". */
    public static final String PREFIX = "DaVinci/";

    /** Puts DaVinci's sounds into BBS' sound picker; picking one copies (and converts) it once. */
    public static void register()
    {
        UISoundOverlayPanel.EXTRA.add(new UISoundOverlayPanel.ExtraSounds()
        {
            @Override
            public Collection<String> names()
            {
                return sounds().keySet();
            }

            @Override
            public Link take(String name)
            {
                File file = sounds().get(name);

                if (file == null)
                {
                    throw new IllegalStateException("not in DaVinci's media list: " + name);
                }
                if (!file.isFile())
                {
                    throw new IllegalStateException("the DaVinci file is gone: " + file);
                }

                return DavinciAdopt.adopt(file);
            }
        });
    }

    /**
     * Every sound DaVinci listed, by the one name the picker shows. A file that is gone is not
     * offered; two items with the same name in the same bin (two "Musik.mp3") get "[2]", "[3]"
     * -- otherwise the name would pick whichever came first, maybe the dead one.
     */
    static Map<String, File> sounds()
    {
        Map<String, File> out = new LinkedHashMap<>();

        for (JsonElement element : items())
        {
            JsonObject item = element.getAsJsonObject();
            File file = new File(item.get("path").getAsString());

            if (!"audio".equals(item.get("kind").getAsString()) || !file.isFile())
            {
                continue;
            }

            String base = PREFIX + item.get("folder").getAsString() + "/" + item.get("name").getAsString();
            String name = base;

            for (int n = 2; out.containsKey(name); n++)
            {
                name = base + " [" + n + "]";
            }

            out.put(name, file);
        }

        return out;
    }

    public static File cacheFile()
    {
        return new File(BBSMod.getSettingsFolder(), "davinci/media.json");
    }
}
