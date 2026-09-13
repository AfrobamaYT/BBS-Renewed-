package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.data.types.MapType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Die Regler eines Control-Rigs — das, was der Animator bedient.
 *
 * <p>In Rigify sind das Custom Properties an einem Eigenschaften-Knochen je Glied
 * ({@code upper_arm_parent.L}, {@code thigh_parent.L}): IK_FK, IK_Stretch, IK_parent,
 * pole_vector, pole_parent, FK_limb_follow. {@link RigTreiber} verdrahtet sie auf
 * Constraint-Influence; hier liegen nur die Werte und ihre Beschreibung.
 *
 * <p>Die Grenzen gehoeren dazu: aus einer nackten {@code 1} ist nicht zu erkennen, ob
 * das ein Schieber 0..1 oder eine Aufzaehlung 0..6 ist — ein Bedienfeld kann den
 * Regler ohne das nicht zeigen.
 */
public final class RigRegler
{
    private final Map<String, Map<String, Float>> werte = new HashMap<>();
    private final Map<String, Map<String, MapType>> schema = new LinkedHashMap<>();

    public void lade(MapType regler)
    {
        if (regler == null)
        {
            return;
        }

        for (String bone : regler.keys())
        {
            MapType props = regler.getMap(bone);

            if (props == null)
            {
                continue;
            }

            Map<String, Float> w = new HashMap<>();
            Map<String, MapType> s = new LinkedHashMap<>();

            for (String prop : props.keys())
            {
                MapType d = props.getMap(prop);

                if (d != null)
                {
                    w.put(prop, d.getFloat("wert", 0F));
                    s.put(prop, d);
                }
            }

            if (!w.isEmpty())
            {
                this.werte.put(bone, w);
                this.schema.put(bone, s);
            }
        }
    }

    public boolean leer()
    {
        return this.werte.isEmpty();
    }

    /** Knochen mit Reglern, in Ladereihenfolge — die Gliederung fuers Bedienfeld. */
    public List<String> knochen()
    {
        return new ArrayList<>(this.schema.keySet());
    }

    public Map<String, MapType> reglerVon(String bone)
    {
        return this.schema.getOrDefault(bone, Map.of());
    }

    /** Gibt es diesen Regler? Ein Treiber auf einen unbekannten Regler ist ein Befund. */
    public boolean hat(String bone, String prop)
    {
        Map<String, Float> w = this.werte.get(bone);

        return w != null && w.containsKey(prop);
    }

    /** Die Art des Reglers (float, int, bool, text) — Blender schreibt getriebene Werte typgerecht. */
    public String artVon(String bone, String prop)
    {
        MapType d = this.schema.getOrDefault(bone, Map.of()).get(prop);

        return d == null ? "float" : d.getString("art", "float");
    }

    public float wertVon(String bone, String prop, float ersatz)
    {
        Map<String, Float> w = this.werte.get(bone);
        Float v = w == null ? null : w.get(prop);

        return v == null ? ersatz : v;
    }

    /** Der Animator dreht am Regler. Wirkt ab dem naechsten Bild auf alle Treiber. */
    public boolean setze(String bone, String prop, float wert)
    {
        Map<String, Float> w = this.werte.get(bone);

        if (w == null || !w.containsKey(prop))
        {
            return false;
        }

        w.put(prop, wert);

        return true;
    }
}
