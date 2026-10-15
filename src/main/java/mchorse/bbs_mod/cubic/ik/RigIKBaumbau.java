package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.bobj.BOBJArmature;
import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.cubic.ik.blender.IKBOBJBruecke;
import mchorse.bbs_mod.data.types.MapType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Blenders {@code initialize_posetree} (iksolver_plugin.cc): aus den IK-Constraints einer Spitze
 * die Kette sammeln, mit bestehenden Baeumen derselben Wurzel verschmelzen und je Constraint das
 * Ziel samt Pol in Blenders Weltraum anlegen. Je Knochen zaehlt nur der ERSTE aktive Constraint.
 */
final class RigIKBaumbau
{
    private RigIKBaumbau()
    {}

    /** {@code initialize_posetree} fuer eine Spitze — wortgetreu samt POSE_CHAIN-Markierung. */
    static void initialisiere(BOBJArmature arm, BOBJBone tip0, List<RigIKKetten.Zwang> cons,
                              Map<BOBJBone, List<IKBOBJBruecke.Baum>> proWurzel, Set<BOBJBone> kette,
                              RigTreiber treiber, RigRahmen rahmen)
    {

        if (cons == null)
        {
            return;
        }

        /* Blenders `pchan_tip` lebt ueber die ganze Constraint-Schleife — Absicht oder nicht,
         * so rechnet es. */
        BOBJBone pchanTip = tip0;

        for (RigIKKetten.Zwang con : cons)
        {
            MapType c = con.daten();
            String ziel = c.getString("ziel", "");

            /* find_ik_constraints: ohne Zielknochen (auch das Armature-Objekt selbst) oder
             * ungueltig kommt der Constraint gar nicht in den Baum. */
            if (ziel == null || ziel.isEmpty() || arm.bones.get(ziel) == null || c.getBool("ungueltig", false))
            {
                continue;
            }

            treiber.felderSetzen(c);

            if (!RigZwangBasis.flag(c, "use_tail", null, true) && pchanTip.parentBone != null)
            {
                pchanTip = pchanTip.parentBone;
            }

            int rootbone = Math.round(c.getFloat("kette", RigZwangBasis.zahl(c, "chain_count", null, 0F)));
            List<BOBJBone> chanlist = new ArrayList<>();
            BOBJBone root = null;
            int segcount = 0;

            for (BOBJBone cur = pchanTip; cur != null; cur = cur.parentBone)
            {
                root = cur;
                kette.add(cur);
                chanlist.add(cur);
                segcount++;

                if (segcount == rootbone || segcount > 255)
                {
                    break;
                }
            }

            if (segcount == 0)
            {
                continue;
            }

            List<IKBOBJBruecke.Baum> baeume = proWurzel.computeIfAbsent(root, x -> new ArrayList<>());
            IKBOBJBruecke.Baum tree = null;

            /* "we make tree-IK, unless all existing targets are in this chain" */
            for (IKBOBJBruecke.Baum kandidat : baeume)
            {
                boolean gefunden = false;

                for (IKBOBJBruecke.Ziel z : kandidat.ziele)
                {
                    BOBJBone cur = kandidat.pchan.get(z.tip());

                    if (kette.contains(cur))
                    {
                        kette.remove(cur);
                    }
                    else
                    {
                        gefunden = true;

                        break;
                    }
                }

                if (gefunden)
                {
                    tree = kandidat;

                    break;
                }
            }

            kette.remove(pchanTip);

            float einfluss = treiber.einfluss(c);
            boolean aus = treiber.abgeschaltet(c);
            int iterations = Math.round(treiber.feld(c, "iterations", 500F));
            boolean stretch = c.getBool("dehnen", RigZwangBasis.flag(c, "use_stretch", null, false));
            int tipIndex;

            if (tree == null)
            {
                tree = new IKBOBJBruecke.Baum();
                tree.iterations = iterations;
                tree.stretch = stretch;

                for (int a = 0; a < segcount; a++)
                {
                    tree.pchan.add(chanlist.get(segcount - a - 1));
                    tree.parent.add(a - 1);
                }

                tipIndex = segcount - 1;
                baeume.add(tree);
            }
            else
            {
                tree.iterations = Math.max(iterations, tree.iterations);
                tree.stretch = tree.stretch && !stretch;
                tipIndex = verschmelze(tree, chanlist, segcount);
                baeume.remove(tree);
                baeume.add(tree);
            }

            tree.ziele.add(ziel(arm, c, tipIndex, treiber, rahmen, einfluss, aus));

            /* Per bone only one active IK constraint is supported. */
            if (einfluss != 0F && !aus)
            {
                break;
            }
        }
    }

    /** Der Verschmelzungs-Zweig von {@code initialize_posetree}: gemeinsame Kanaele ueberspringen, Rest anhaengen. */
    private static int verschmelze(IKBOBJBruecke.Baum tree, List<BOBJBone> chanlist, int segcount)
    {
        int tot = tree.pchan.size();
        int size = Math.min(segcount, tot);
        int a = 0;
        int t = 0;

        while (a < size && t < tot)
        {
            for (; t < tot && tree.pchan.get(t) != chanlist.get(segcount - a - 1); t++)
            {}

            if (t >= tot)
            {
                break;
            }

            for (; a < size && t < tot && tree.pchan.get(t) == chanlist.get(segcount - a - 1); a++, t++)
            {}
        }

        int rest = segcount - a;
        int tipIndex = tot + rest - 1;

        if (rest > 0)
        {
            int parent;

            for (parent = Math.max(a - 1, 0); parent < tot; parent++)
            {
                if (tree.pchan.get(parent) == chanlist.get(rest - 1).parentBone)
                {
                    break;
                }
            }

            /* shouldn't happen, but could with dependency cycles */
            if (parent == tot)
            {
                parent = a - 1;
            }

            for (int i = 0; i < rest; i++)
            {
                tree.pchan.add(chanlist.get(rest - i - 1));
                tree.parent.add(tot + i - 1);
            }

            tree.parent.set(tot, parent);
        }

        return tipIndex;
    }

    /** Die Constraint-Felder dieses Bildes samt Ziel- und Polmatrix in Blenders Weltraum. */
    private static IKBOBJBruecke.Ziel ziel(BOBJArmature arm, MapType c, int tip, RigTreiber t, RigRahmen rahmen,
                                    float einfluss, boolean aus)
    {
        float[] eigenRaum = RigZiel.eigenRaum(rahmen, arm, c);
        String raum = c.getString("raum_z", "WORLD");
        float kopfSchwanz = RigZwangBasis.zahl(c, "head_tail", "kopf_schwanz", 0F);
        boolean bbone = RigZwangBasis.flag(c, "use_bbone_shape", null, false);
        BOBJBone zb = arm.bones.get(c.getString("ziel", ""));
        float[] zielWelt = RigZiel.zielMatrix(rahmen, zb, null, eigenRaum, raum, kopfSchwanz, bbone, false);
        float[] polWelt = null;
        String pol = c.getString("pol", "");

        if ((pol != null && !pol.isEmpty()) || c.getBool("pol_objekt", false))
        {
            /* Ein fehlender Pol-Knochen faellt in Blender auf die Objektmatrix zurueck. */
            BOBJBone pb = pol == null || pol.isEmpty() ? null : arm.bones.get(pol);

            polWelt = RigZiel.zielMatrix(rahmen, pb, null, eigenRaum, raum, kopfSchwanz, bbone, false);
        }

        boolean position = RigZwangBasis.flag(c, "use_location", null, true);
        boolean rotation = c.getBool("spitzen_rotation", RigZwangBasis.flag(c, "use_rotation", null, false));
        float gewicht = t.feld(c, "weight", 1F);
        float orient = t.feld(c, "orient_weight", 1F);
        double polWinkel = c.has("treiber_pol_winkel")
            ? RigTreiber.klemme("pole_angle", t.wert(c.getMap("treiber_pol_winkel"), c.getFloat("pol_winkel", 0F)))
            : t.feld(c, "pole_angle", c.getFloat("pol_winkel", 0F));

        return new IKBOBJBruecke.Ziel(tip, zielWelt, polWelt, einfluss, aus, position, rotation, gewicht, orient, polWinkel);
    }
}
