package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.bobj.BOBJBasisMathe;
import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.pose.Transform;

import java.util.List;
import static mchorse.bbs_mod.cubic.ik.RigZwangBasis.*;

/**
 * Blenders Copy Location / Rotation / Scale / Transforms ({@code constraint.cc}: loclike_,
 * rotlike_, sizelike_, translike_evaluate), Mischmodi eingeschlossen. Besitzer und Ziel wie in
 * {@link RigZwangBasis}.
 */
public final class RigZwangKopie
{
    private RigZwangKopie()
    {}

    /** {@code loclike_evaluate}. */
    public static void copyLocation(float[] cob, Ziel ziel, MapType c)
    {
        float[] offset = flag(c, "use_offset", "versatz", false) ? RigMathe.ort(cob) : new float[3];
        String[] achsen = {"x", "y", "z"};

        for (int i = 0; i < 3; i++)
        {
            if (!flag(c, "use_" + achsen[i], "nutz_" + achsen[i], true))
            {
                continue;
            }

            float v = ziel.matrix()[12 + i];

            if (flag(c, "invert_" + achsen[i], "inv_" + achsen[i], false))
            {
                v = -v;
            }

            cob[12 + i] = v + offset[i];
        }
    }

    /** {@code rotlike_evaluate}. */
    public static void copyRotation(float[] cob, Ziel ziel, MapType c, Besitzer b)
    {
        float[] oldrot = new float[9];
        float[] size = new float[3];
        float[] loc = RigZerlegung.zerlege(cob, oldrot, size);
        String rotOrder = ordnung(c, "euler_order", b);
        float[] obeul = RigZerlegung.euler(cob, rotOrder);
        float[] mat = RigMathe.kopie(ziel.matrix());

        BOBJBasisMathe.orthogonalisiereUmY(mat, true);

        float[] eul = RigZerlegung.eulerNahe(mat, obeul, rotOrder);
        String mix = text(c, "mix_mode", "mischung", "REPLACE");
        boolean legacyOffset = "OFFSET".equals(mix);
        float[] defeul = legacyOffset || "REPLACE".equals(mix) ? obeul.clone() : new float[3];
        String[] achsen = {"x", "y", "z"};

        for (int i = 0; i < 3; i++)
        {
            if (!flag(c, "use_" + achsen[i], "nutz_" + achsen[i], true))
            {
                eul[i] = defeul[i];
            }
            else
            {
                if (legacyOffset)
                {
                    RigEuler.drehen(eul, rotOrder, i, obeul[i]);
                }

                if (flag(c, "invert_" + achsen[i], "inv_" + achsen[i], false))
                {
                    eul[i] = -eul[i];
                }
            }
        }

        if ("ADD".equals(mix))
        {
            for (int i = 0; i < 3; i++)
            {
                eul[i] += obeul[i];
            }
        }

        RigEuler.vertraeglich(eul, obeul);

        float[] newrot = RigEuler.zuMatrix(eul, rotOrder);

        if ("BEFORE".equals(mix))
        {
            newrot = RigMatrix3.mul3(newrot, oldrot);
        }
        else if ("AFTER".equals(mix))
        {
            newrot = RigMatrix3.mul3(oldrot, newrot);
        }

        System.arraycopy(RigZerlegung.zusammen(loc, newrot, size), 0, cob, 0, 16);
    }

    /** {@code sizelike_evaluate}. */
    public static void copyScale(float[] cob, Ziel ziel, MapType c)
    {
        float[] obsize = RigMathe.groesse(cob);
        boolean[] nutz = {flag(c, "use_x", "nutz_x", true), flag(c, "use_y", "nutz_y", true), flag(c, "use_z", "nutz_z", true)};
        boolean uniform = flag(c, "use_make_uniform", "uniform", false);
        float[] size;

        if (uniform)
        {
            float total = 1F;

            if (nutz[0] && nutz[1] && nutz[2])
            {
                total = Math.abs(RigMathe.volumen(ziel.matrix()));
            }
            else
            {
                float[] s = RigMathe.groesse(ziel.matrix());

                for (int i = 0; i < 3; i++)
                {
                    total *= nutz[i] ? s[i] : 1F;
                }
            }

            float cb = (float) Math.cbrt(total);

            size = new float[] {cb, cb, cb};
        }
        else
        {
            size = RigMathe.groesse(ziel.matrix());
        }

        float power = zahl(c, "power", "potenz", 1F);

        for (int i = 0; i < 3; i++)
        {
            size[i] = (float) Math.pow(size[i], power);
        }

        if (flag(c, "use_offset", "versatz", false))
        {
            boolean multiply = !flag(c, "use_add", "addieren", false);

            for (int i = 0; i < 3; i++)
            {
                size[i] = multiply ? size[i] * obsize[i] : size[i] + obsize[i] - 1F;
            }
        }

        for (int i = 0; i < 3; i++)
        {
            if ((nutz[i] || uniform) && obsize[i] != 0F)
            {
                float f = size[i] / obsize[i];

                cob[i * 4] *= f;
                cob[i * 4 + 1] *= f;
                cob[i * 4 + 2] *= f;
            }
        }
    }

    /** {@code translike_evaluate}; {@code mix} auch fuer Action ({@code actcon_evaluate}). */
    public static void copyTransforms(float[] cob, float[] zielMatrix, String mix, boolean scherungRaus)
    {
        float[] target = RigMathe.kopie(zielMatrix);

        if (scherungRaus)
        {
            BOBJBasisMathe.orthogonalisiereUmY(target, false);
        }

        float[] r = switch (mix == null ? "REPLACE" : mix)
        {
            case "BEFORE_FULL" -> RigMathe.mul(target, cob);
            case "AFTER_FULL" -> RigMathe.mul(cob, target);
            case "BEFORE" -> RigZerlegung.mulAusgerichtet(target, cob);
            case "AFTER" -> RigZerlegung.mulAusgerichtet(cob, target);
            case "BEFORE_SPLIT" -> RigZerlegung.mulGetrennt(target, cob);
            case "AFTER_SPLIT" -> RigZerlegung.mulGetrennt(cob, target);
            default -> target;
        };

        System.arraycopy(r, 0, cob, 0, 16);
    }
}
