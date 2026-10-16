package mchorse.bbs_mod.utils.resources;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mchorse.bbs_mod.cubic.jem.CemNames;
import mchorse.bbs_mod.resources.ISourcePack;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.IOUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.resources.Identifier;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * The OptiFine CEM models of the installed resource packs, served as if they were models of the
 * user's own — so a pack's cow shows up in the form palette the moment the pack is enabled, without
 * anyone copying files into {@code config/bbs/assets/models/}.
 *
 * <p>It registers under {@link Link#ASSETS} alongside the folder and the jar, after both, which is
 * what makes the whole existing pipeline carry it: model ids, {@code config.json}, the palette, the
 * lazy loader. Being last also means a folder of the user's under the same id wins, so a pack model
 * can be overridden or given a config without touching the pack.</p>
 *
 * <p>A pack keeps its models flat in one folder and its textures somewhere else entirely, while BBS
 * addresses a model by the folder it sits in. So the pack presents a folder per model:</p>
 *
 * <pre>
 * models/cem/&lt;entity&gt;/&lt;entity&gt;.jem   the entity model
 * models/cem/&lt;entity&gt;/&lt;name&gt;.jpm     only the part models that .jem refers to
 * models/cem/&lt;entity&gt;/model.png       the entity's texture, when it can be resolved
 * </pre>
 *
 * <p>Everything is read through Minecraft's own {@link ResourceManager}, which is the point: it
 * already merges the enabled packs in the right order and resolves a pack's format overlays (Fresh
 * Animations ships four), so what BBS loads is what OptiFine would have loaded. Reading the zips
 * directly would take the wrong overlay.</p>
 *
 * <p>Nothing here is a file the user owns, so {@link #getFile}/{@link #getLink} answer null. That
 * disables saving the model geometry (a .jem is not editable anyway) and keeps the watchdog out of
 * it; changes arrive through {@link #reindex()} on a resource reload instead.</p>
 *
 * <p>From BBS FS 4d7aa6a43 (Wemppy4/bbs-fs, MIT); name rules and reference scanning in {@link CemPaket}.</p>
 */
public class CemSourcePack implements ISourcePack
{
    /** The folder every pack model lands in, so its id reads {@code cem/<entity>}: an id written into saved forms, not a word */
    public static final String NAME = "cem";

    /** Where a pack model lands */
    public static final String FOLDER = "models/" + NAME + "/";

    /** Where OptiFine keeps entity models inside a resource pack. */
    private static final String CEM = "optifine/cem";

    /** The name BBS's model loaders look for first when picking a model's texture. */
    private static final String TEXTURE = "model.png";

    private final ResourceManager manager;

    /**
     * Synthetic path (without the source) &rarr; the resource behind it. Replaced whole on a reload
     * rather than edited, because the model loader reads it off its own thread.
     */
    private volatile Map<String, Identifier> assets = new HashMap<>();

    public CemSourcePack()
    {
        this.manager = Minecraft.getInstance().getResourceManager();

        this.reindex();
    }

    /** Rebuild the index from the packs as they are enabled now. */
    public void reindex()
    {
        Map<String, Identifier> assets = new TreeMap<>();
        Map<String, Map<String, Identifier>> layers = new TreeMap<>();
        CemTexturen textures = new CemTexturen(this.manager);

        for (Identifier jem : this.manager.listResources(CEM, (id) -> CemPaket.isMinecraft(id) && id.getPath().endsWith(".jem")).keySet())
        {
            /* The path under the CEM folder without its extension: "cow", or "boat/bamboo" for the
             * ones a pack files away in a subfolder. The last segment names the model's folder, and
             * the .jem inside carries the same name so the loader picks it over any sibling. A layer
             * over an entity (sheep_wool, drowned_outer) is no model of its own: it goes into the
             * folder of the model it is a layer of, its texture under the layer's material (BBS FS
             * bd268e66f). The texture's siblings come along to pick from (9afb17bfc). */
            String model = jem.getPath().substring(CEM.length() + 1, jem.getPath().length() - 4);
            String name = model.substring(model.lastIndexOf('/') + 1);
            CemNames.Layer layer = CemNames.layer(name);
            String folder = FOLDER + (layer == null ? model : model.substring(0, model.length() - name.length()) + layer.base()) + "/";
            String subfolder = layer == null ? "" : "textures/" + layer.name() + "/";
            Map<String, Identifier> own = new TreeMap<>();

            own.put(folder + name + ".jem", jem);
            this.collectParts(jem, folder, own);

            Identifier texture = textures.resolve(name);

            if (texture != null)
            {
                own.put(folder + subfolder + TEXTURE, texture);

                for (Identifier alternative : textures.alternatives(texture)) own.put(folder + subfolder + CemTexturen.fileName(alternative) + ".png", alternative);
            }

            assets.putAll(own);

            if (layer != null) layers.computeIfAbsent(folder, (key) -> new TreeMap<>()).putAll(own);
        }

        CemPaket.dressTheYoung(assets, layers);

        this.assets = assets;
    }

    /**
     * Put every part model the entity model pulls in beside it, under its own file name — which is
     * how {@link mchorse.bbs_mod.cubic.model.loaders.JemModelLoader} keys them. Only the ones it
     * actually refers to: a pack's CEM folder holds a part model for every entity, and serving all
     * of them into every model's folder would be a listing of thousands.
     */
    private void collectParts(Identifier jem, String folder, Map<String, Identifier> assets)
    {
        Set<String> visited = new HashSet<>();
        Set<String> queue = new HashSet<>(this.references(jem));

        while (!queue.isEmpty())
        {
            String reference = queue.iterator().next();

            queue.remove(reference);

            if (!visited.add(reference))
            {
                continue;
            }

            Identifier part = CemPaket.resolve(this.manager, jem, reference, CEM);

            if (part == null)
            {
                continue;
            }

            assets.put(folder + reference.substring(reference.lastIndexOf('/') + 1), part);

            queue.addAll(this.references(part));
        }
    }

    /** The {@code "model"} references inside a .jem/.jpm — a part model may pull in another. */
    private Set<String> references(Identifier id)
    {
        Set<String> references = new HashSet<>();

        try (InputStream stream = this.open(id))
        {
            if (stream == null)
            {
                return references;
            }

            CemPaket.collectReferences(JsonParser.parseString(IOUtils.readText(stream)), references);
        }
        catch (Exception e)
        {
            System.err.println("CEM model " + id + " could not be read for its part models: " + e);
        }

        return references;
    }

    /** {@code new Identifier} throws on anything it does not consider a legal path. */
    static Identifier tryIdentifier(String path)
    {
        try
        {
            return Identifier.tryBuild("minecraft", path);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private InputStream open(Identifier id) throws IOException
    {
        Optional<Resource> resource = this.manager.getResource(id);

        return resource.isPresent() ? resource.get().open() : null;
    }

    @Override
    public String getPrefix()
    {
        return Link.ASSETS;
    }

    @Override
    public boolean hasAsset(Link link)
    {
        return this.assets.containsKey(link.path);
    }

    @Override
    public InputStream getAsset(Link link) throws IOException
    {
        Identifier id = this.assets.get(link.path);

        return id == null ? null : this.open(id);
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
        CemPaket.auflisten(links, link, recursive, this.assets.keySet(), FOLDER);
    }
}
