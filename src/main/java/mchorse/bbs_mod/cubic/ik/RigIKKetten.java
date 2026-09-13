package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.bobj.BOBJArmature;
import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.cubic.ik.blender.IKBOBJBruecke;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Die IK-Constraints aus {@code rig.json}, zu Blenders IK-BAEUMEN zusammengesetzt
 * ({@code initialize_posetree}, iksolver_plugin.cc) und mit dem portierten Solver geloest.
 *
 * ⛔ Blender loest IK als Teil der Pose-Auswertung: erst wenn ALLE Spitzen einer Gruppe sich
 * ueberlappender Ketten gerechnet sind ({@code loese_bei} aus dem Export), werden die Baeume je
 * Wurzel gebaut ({@link RigIKBaumbau}) und in Listenreihenfolge ausgefuehrt. Je Knochen zaehlt nur der ERSTE aktive
 * IK-Constraint; abgeschaltete werden als Ziele mit Einfluss 0 mitgefuehrt (wie in Blender).
 */
public final class RigIKKetten
{
    /** Ein IK-Constraint, wie er im Stapel seines Spitzenknochens steht. */
    public record Zwang(String spitze, MapType daten)
    {}

    private final Map<String, List<Zwang>> proSpitze = new HashMap<>();
    /** {@code loese_bei} -> Spitzen der Gruppe (Auswertungsreihenfolge). */
    private final Map<String, List<String>> gruppen = new HashMap<>();
    private final Map<String, IKBOBJBruecke.Gelenk> gelenke = new HashMap<>();
    /** Knochen -> (ik_feld -> Treiber): Auto-Rig Pro faehrt ik_stretch ueber Regler. */
    private final Map<String, MapType> gelenkTreiber = new HashMap<>();

    private RigIKKetten()
    {}

    public static RigIKKetten lesen(ListType mechanik, MapType ikGelenke, ListType ikGruppen)
    {
        RigIKKetten k = new RigIKKetten();

        for (int i = 0; mechanik != null && i < mechanik.size(); i++)
        {
            MapType c = mechanik.getMap(i);

            /* ⛔ Auch der abgeschaltete Zweig kommt mit: Rigifys Pol-Umschalter sind ZWEI
             * IK-Constraints am selben Knochen, `pole_vector` stummt einen davon per Treiber.
             * Beim Laden zu filtern friert die Wahl auf den Exportstand ein (gemessen 2026-08-16). */
            if ("IK".equals(c.getString("typ")))
            {
                k.proSpitze.computeIfAbsent(c.getString("bone"), x -> new ArrayList<>()).add(new Zwang(c.getString("bone"), c));
            }
        }

        for (String name : ikGelenke == null ? List.<String>of() : ikGelenke.keys())
        {
            MapType g = ikGelenke.getMap(name);

            k.gelenke.put(name, new IKBOBJBruecke.Gelenk(bools(g.getList("lock")), doubles(g.getList("stiff")),
                bools(g.getList("grenze")), doubles(g.getList("min")), doubles(g.getList("max")),
                g.getFloat("stretch", 0F)));

            if (g.has("treiber"))
            {
                k.gelenkTreiber.put(name, g.getMap("treiber"));
            }
        }

        if (ikGruppen != null && ikGruppen.size() > 0)
        {
            for (int i = 0; i < ikGruppen.size(); i++)
            {
                MapType g = ikGruppen.getMap(i);
                List<String> tips = new ArrayList<>();
                ListType t = g.getList("tips");

                for (int j = 0; t != null && j < t.size(); j++)
                {
                    tips.add(t.getString(j));
                }

                k.gruppen.put(g.getString("loese_bei"), tips);
            }
        }
        else
        {
            /* Aeltere Exportdatei ohne Gruppen: jede Spitze loest fuer sich. */
            for (String spitze : k.proSpitze.keySet())
            {
                k.gruppen.put(spitze, List.of(spitze));
            }
        }

        return k;
    }

    private static boolean[] bools(ListType l)
    {
        boolean[] r = new boolean[3];

        for (int i = 0; l != null && i < 3 && i < l.size(); i++)
        {
            r[i] = l.getBool(i);
        }

        return r;
    }

    private static double[] doubles(ListType l)
    {
        double[] r = new double[3];

        for (int i = 0; l != null && i < 3 && i < l.size(); i++)
        {
            r[i] = l.getDouble(i);
        }

        return r;
    }

    /** Loest an diesem Knochen eine Gruppe aus? */
    public boolean hat(String bone)
    {
        return this.gruppen.containsKey(bone);
    }

    /**
     * Der Pol-Control, der an {@code bone} GERADE wirkt — der des ersten aktiven IK-Constraints,
     * oder {@code null}. Der Snap schwenkt danach anders (Rigifys zwei Zweige).
     */
    public String polVon(String bone, RigTreiber treiber)
    {
        for (Zwang z : this.proSpitze.getOrDefault(bone, List.of()))
        {
            MapType c = z.daten();

            if (treiber.abgeschaltet(c) || treiber.einfluss(c) == 0F)
            {
                continue;
            }

            String pol = c.getString("pol");

            return pol == null || pol.isEmpty() ? null : pol;
        }

        return null;
    }

    /**
     * Baut die Baeume der Gruppe, die an {@code loeseBei} faellig ist, und loest sie.
     *
     * @return die Knochen, deren Matrizen dabei neu geschrieben wurden
     */
    public List<BOBJBone> loese(BOBJArmature armature, String loeseBei, RigTreiber treiber, RigRahmen rahmen)
    {
        List<String> tips = this.gruppen.get(loeseBei);

        if (tips == null)
        {
            return List.of();
        }

        Set<String> spitzen = new HashSet<>(tips);
        Map<BOBJBone, List<IKBOBJBruecke.Baum>> proWurzel = new LinkedHashMap<>();
        Set<BOBJBone> kette = new HashSet<>();

        /* iksolver_initialize_tree laeuft ueber die Kanaele in Knochenreihenfolge. */
        for (BOBJBone tip : armature.orderedBones)
        {
            if (spitzen.contains(tip.name))
            {
                RigIKBaumbau.initialisiere(armature, tip, this.proSpitze.get(tip.name), proWurzel, kette, treiber, rahmen);
            }
        }

        List<BOBJBone> wurzeln = new ArrayList<>(proWurzel.keySet());

        wurzeln.sort((a, b) -> a.index - b.index);

        List<BOBJBone> betroffen = new ArrayList<>();

        for (BOBJBone wurzel : wurzeln)
        {
            for (IKBOBJBruecke.Baum baum : proWurzel.get(wurzel))
            {
                for (BOBJBone b : baum.pchan)
                {
                    if (!betroffen.contains(b))
                    {
                        betroffen.add(b);
                    }
                }

                if (baum.wirkt())
                {
                    IKBOBJBruecke.loese(rahmen, baum, RigIKGelenke.fuer(this.gelenke, this.gelenkTreiber, baum, treiber));
                }
            }
        }

        return betroffen;
    }
}
