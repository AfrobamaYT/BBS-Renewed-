package mchorse.bbs_mod.ui.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.Blaze3D;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * Short tutorial videos, one per feature, opened from the feature itself (owner, 2026-10-13: the
 * 20-40 s feature videos are the tutorial material BBS lacks). The links ship in
 * assets/bbs/tutorials/links.json; config/bbs/settings/tutorials.json overrides or adds entries, so
 * a new video needs no new jar. A feature without a link shows no button.
 */
public final class Tutorials
{
    public static final IKey WATCH = IKey.raw("Watch the tutorial video");

    private static Map<String, String> links;

    private Tutorials()
    {}

    public static String link(String id)
    {
        if (links == null)
        {
            links = new HashMap<>();
            try (InputStream in = Tutorials.class.getResourceAsStream("/assets/bbs/tutorials/links.json"))
            {
                if (in != null)
                {
                    lesen(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
                }
            }
            catch (Exception e)
            {
                System.err.println("[tutorials] built-in links unreadable: " + e);
            }

            File own = new File(BBSMod.getGameFolder(), "config/bbs/settings/tutorials.json");

            if (own.isFile())
            {
                try
                {
                    lesen(JsonParser.parseString(Files.readString(own.toPath(), StandardCharsets.UTF_8)).getAsJsonObject());
                }
                catch (Exception e)
                {
                    System.err.println("[tutorials] " + own + " unreadable: " + e);
                }
            }
        }

        String url = links.get(id);

        return url == null || url.isBlank() ? null : url;
    }

    private static void lesen(JsonObject object)
    {
        for (String key : object.keySet())
        {
            links.put(key, object.get(key).getAsString());
        }
    }

    public static void open(String id)
    {
        String url = link(id);

        if (url != null)
        {
            System.out.println("[tutorials] opening " + id + ": " + url);
            net.minecraft.Util.getPlatform().openUri(URI.create(url));
        }
    }

    /** A help button for the feature, or null when it has no video yet. */
    public static UIIcon button(String id)
    {
        if (link(id) == null)
        {
            return null;
        }

        UIIcon icon = new UIIcon(Icons.HELP, (b) -> open(id));

        icon.tooltip(WATCH);

        return icon;
    }
}
