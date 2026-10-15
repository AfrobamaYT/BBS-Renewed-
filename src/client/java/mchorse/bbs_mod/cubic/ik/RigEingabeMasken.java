package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.bobj.BOBJArmature;
import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Axes unconditionally overwritten by channel drivers before a bone is evaluated. */
final class RigEingabeMasken
{
    private RigEingabeMasken() {}

    static int[] bauen(RigMechanik rig, BOBJArmature armature)
    {
        int[] masks = new int[armature.orderedBones.size()];
        Map<String, Integer> order = new HashMap<>();

        for (int i = 0; i < rig.reihenfolge().size(); i++)
        {
            order.put(rig.reihenfolge().get(i), i);
        }

        if (rig.ruhelage)
        {
            return masks;
        }

        for (Map.Entry<String, List<MapType>> entry : rig.kanalTreiber.entrySet())
        {
            BOBJBone bone = armature.bones.get(entry.getKey());

            if (bone == null || bone.index < 0 || bone.index >= masks.length)
            {
                continue;
            }

            /* A driver may read its own (or an as-yet unevaluated) raw channel.
             * Such a channel is not redundant even though kanalSetzen writes it later. */
            if (entry.getValue().stream().anyMatch(k -> !treiberVorherSicher(rig, k, entry.getKey(), order)))
            {
                continue;
            }

            for (MapType channel : entry.getValue())
            {
                int axis = Math.max(0, Math.min(2, channel.getInt("achse", 0)));
                String kind = channel.getString("art");

                if ("kanal_skala".equals(kind))
                {
                    masks[bone.index] |= 1 << (axis + 3);
                }
                else if ("kanal_ort".equals(kind))
                {
                    masks[bone.index] |= 1 << axis;
                }
                else if (!"QUATERNION".equals(channel.getString("modus", "XYZ")))
                {
                    masks[bone.index] |= 1 << (axis + 6) | 1 << 9;
                }
            }
        }

        for (int mask : masks)
        {
            if (mask != 0)
            {
                return masks;
            }
        }

        return new int[0];
    }

    private static boolean treiberVorherSicher(RigMechanik rig, MapType channel,
        String destination, Map<String, Integer> order)
    {
        MapType driver = channel.getMap("treiber");

        if (driver == null || !driver.has("typ"))
        {
            return true;
        }

        ListType vars = driver.getList("vars");

        for (int i = 0; vars != null && i < vars.size(); i++)
        {
            MapType variable = vars.getMap(i);

            if (!"SINGLE_PROP".equals(variable.getString("typ", "SINGLE_PROP")))
            {
                return false;
            }

            ListType targets = variable.getList("ziele");
            MapType target = targets == null || targets.size() == 0
                ? variable : targets.getMap(0);
            String source = target.getString("bone", "");
            String property = target.getString("prop", "");

            if (!property.isEmpty())
            {
                if (!rig.treiber.regler.hat(source, property)) return false;
                continue;
            }

            String channelName = target.getString("kanal", "");
            Integer sourceOrder = order.get(source);
            Integer destinationOrder = order.get(destination);

            if (channelName.isEmpty() || sourceOrder == null || destinationOrder == null
                || sourceOrder >= destinationOrder)
            {
                return false;
            }
        }

        return true;
    }
}
