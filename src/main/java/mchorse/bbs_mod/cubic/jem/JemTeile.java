package mchorse.bbs_mod.cubic.jem;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mchorse.bbs_mod.cubic.data.model.ModelCube;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import org.joml.Vector3f;

import java.util.Map;

/**
 * A CEM part's content: its boxes and sprites, its submodels as child bones, an external .jpm merged
 * in, and the quirks of each noted on the parse. From BBS FS b44e56c87, 562c23747, 663b33cf8
 * (Wemppy4/bbs-fs, MIT); boxes and UVs live in {@link JemBoxes}.
 */
final class JemTeile
{
    private JemTeile()
    {}

    /**
     * Add a definition's boxes/sprites to a bone and recurse into its submodels (as child bones).
     *
     * @param boxOffset   offset added to this definition's box coordinates (zero for a top-level part,
     *                    the submodel's own pivot for a submodel).
     * @param groupOrigin this bone's pivot, accumulated into its submodels' translates when depth >= 1.
     */
    static void readContent(JemParse parse, ModelGroup group, JsonObject def, Vector3f boxOffset, Vector3f groupOrigin, int depth)
    {
        boolean geometry = def.has("boxes") || def.has("sprites");

        if (geometry)
        {
            checkInvertAxis(parse, def);
        }

        boolean mirror = mirrorTexture(parse, def);

        if (def.has("boxes"))
        {
            for (JsonElement element : def.getAsJsonArray("boxes"))
            {
                ModelCube cube = JemBoxes.parseBox(parse, element.getAsJsonObject(), boxOffset, mirror);

                if (cube != null)
                {
                    group.cubes.add(cube);
                }
            }
        }

        if (def.has("sprites"))
        {
            for (JsonElement element : def.getAsJsonArray("sprites"))
            {
                ModelCube cube = JemBoxes.parseSprite(parse, element.getAsJsonObject(), boxOffset);

                if (cube != null)
                {
                    group.cubes.add(cube);
                }
            }
        }

        if (def.has("submodels"))
        {
            for (JsonElement element : def.getAsJsonArray("submodels"))
            {
                parseSubmodel(parse, group, element.getAsJsonObject(), groupOrigin, depth);
            }
        }

        if (def.has("submodel"))
        {
            parseSubmodel(parse, group, def.getAsJsonObject("submodel"), groupOrigin, depth);
        }
    }

    static void parseSubmodel(JemParse parse, ModelGroup parent, JsonObject def, Vector3f parentOrigin, int depth)
    {
        Vector3f origin = translate(def);

        /* Submodels two or more levels deep accumulate the parent submodel's pivot (Blockbench does
         * this only for depth >= 1, so a part's direct submodels keep their own raw translate). */
        if (depth >= 1)
        {
            origin.add(parentOrigin);
        }

        ModelGroup group = new ModelGroup(uniqueId(parse, parent, getString(def, "id", null)));

        parent.children.add(group);
        setupBone(group, def, origin);
        readContent(parse, group, def, origin, origin, depth + 1);
    }

    /**
     * Merge an external .jpm part model (referenced via {@code "model"}) into the entry, letting the
     * entry's own keys win.
     *
     * <p>A reference that resolves to nothing leaves the entry as it stands — a bone with no boxes and
     * no animation — and says so. It is the one quirk that costs a whole limb rather than a detail, and
     * it looks exactly like a model that was drawn that way: the {@code player cem+} pack asks for a
     * {@code player_face.jpm} it does not ship, and the face simply was not there.</p>
     */
    static JsonObject resolveEntry(JsonObject entry, JemModelParser.JpmResolver resolver, JemParse parse)
    {
        if (!entry.has("model") || resolver == null)
        {
            return entry;
        }

        String reference = entry.get("model").getAsString();
        JsonObject jpm = resolver.apply(reference);

        if (jpm == null)
        {
            parse.warn("part model \"" + reference + "\" was not found - the part it belongs to is empty");

            return entry;
        }

        JsonObject merged = jpm.deepCopy();

        for (Map.Entry<String, JsonElement> e : entry.entrySet())
        {
            merged.add(e.getKey(), e.getValue());
        }

        return merged;
    }

    static void setupBone(ModelGroup group, JsonObject def, Vector3f pivot)
    {
        group.initial.translate.set(pivot);

        if (def.has("rotate"))
        {
            JsonArray r = def.getAsJsonArray("rotate");

            group.initial.rotate.set(r.get(0).getAsFloat(), r.get(1).getAsFloat(), r.get(2).getAsFloat());
        }

        group.current.copy(group.initial);
    }

    /**
     * The coordinate handling assumes the standard {@code "xy"} inversion (see the class javadoc). A
     * definition that declares anything else — or nothing at all, which OptiFine reads as no inversion
     * — is laid out in a different convention and may come out flipped; say so instead of guessing.
     */
    static void checkInvertAxis(JemParse parse, JsonObject def)
    {
        String invert = getString(def, "invertAxis", "");

        if (!invert.equalsIgnoreCase("xy"))
        {
            parse.warn("invertAxis \"" + invert + "\" is read as the standard \"xy\" - the part may come out flipped");
        }
    }

    /** Whether the definition mirrors its box UV horizontally ({@code mirrorTexture "u"}); a vertical mirror has no counterpart here. */
    static boolean mirrorTexture(JemParse parse, JsonObject def)
    {
        String mirror = getString(def, "mirrorTexture", "").toLowerCase();

        if (mirror.contains("v"))
        {
            parse.warn("mirrorTexture \"" + mirror + "\": only \"u\" is mirrored, the vertical mirror is ignored");
        }

        return mirror.contains("u");
    }

    static Vector3f translate(JsonObject def)
    {
        if (!def.has("translate"))
        {
            return new Vector3f();
        }

        JsonArray t = def.getAsJsonArray("translate");

        return new Vector3f(t.get(0).getAsFloat(), t.get(1).getAsFloat(), t.get(2).getAsFloat());
    }

    /**
     * A bone id no other bone of this parse has: a duplicate gets underscores appended. Animations
     * address bones by id, so a renamed duplicate is out of their reach - hence the warning.
     */
    static String uniqueId(JemParse parse, ModelGroup parent, String id)
    {
        if (id == null)
        {
            id = parent.id + "_sub" + parent.children.size();
        }

        String unique = id;

        while (!parse.ids.add(unique))
        {
            unique = unique + "_";
        }

        if (!unique.equals(id))
        {
            parse.warn("duplicate bone id \"" + id + "\" renamed to \"" + unique + "\" - animations addressing it drive the first one");
        }

        return unique;
    }

    static String getString(JsonObject object, String key, String fallback)
    {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : fallback;
    }
}
