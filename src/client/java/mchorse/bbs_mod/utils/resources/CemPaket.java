package mchorse.bbs_mod.utils.resources;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.Set;

/** What the CEM source pack reads out of a pack: part references, texture names, variants (BBS FS 4d7aa6a43, Wemppy4/bbs-fs, MIT) */
final class CemPaket
{
    private CemPaket()
    {}

    static void collectReferences(JsonElement element, Set<String> references)
    {
        if (element instanceof JsonObject object)
        {
            for (Map.Entry<String, JsonElement> entry : object.entrySet())
            {
                if (entry.getKey().equals("model") && entry.getValue().isJsonPrimitive())
                {
                    references.add(entry.getValue().getAsString());
                }
                else
                {
                    collectReferences(entry.getValue(), references);
                }
            }
        }
        else if (element instanceof JsonArray array)
        {
            for (JsonElement child : array)
            {
                collectReferences(child, references);
            }
        }
    }

    static boolean isMinecraft(Identifier id)
    {
        return id.getNamespace().equals("minecraft");
    }

    /** The served paths below {@code link}: files, and with {@code recursive} their folders too */
    static void auflisten(java.util.Collection<mchorse.bbs_mod.resources.Link> links, mchorse.bbs_mod.resources.Link link, boolean recursive, java.util.Collection<String> served, String FOLDER)
    {
        /* The root lists everything; any other folder is asked for with a trailing slash. */
        String path = link.path.isEmpty() || link.path.equals("/") ? "" : link.path.endsWith("/") ? link.path : link.path + "/";

        /* Everything served lives under one folder; anything else asked for is another pack's. */
        if (!path.isEmpty() && !FOLDER.startsWith(path) && !path.startsWith(FOLDER))
        {
            return;
        }

        for (String asset : served)
        {
            if (!asset.startsWith(path))
            {
                continue;
            }

            String rest = asset.substring(path.length());
            int slash = rest.indexOf('/');

            if (slash < 0)
            {
                links.add(mchorse.bbs_mod.resources.Link.assets(asset));
            }
            else if (recursive)
            {
                links.add(mchorse.bbs_mod.resources.Link.assets(asset));
                links.add(mchorse.bbs_mod.resources.Link.assets(path + rest.substring(0, slash) + "/"));
            }
            else
            {
                links.add(mchorse.bbs_mod.resources.Link.assets(path + rest.substring(0, slash) + "/"));
            }
        }
    }

    /** A reference is looked for beside the model first, then at the root of the CEM folder. */
    static Identifier resolve(net.minecraft.server.packs.resources.ResourceManager manager, Identifier jem, String reference, String CEM)
    {
        String path = jem.getPath();
        String beside = path.substring(0, path.lastIndexOf('/') + 1) + reference;

        for (String candidate : new String[] {beside, CEM + "/" + reference})
        {
            Identifier id = CemSourcePack.tryIdentifier(candidate);

            if (id != null && manager.getResource(id).isPresent())
            {
                return id;
            }
        }

        return null;
    }

    /**
     * The young wear the layers of the grown: vanilla draws a drowned's outer layer over a baby drowned
     * too, so a layer folded into a model's folder goes into the folder of its young as well, where the
     * pack has one (BBS FS 572ca409c). A layer the pack draws for the young by name is theirs already.
     */
    static void dressTheYoung(Map<String, Identifier> assets, Map<String, Map<String, Identifier>> layers)
    {
        for (Map.Entry<String, Map<String, Identifier>> layer : layers.entrySet())
        {
            String folder = layer.getKey();
            String name = folder.substring(folder.lastIndexOf('/', folder.length() - 2) + 1, folder.length() - 1);
            String babyFolder = folder.substring(0, folder.length() - 1) + "_baby/";

            if (!assets.containsKey(babyFolder + name + "_baby.jem")) continue;

            for (Map.Entry<String, Identifier> asset : layer.getValue().entrySet())
            {
                assets.putIfAbsent(babyFolder + asset.getKey().substring(folder.length()), asset.getValue());
            }
        }
    }
}
