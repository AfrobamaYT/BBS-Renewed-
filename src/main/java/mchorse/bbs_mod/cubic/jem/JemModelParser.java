package mchorse.bbs_mod.cubic.jem;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.math.molang.MolangParser;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * OptiFine CEM models (.jem entity model + .jpm part models) into BBS's {@link Model}, with the
 * coordinates of Blockbench's OptiFine importer: a top-level part's pivot is {@code -translate} and its
 * boxes use their coordinates as they are; a submodel's pivot is its own translate (plus its parent
 * submodel's, two or more levels deep) and its boxes are offset by it; {@code invertAxis "xy"} is
 * assumed, rotations taken as is. The parts' "animations" go into a {@link CemAnimation}; whatever the
 * parser had to work around is reported once per quirk in {@link Result#warnings()}.
 *
 * <p>From BBS FS b44e56c87, 562c23747, 26ca19c49, 98383551e, 07f4b8b65, 4e36c2e12 (Wemppy4/bbs-fs,
 * MIT). A part's content is read in {@link JemTeile}, its boxes in {@link JemBoxes}.</p>
 */
public class JemModelParser
{
    /** Resolves an external {@code "model"} reference (.jpm filename) to its parsed JSON, or null. */
    public interface JpmResolver extends Function<String, JsonObject>
    {}

    /** Result of parsing a .jem: the geometry model, its (possibly empty) procedural CEM animation, and the quirks met on the way. */
    public record Result(Model model, CemAnimation animation, Collection<String> warnings)
    {}

    public static Result parse(JsonObject jem, JpmResolver resolver, MolangParser parser)
    {
        return parse(jem, resolver, parser, CemHierarchy.NONE);
    }

    /** @param hierarchy what the file leaves to the vanilla model: reparenting and pivot overrides, see {@link CemHierarchy}. */
    public static Result parse(JsonObject jem, JpmResolver resolver, MolangParser parser, CemHierarchy hierarchy)
    {
        JemParse parse = new JemParse(new Model(parser), new CemAnimation());
        Model model = parse.model;

        model.textureWidth = 64;
        model.textureHeight = 64;

        if (jem.has("textureSize"))
        {
            JsonArray size = jem.getAsJsonArray("textureSize");

            model.textureWidth = size.get(0).getAsInt();
            model.textureHeight = size.get(1).getAsInt();
        }

        if (!jem.has("models"))
        {
            return parse.result();
        }

        /* Collect entries, merging the multiple model entries that target the same bone (e.g. the
         * five "root" entries of player.jem) into a single bone with several definitions. */
        Map<String, GroupInfo> infos = new LinkedHashMap<>();

        for (JsonElement element : jem.getAsJsonArray("models"))
        {
            if (!element.isJsonObject())
            {
                continue;
            }

            JsonObject entry = JemTeile.resolveEntry(element.getAsJsonObject(), resolver, parse);
            String id = JemTeile.getString(entry, "id", JemTeile.getString(entry, "part", null));

            if (id == null)
            {
                continue;
            }

            infos.computeIfAbsent(id, GroupInfo::new).defs.add(entry);
            parse.animation.sammeln(entry);
        }

        /* The parts' ids are taken before any submodel is named, so a submodel can never shadow a part. */
        parse.ids.addAll(infos.keySet());

        /* Each model entry is a top-level bone (Blockbench keeps the part list flat). */
        for (GroupInfo info : infos.values())
        {
            JsonObject primary = info.defs.get(0);
            Vector3f pivot = JemTeile.translate(primary).negate();

            JemTeile.setupBone(info.group, primary, pivot);
            parse.animation.markPart(info.group);

            for (JsonObject def : info.defs)
            {
                JemTeile.readContent(parse, info.group, def, ZERO, pivot, 0);
            }

            checkAttach(parse, info);
            model.topGroups.add(info.group);

            /* A shell: the file draws nothing in it. Noted now, before the rig hangs other parts on it. */
            if (!hasGeometry(info.group))
            {
                parse.shells.add(info.group);
            }
        }

        applyHierarchy(parse, hierarchy);

        model.initialize();
        parse.animation.setup(model);

        return parse.result();
    }

    /**
     * Lay the vanilla rig over the flat file (see {@link CemHierarchy}): give the parts the file left
     * empty vanilla's rotation points, then reparent flat top-level parts onto their vanilla parent.
     * Geometry is unaffected — BBS composes child bones from their absolute pivots, and a parent with
     * no rest rotation contributes nothing at rest — so a part keeps its rest position while now
     * following the parent's animation. A reparented part is told to the animation as parent-relative:
     * the pack positions it against the parent's rotation point, the way OptiFine composes vanilla
     * children.
     *
     * <p>Only an empty part takes vanilla's pivot. A pack keeps most vanilla parts as empty shells at a
     * zero translate and reads their positions, and what such a shell holds in OptiFine is vanilla's own
     * rotation point: the villager's head is animated about the neck, the magma cube's layers hang off
     * {@code segment4.ty}, the blaze's body cancels {@code stick1.ty}. A part with geometry is placed
     * where its file says — a pack that moved the cow's body rotation into a submodel meant it. An
     * empty part hung on a parent stands at vanilla's offset from that parent's pivot, wherever the file
     * put the parent — the guardian's spikes hang off a head the pack rotates about its own point — and
     * so agrees with the vanilla frame the program is seeded from.</p>
     */
    private static void applyHierarchy(JemParse parse, CemHierarchy hierarchy)
    {
        if (hierarchy == null || hierarchy.isEmpty())
        {
            return;
        }

        Map<String, ModelGroup> byId = new LinkedHashMap<>();

        for (ModelGroup group : parse.model.topGroups)
        {
            byId.put(group.id, group);
        }

        for (Map.Entry<String, String> entry : hierarchy.parents.entrySet())
        {
            ModelGroup child = byId.get(entry.getKey());
            ModelGroup parent = byId.get(entry.getValue());

            if (child != null && parent != null && child != parent)
            {
                parse.model.topGroups.remove(child);
                parent.children.add(child);
                parse.animation.markParentRelative(child);
            }
        }

        for (ModelGroup group : parse.model.topGroups)
        {
            place(parse, group, null, hierarchy);
        }
    }

    /** Give a shell vanilla's pivot — against the parent it hangs on, or the model — parents before children. */
    private static void place(JemParse parse, ModelGroup group, ModelGroup parent, CemHierarchy hierarchy)
    {
        Vector3f offset = parent == null ? null : hierarchy.offsets.get(group.id);
        Vector3f pivot = offset != null ? new Vector3f(parent.initial.translate).add(offset) : hierarchy.pivots.get(group.id);

        if (pivot != null && parse.shells.contains(group))
        {
            group.initial.translate.set(pivot);
            group.current.copy(group.initial);
        }

        for (ModelGroup child : group.children)
        {
            place(parse, child, group, hierarchy);
        }
    }

    private static final Vector3f ZERO = new Vector3f();

    /**
     * A part marked {@code attach} is added to the vanilla part rather than put in its place, so
     * OptiFine draws it over the vanilla geometry. There is no vanilla geometry here - the pack's own
     * boxes are all there is of the part. That is fine for the one use every pack makes of the flag,
     * a signature hung on the root with no boxes at all, and a loss for a part that has some.
     */
    private static void checkAttach(JemParse parse, GroupInfo info)
    {
        for (JsonObject def : info.defs)
        {
            if (isTrue(def, "attach") && hasGeometry(info.group))
            {
                parse.warn("part \"" + info.group.id + "\" is attached to the vanilla part, whose geometry is not available here - only the pack's own boxes of it are drawn");

                return;
            }
        }
    }

    private static boolean hasGeometry(ModelGroup group)
    {
        if (!group.cubes.isEmpty())
        {
            return true;
        }

        for (ModelGroup child : group.children)
        {
            if (hasGeometry(child))
            {
                return true;
            }
        }

        return false;
    }

    /** A flag, whether written as a boolean or as the string {@code "true"} - packs do both. */
    private static boolean isTrue(JsonObject object, String key)
    {
        return object.has(key) && object.get(key).isJsonPrimitive() && object.get(key).getAsString().equalsIgnoreCase("true");
    }

    /** A bone being assembled: its {@link ModelGroup} and its merged definitions. */
    private static class GroupInfo
    {
        public final ModelGroup group;
        public final List<JsonObject> defs = new ArrayList<>();

        public GroupInfo(String id)
        {
            this.group = new ModelGroup(id);
        }
    }

}
