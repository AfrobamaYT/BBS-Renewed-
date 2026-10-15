package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.bobj.BOBJArmature;
import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Conservative dependency closure for property-only pose updates. The native evaluation order is retained. */
public final class RigDependencyPlan
{
    public record Property(String bone, String name) {}
    private static final Set<String> CONSTRAINTS = Set.of("COPY_TRANSFORMS", "COPY_LOCATION", "COPY_ROTATION", "COPY_SCALE",
        "STRETCH_TO", "ARMATURE", "DAMPED_TRACK", "TRACK_TO", "LOCKED_TRACK", "LIMIT_ROTATION", "LIMIT_LOCATION",
        "LIMIT_SCALE", "LIMIT_DISTANCE", "MAINTAIN_VOLUME", "PIVOT", "ACTION", "TRANSFORM", "FLOOR", "CHILD_OF", "IK");
    private final BOBJArmature armature;
    private final BitSet[] dependents;
    private final Map<Property, BitSet> properties = new HashMap<>();
    private final Map<String, Integer> order = new HashMap<>();
    private final BitSet backwardReaders = new BitSet();
    private boolean safe = true;

    public RigDependencyPlan(BOBJArmature armature, MapType data)
    {
        this.armature = armature;
        this.dependents = new BitSet[armature.orderedBones.size()];
        for (int i = 0; i < this.dependents.length; i++) this.dependents[i] = new BitSet();
        ListType evaluation = data.getList("auswertung");
        for (int i = 0; evaluation != null && i < evaluation.size(); i++) this.order.put(evaluation.getString(i), i);
        for (BOBJBone bone : armature.orderedBones)
        {
            if (bone.index < 0 || bone.index >= this.dependents.length || !this.order.containsKey(bone.name)) this.safe = false;
            if (bone.parentBone != null) this.edge(bone.parentBone.name, bone.name, false);
        }
        ListType constraints = data.getList("mechanik");
        Map<String, List<MapType>> ik = new HashMap<>();
        for (int i = 0; constraints != null && i < constraints.size(); i++)
        {
            MapType c = constraints.getMap(i);
            String owner = c.getString("bone");
            String type = c.getString("typ");
            if (!CONSTRAINTS.contains(type)) this.safe = false;
            if ("IK".equals(type)) ik.computeIfAbsent(owner, key -> new ArrayList<>()).add(c);
            this.scan(c, owner, !"IK".equals(type));
        }
        for (String channel : List.of("kanal_treiber", "bbone_treiber"))
        {
            ListType entries = data.getList(channel);
            for (int i = 0; entries != null && i < entries.size(); i++)
            {
                MapType entry = entries.getMap(i);
                this.scan(entry, entry.getString("bone"), true);
            }
        }
        MapType joints = data.getMap("ik_gelenke");
        for (String bone : joints == null ? List.<String>of() : joints.keys()) this.scan(joints.getMap(bone), bone, false);
        ListType groups = data.getList("ik_gruppen");
        if (!ik.isEmpty() && (groups == null || groups.size() == 0)) this.safe = false;
        for (int i = 0; groups != null && i < groups.size(); i++) this.group(groups.getMap(i), ik);
    }

    private void edge(String source, String destination, boolean ordered)
    {
        BOBJBone from = this.armature.bones.get(source), to = this.armature.bones.get(destination);
        if (from == null || to == null) return;
        this.dependents[from.index].set(to.index);
        if (ordered && this.order.getOrDefault(source, Integer.MAX_VALUE) > this.order.getOrDefault(destination, -1))
            this.backwardReaders.set(to.index);
    }

    private void variable(MapType variable, String owner, boolean ordered)
    {
        String type = variable.getString("typ", "SINGLE_PROP");
        ListType targets = variable.getList("ziele");
        if (!Set.of("SINGLE_PROP", "TRANSFORMS", "ROTATION_DIFF", "LOC_DIFF").contains(type)) this.safe = false;
        int count = targets == null || targets.size() == 0 ? 1 : targets.size();
        for (int i = 0; i < count; i++)
        {
            MapType target = targets == null || targets.size() == 0 ? variable : targets.getMap(i);
            String property = target.getString("prop");
            if ("SINGLE_PROP".equals(type) && property != null && !property.isEmpty())
            {
                BOBJBone bone = this.armature.bones.get(owner);
                if (bone != null) this.properties.computeIfAbsent(new Property(target.getString("bone"), property), key -> new BitSet()).set(bone.index);
            }
            else this.edge(target.getString("bone"), owner, ordered);
        }
    }

    private void scan(MapType data, String owner, boolean ordered)
    {
        if (data == null) return;
        if (data.getBool("use_bbone_shape", false)) this.safe = false;
        if (data.has("vars"))
        {
            String expression = data.getString("ausdruck", "");
            if (expression != null && expression.contains("frame")) this.safe = false;
            ListType variables = data.getList("vars");
            for (int i = 0; variables != null && i < variables.size(); i++) this.variable(variables.getMap(i), owner, ordered);
        }
        for (String key : data.keys())
        {
            if (key.equals("vars")) continue;
            if (Set.of("ziel", "pol", "raum_bone", "bone").contains(key)) this.edge(data.getString(key), owner, ordered);
            Object value = data.get(key);
            if (value instanceof MapType map) this.scan(map, owner, ordered);
            if (value instanceof ListType list)
                for (int i = 0; i < list.size(); i++) if (list.get(i) instanceof MapType map) this.scan(map, owner, ordered);
        }
    }

    private void group(MapType group, Map<String, List<MapType>> constraints)
    {
        BitSet members = new BitSet();
        String trigger = group.getString("loese_bei");
        BOBJBone triggerBone = this.armature.bones.get(trigger);
        if (triggerBone == null) { this.safe = false; return; }
        members.set(triggerBone.index);
        ListType tips = group.getList("tips");
        for (int i = 0; tips != null && i < tips.size(); i++)
        {
            String name = tips.getString(i);
            BOBJBone tip = this.armature.bones.get(name);
            if (tip == null) { this.safe = false; continue; }
            members.set(tip.index);
            for (MapType constraint : constraints.getOrDefault(name, List.of()))
            {
                if (!RigZwangBasis.flag(constraint, "use_tail", null, true) && tip.parentBone != null) tip = tip.parentBone;
                int length = Math.round(constraint.getFloat("kette", RigZwangBasis.zahl(constraint, "chain_count", null, 0F)));
                int count = 0;
                for (BOBJBone bone = tip; bone != null; bone = bone.parentBone)
                {
                    members.set(bone.index);
                    if (++count == length || count > 255) break;
                }
            }
        }
        ListType exported = group.getList("knochen");
        for (int i = 0; exported != null && i < exported.size(); i++)
        {
            BOBJBone bone = this.armature.bones.get(exported.getString(i));
            if (bone != null) members.set(bone.index);
        }
        for (int bone = members.nextSetBit(0); bone >= 0; bone = members.nextSetBit(bone + 1)) this.dependents[bone].or(members);
    }

    public BitSet affected(List<Property> changed)
    {
        if (!this.safe) return null;
        BitSet result = new BitSet();
        for (Property property : changed)
        {
            BitSet users = this.properties.get(property);
            if (users != null) result.or(users);
        }
        BitSet pending = (BitSet) result.clone();
        while (!pending.isEmpty())
        {
            int bone = pending.nextSetBit(0);
            pending.clear(bone);
            BitSet next = (BitSet) this.dependents[bone].clone();
            next.andNot(result);
            result.or(next);
            pending.or(next);
        }
        return result.intersects(this.backwardReaders) ? null : result;
    }
}
