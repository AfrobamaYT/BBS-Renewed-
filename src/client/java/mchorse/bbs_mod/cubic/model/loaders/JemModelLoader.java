package mchorse.bbs_mod.cubic.model.loaders;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.animation.Animations;
import mchorse.bbs_mod.cubic.jem.CemHierarchy;
import mchorse.bbs_mod.cubic.jem.CemNames;
import mchorse.bbs_mod.cubic.jem.JemModelParser;
import mchorse.bbs_mod.cubic.jem.JemSchichten;
import mchorse.bbs_mod.cubic.model.ModelManager;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.IOUtils;
import mchorse.bbs_mod.utils.StringUtils;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OptiFine CEM models: a .jem entity model with the .jpm part models it references, converted into
 * BBS's own model by {@link JemModelParser}. The texture is the folder's first PNG, as with the other
 * loaders. From BBS FS b44e56c87 (Wemppy4/bbs-fs, MIT).
 */
public class JemModelLoader implements IModelLoader
{
    @Override
    public ModelInstance load(String id, ModelManager models, Link model, Collection<Link> links, MapType config)
    {
        Collection<Link> recursiveLinks = BBSMod.getProvider().getLinksFromPath(model, true);
        List<Link> jems = IModelLoader.getLinks(links, ".jem");

        if (jems.isEmpty())
        {
            return null;
        }

        Link texture = IModelLoader.getLink(model.combine("model.png"), recursiveLinks, ".png");
        Map<String, JsonObject> jpms = this.loadJpms(recursiveLinks);
        Link chosen = pickJem(jems, model);
        String entity = StringUtils.removeExtension(StringUtils.fileName(chosen.path));

        CemHierarchy hierarchy = hierarchy(entity, config);

        try
        {
            JemModelParser.Result result = parse(chosen, jpms, models, hierarchy);
            List<String> warnings = new ArrayList<>(result.warnings());
            List<String> layers = new ArrayList<>();

            /* The folder's other files: a layer over the entity is folded in as a material, anything
             * else is left out. The young wear the layers of the grown (drowned_outer over drowned_baby),
             * which CemSourcePack puts in their folder (BBS FS 42ee69b2d, 572ca409c) */
            String grown = entity.replace("_baby", "");

            for (Link link : jems)
            {
                if (link == chosen) continue;

                String name = StringUtils.removeExtension(StringUtils.fileName(link.path));
                CemNames.Layer layer = CemNames.layer(name);

                if (layer != null && (layer.base().equals(entity) || layer.base().equals(grown)))
                {
                    JemModelParser.Result folded = parse(link, jpms, models, hierarchy);

                    for (String warning : folded.warnings()) warnings.add(layer.name() + ": " + warning);
                    for (String warning : JemSchichten.graft(result, folded, layer.name())) warnings.add(layer.name() + ": " + warning);

                    layers.add(layer.name());
                }
                else
                {
                    warnings.add(name + ".jem is left out - a folder is one model, and " + entity + ".jem is the one loaded; a layer over it (_outer, _armor, _saddle, _wool...) would be folded in");
                }
            }

            for (String warning : warnings) System.err.println("OptiFine CEM model " + model + ": " + warning);

            if (result.model().topGroups.isEmpty())
            {
                return null;
            }

            ModelInstance instance = new ModelInstance(id, result.model(), new Animations(models.parser), texture);

            result.animation().jem = entity;
            instance.cemAnimation = result.animation().isEmpty() ? null : result.animation();
            materials(instance, model, recursiveLinks, entity, texture, layers);

            /* CEM layers overlap (headwear, jacket, sleeves): no culling unless config.json says so */
            instance.config.culling.set(false);

            /* Vanilla draws the young at half size with the feet on the ground, and a pack draws its _baby
             * file at the adult's scale knowing that; config.json can still override it (BBS FS 2f1d64e70) */
            if (CemNames.baby(entity)) instance.config.scale.set(new org.joml.Vector3f(0.5F));

            instance.applyConfig(config);

            return instance;
        }
        catch (Exception e)
        {
            System.err.println("Failed to load OptiFine CEM .jem model: " + model);
            e.printStackTrace();
        }

        return null;
    }

    private static JemModelParser.Result parse(Link link, Map<String, JsonObject> jpms, ModelManager models, CemHierarchy hierarchy) throws java.io.IOException
    {
        try (InputStream stream = BBSMod.getProvider().getAsset(link))
        {
            return JemModelParser.parse(JsonParser.parseString(IOUtils.readText(stream)).getAsJsonObject(), jpms::get, models.parser, hierarchy);
        }
    }

    /**
     * With a layer folded in the model has more than one texture, and the base becomes a material too,
     * named after the entity, so everything keyed by material comes on: the texture per material, the
     * texture tracks. A layer wears {@code textures/<layer>/model.png} of its folder; without one it
     * falls back to the base's at render time and the folder is made for the user to drop one into. A
     * model with no layer stays a single-texture one (BBS FS 42ee69b2d).
     */
    private static void materials(ModelInstance instance, Link model, Collection<Link> links, String entity, Link texture, List<String> layers)
    {
        if (layers.isEmpty()) return;

        for (mchorse.bbs_mod.cubic.data.model.ModelGroup group : ((mchorse.bbs_mod.cubic.data.model.Model) instance.model).getAllGroups())
        {
            for (mchorse.bbs_mod.cubic.data.model.ModelCube cube : group.cubes)
            {
                if (cube.material.isEmpty()) cube.material = entity;
            }
        }

        instance.materials.add(entity);

        if (texture != null) instance.materialTextures.put(entity, texture);

        for (String layer : layers)
        {
            Link preferred = model.combine("textures/" + layer + "/model.png");
            Link layerTexture = links.contains(preferred) ? preferred : IModelLoader.findMaterialTexture(links, model, layer);

            instance.materials.add(layer);

            if (layerTexture != null) instance.materialTextures.put(layer, layerTexture);
            else IModelLoader.ensureMaterialFolder(BBSMod.getProvider(), model, layer);
        }
    }

    /**
     * Which .jem a folder holding several stands for: the one named after the folder, else the first by
     * name. A pack ships an entity as a set (wolf.jem beside wolf_armor.jem) and BBS loads a folder as
     * one model, so naming settles it (BBS FS 286c8967b).
     */
    private static Link pickJem(List<Link> jems, Link model)
    {
        String folder = StringUtils.fileName(model.path);

        for (Link link : jems)
        {
            if (StringUtils.removeExtension(StringUtils.fileName(link.path)).equals(folder)) return link;
        }

        return jems.get(0);
    }

    /**
     * The vanilla rig for this model: what Minecraft's own model of the entity says, with the model's
     * own {@code cem_parents} from config.json laid over it, so any entity can be fixed with data
     * (BBS FS cb590aaaa, f19a5624a). Read off the map: the config is applied after parsing.
     */
    private static CemHierarchy hierarchy(String file, MapType config)
    {
        Map<String, String> parents = new java.util.LinkedHashMap<>();

        if (config != null)
        {
            MapType overrides = config.getMap("cem_parents");

            for (String child : overrides.keys()) parents.put(child, overrides.getString(child));
        }

        return mchorse.bbs_mod.cubic.jem.VanillaRigs.of(mchorse.bbs_mod.cubic.jem.CemNames.entity(file)).withParents(parents);
    }

    /** Every .jpm in the folder, by file name with and without extension, for the .jem's "model" references */
    private Map<String, JsonObject> loadJpms(Collection<Link> links)
    {
        Map<String, JsonObject> jpms = new HashMap<>();

        for (Link link : IModelLoader.getLinks(links, ".jpm"))
        {
            try (InputStream stream = BBSMod.getProvider().getAsset(link))
            {
                JsonObject jpm = JsonParser.parseString(IOUtils.readText(stream)).getAsJsonObject();
                String name = StringUtils.fileName(link.path);

                jpms.put(name, jpm);
                jpms.put(StringUtils.removeExtension(name), jpm);
            }
            catch (Exception e)
            {
                System.err.println("Failed to load OptiFine CEM .jpm part: " + link + " (" + e.getMessage() + ")");
            }
        }

        return jpms;
    }
}
