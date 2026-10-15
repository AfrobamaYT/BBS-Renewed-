package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.cubic.ik.blender.IKBOBJBruecke;
import mchorse.bbs_mod.data.types.MapType;

import java.util.HashMap;
import java.util.Map;

/**
 * Gelenkdaten eines IK-Baums mit eingesetzten getriebenen Feldern ({@code ik_stretch},
 * {@code ik_stiffness_*}, {@code ik_min_*}/{@code ik_max_*}, {@code lock_ik_*}, {@code use_ik_limit_*}) —
 * Auto-Rig Pro faehrt ik_stretch ueber Regler.
 */
final class RigIKGelenke
{
    private RigIKGelenke()
    {}

    /** Gelenkdaten des Baums, getriebene Felder eingesetzt. */
    static Map<String, IKBOBJBruecke.Gelenk> fuer(Map<String, IKBOBJBruecke.Gelenk> gelenke, Map<String, MapType> gelenkTreiber,
                                                  IKBOBJBruecke.Baum baum, RigTreiber treiber)
    {
        if (gelenkTreiber.isEmpty())
        {
            return gelenke;
        }

        Map<String, IKBOBJBruecke.Gelenk> raus = new HashMap<>(gelenke);

        for (BOBJBone b : baum.pchan)
        {
            MapType t = gelenkTreiber.get(b.name);
            IKBOBJBruecke.Gelenk g = gelenke.get(b.name);

            if (t == null || g == null)
            {
                continue;
            }

            boolean[] sperre = g.sperre().clone();
            boolean[] grenze = g.grenze().clone();
            double[] steif = g.steifigkeit().clone();
            double[] min = g.min().clone();
            double[] max = g.max().clone();
            double dehnung = g.dehnung();

            for (String feld : t.keys())
            {
                float v = treiber.wert(t.getMap(feld), Float.NaN);

                if (Float.isNaN(v))
                {
                    continue;
                }

                v = RigTreiber.klemme(feld, v);

                int achse = "xyz".indexOf(feld.charAt(feld.length() - 1));

                if ("ik_stretch".equals(feld))
                {
                    dehnung = v;
                }
                else if (achse >= 0 && feld.startsWith("ik_stiffness_"))
                {
                    steif[achse] = v;
                }
                else if (achse >= 0 && feld.startsWith("ik_min_"))
                {
                    min[achse] = v;
                }
                else if (achse >= 0 && feld.startsWith("ik_max_"))
                {
                    max[achse] = v;
                }
                else if (achse >= 0 && feld.startsWith("lock_ik_"))
                {
                    sperre[achse] = v > RigTreiber.BOOL_SCHWELLE;
                }
                else if (achse >= 0 && feld.startsWith("use_ik_limit_"))
                {
                    grenze[achse] = v > RigTreiber.BOOL_SCHWELLE;
                }
            }

            raus.put(b.name, new IKBOBJBruecke.Gelenk(sperre, steif, grenze, min, max, dehnung));
        }

        return raus;
    }
}
