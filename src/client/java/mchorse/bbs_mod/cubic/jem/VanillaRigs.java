package mchorse.bbs_mod.cubic.jem;

import net.minecraft.client.model.geom.LayerDefinitions;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The part hierarchy of Minecraft's own entity models, read straight off the game.
 *
 * <p>A {@code .jem} lists every part of an entity flat, but OptiFine hangs each one on the matching
 * vanilla bone, so a hat follows the head and a saddle the body although the file says nothing about
 * it; without that, forty-two of Fresh Animations' hundred and twenty-eight models came apart.
 * {@link LayerDefinitions#createRoots()} hands over every model the game knows, built by plain code —
 * no resources, no world — and {@link LayerDefinition#bakeRoot()} turns one into a fresh tree of its
 * own, so nothing here touches the parts a renderer draws with. The layer an entity's own model sits
 * under is {@code (minecraft:cow, "main")}, so the entity's name is the key with no table to write.</p>
 *
 * <p>The rotation points come along for the parts a pack leaves <em>empty</em> (see
 * {@link JemModelParser}). From BBS FS f19a5624a, 07f4b8b65 (Wemppy4/bbs-fs, MIT).</p>
 */
public class VanillaRigs
{
    /** The layer an entity's own model is registered under; the rest dress it (armour, saddles, hats) */
    private static final String MAIN = "main";

    /** The height of an entity model's origin above the ground: vanilla counts pixels down from it, BBS up from the ground */
    private static final float Y_OFFSET = 24F;

    private static Map<String, CemHierarchy> rigs;

    /** The vanilla rig of an entity by its plain name ({@link CemNames#entity}), or {@link CemHierarchy#NONE} */
    public static synchronized CemHierarchy of(String entity)
    {
        if (rigs == null)
        {
            rigs = build();
        }

        return rigs.getOrDefault(entity, CemHierarchy.NONE);
    }

    /** Forget the trees, so a resource reload that swapped a mod's models is picked up */
    public static synchronized void clear()
    {
        rigs = null;
    }

    private static Map<String, CemHierarchy> build()
    {
        Map<String, CemHierarchy> rigs = new HashMap<>();

        for (Map.Entry<ModelLayerLocation, LayerDefinition> entry : LayerDefinitions.createRoots().entrySet())
        {
            ModelLayerLocation layer = entry.getKey();

            if (!layer.layer().equals(MAIN))
            {
                continue;
            }

            Map<String, String> parents = new LinkedHashMap<>();
            Map<String, Vector3f> pivots = new LinkedHashMap<>();
            Map<String, Vector3f> offsets = new LinkedHashMap<>();
            String name = layer.model().getPath();

            try
            {
                collect(entry.getValue().bakeRoot(), null, parents, pivots, offsets, CemPartNames.of(name), new Vector3f());
            }
            catch (Exception e)
            {
                /* One model that will not build must not cost every other entity its rig */
                System.err.println("Vanilla rig of " + layer.model() + " could not be read: " + e);

                continue;
            }

            if (!pivots.isEmpty())
            {
                rigs.put(name, new CemHierarchy(parents, pivots, offsets));
            }
        }

        return rigs;
    }

    /**
     * Note every part's rest pivot, and every part below the root against the part it hangs on; the
     * parts directly under the root get no parent, as they are top level in the file too. Each is noted
     * under OptiFine's name and, where free, vanilla's — the two vocabularies cross (the bee's
     * {@code body} is vanilla's {@code bone}), so OptiFine's goes first. Vanilla's pivots are local to
     * the parent, Y down from the top of a 24-pixel entity, X to the entity's left; BBS keeps them
     * absolute, Y up from the ground, X mirrored. {@code origin} carries the parent's absolute pivot.
     */
    private static void collect(ModelPart part, String name, Map<String, String> parents, Map<String, Vector3f> pivots, Map<String, Vector3f> offsets, CemPartNames names, Vector3f origin)
    {
        for (Map.Entry<String, ModelPart> entry : part.children.entrySet())
        {
            String child = entry.getKey();
            ModelPart childPart = entry.getValue();
            Vector3f absolute = new Vector3f(origin).add(childPart.x, childPart.y, childPart.z);
            Vector3f pivot = new Vector3f(-absolute.x, Y_OFFSET - absolute.y, absolute.z);
            Vector3f offset = new Vector3f(-childPart.x, -childPart.y, childPart.z);

            pivots.putIfAbsent(names.optifine(child), pivot);
            pivots.putIfAbsent(child, pivot);
            offsets.putIfAbsent(names.optifine(child), offset);
            offsets.putIfAbsent(child, offset);

            if (name != null)
            {
                parents.putIfAbsent(names.optifine(child), names.optifine(name));
                parents.putIfAbsent(child, name);
            }

            collect(childPart, child, parents, pivots, offsets, names, absolute);
        }
    }
}
