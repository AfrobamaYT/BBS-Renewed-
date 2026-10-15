package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.bobj.BOBJArmature;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.model.bobj.BOBJModel;
import mchorse.bbs_mod.data.types.MapType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The bones a pose editor offers for a rigged model, as Blender's pose mode offers them: the
 * rig's controls first, in the rig's order, then the other bones; bones a constraint overwrites
 * completely every frame are left out. Rigify keeps those (ORG-, DEF-) on hidden bone
 * collections; here they were listed first and took values that vanished without a word
 * (audit 2026-10-14: ORG-head translate Y 5 stored, the viewport unchanged).
 */
public final class RigPoseListe
{
    private RigPoseListe()
    {}

    public static List<String> ordnen(IModel model, String modellId, List<String> knochen)
    {
        if (!(model instanceof BOBJModel bobj))
        {
            return knochen;
        }

        BOBJArmature armature = bobj.getArmature();
        List<RigCtrl.Eintrag> rig = RigCtrl.fuer(armature, modellId);

        if (rig == null)
        {
            return knochen;
        }

        Set<String> vorne = new LinkedHashSet<>();

        for (RigCtrl.Eintrag e : rig)
        {
            if (knochen.contains(e.name()))
            {
                vorne.add(e.name());
            }
        }

        List<String> aus = new ArrayList<>(vorne);

        for (String k : knochen)
        {
            if (!vorne.contains(k) && !versteckt(modellId).contains(k))
            {
                aus.add(k);
            }
        }

        return aus;
    }

    /** Hidden bones and, per control, Blender's transform locks (loc xyz, rot xyz, scale xyz). */
    private record Daten(Set<String> versteckt, java.util.Map<String, boolean[]> sperren) {}

    /* Lazy (hotswap: new statics are null). */
    private static java.util.Map<String, Daten> proModell;

    /** Beim Models-Reload rufen, wie RigCtrl.leeren. */
    public static void leeren()
    {
        proModell = null;
    }

    /**
     * The bones Blender's pose mode would not offer. With "knochen_lagen" (exporter since
     * 2026-10-14): bones whose bone collections are all outside the rig's UI collections
     * ("lagen") -- Rigify's ORG, MCH and DEF. Without it: bones with a full-influence Copy
     * Transforms in replace mode, whose posed values are overwritten every frame. Read from
     * rig.json, not the armature: its constraint stack exists only once the model was drawn.
     */
    public static Set<String> versteckt(String modellId)
    {
        return daten(modellId).versteckt();
    }

    /**
     * Blender's locks of this control as {loc x,y,z, rot x,y,z, scale x,y,z}, or null when the
     * rig.json has none for it. Rigify's face controls free a single translation axis.
     */
    public static boolean[] sperren(String modellId, String knochen)
    {
        return daten(modellId).sperren().get(knochen);
    }

    private static Daten daten(String modellId)
    {
        if (proModell == null)
        {
            proModell = new java.util.HashMap<>();
        }

        return proModell.computeIfAbsent(modellId == null ? "" : modellId, id ->
        {
            Set<String> aus = new java.util.HashSet<>();
            java.util.Map<String, boolean[]> sperren = new java.util.HashMap<>();

            try (java.io.InputStream rein = mchorse.bbs_mod.BBSMod.getProvider()
                .getAsset(mchorse.bbs_mod.resources.Link.assets("models/" + id + "/rig.json")))
            {
                MapType wurzel = (MapType) mchorse.bbs_mod.data.DataToString.fromString(
                    new String(rein.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                mchorse.bbs_mod.data.types.ListType mechanik = wurzel.getList("mechanik");
                mchorse.bbs_mod.data.types.ListType ctrls = wurzel.getList("ctrls");

                for (int i = 0; ctrls != null && i < ctrls.size(); i++)
                {
                    MapType c = ctrls.getMap(i);

                    if (c.has("sperren"))
                    {
                        MapType l = c.getMap("sperren");
                        boolean[] b = new boolean[9];
                        String[] teile = {"loc", "rot", "scale"};

                        for (int t = 0; t < 3; t++)
                        {
                            for (int a = 0; a < 3; a++)
                            {
                                b[t * 3 + a] = l.getList(teile[t]).getBool(a);
                            }
                        }

                        sperren.put(c.getString("name"), b);
                    }
                }

                if (wurzel.has("knochen_lagen"))
                {
                    Set<String> ui = new java.util.HashSet<>();
                    mchorse.bbs_mod.data.types.ListType lagen = wurzel.getList("lagen");

                    for (int i = 0; lagen != null && i < lagen.size(); i++)
                    {
                        ui.add(lagen.getMap(i).getString("name"));
                    }

                    MapType proKnochen = wurzel.getMap("knochen_lagen");

                    for (String knochen : proKnochen.keys())
                    {
                        mchorse.bbs_mod.data.types.ListType colls = proKnochen.getList(knochen);
                        boolean sichtbar = colls.size() == 0;

                        for (int i = 0; i < colls.size() && !sichtbar; i++)
                        {
                            sichtbar = ui.contains(colls.getString(i));
                        }

                        if (!sichtbar)
                        {
                            aus.add(knochen);
                        }
                    }

                    return new Daten(aus, sperren);
                }

                for (int i = 0; mechanik != null && i < mechanik.size(); i++)
                {
                    MapType c = mechanik.getMap(i);

                    if (c.getString("typ").equals("COPY_TRANSFORMS") && !c.getBool("aus", false)
                        && c.getFloat("einfluss", 0F) >= 0.999F && c.getString("mischung", "REPLACE").equals("REPLACE"))
                    {
                        aus.add(c.getString("bone"));
                    }
                }
            }
            catch (Exception keinRig)
            {
                /* No rig.json, or one without mechanik: nothing is overwritten. */
            }

            return new Daten(aus, sperren);
        });
    }
}
