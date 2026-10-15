package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.bobj.BOBJBasisMathe;
import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.pose.Transform;

import java.util.List;
import static mchorse.bbs_mod.cubic.ik.RigZwangBasis.*;

/**
 * Blenders Child Of, Maintain Volume, Floor und Pivot ({@code constraint.cc}: childof_,
 * samevolume_, minmax_, pivotcon_evaluate).
 */
public final class RigZwangLage
{
    private RigZwangLage()
    {}

    /** {@code childof_evaluate}. */
    public static void childOf(float[] cob, Ziel ziel, MapType c, Besitzer b)
    {
        String[] a = {"x", "y", "z"};
        boolean[] loc = new boolean[3];
        boolean[] rot = new boolean[3];
        boolean[] sca = new boolean[3];
        boolean alle = true;

        for (int i = 0; i < 3; i++)
        {
            loc[i] = flag(c, "use_location_" + a[i], null, true);
            rot[i] = flag(c, "use_rotation_" + a[i], null, true);
            sca[i] = flag(c, "use_scale_" + a[i], null, true);
            alle &= loc[i] && rot[i] && sca[i];
        }

        float[] parmat;
        float[] inverse;
        float[] invmat = matrix(c, "inverse_matrix");

        if (alle)
        {
            parmat = RigMathe.kopie(ziel.matrix());
            inverse = invmat;
        }
        else
        {
            float[] l = RigMathe.ort(ziel.matrix());
            float[] e = RigZerlegung.euler(ziel.matrix(), ziel.rotOrder());
            float[] s = RigMathe.groesse(ziel.matrix());
            float[] lo = RigMathe.ort(invmat);
            float[] eo = RigZerlegung.euler(invmat, b.rotOrder());
            float[] so = RigMathe.groesse(invmat);

            for (int i = 0; i < 3; i++)
            {
                if (!loc[i])
                {
                    l[i] = lo[i] = 0F;
                }

                if (!rot[i])
                {
                    e[i] = eo[i] = 0F;
                }

                if (!sca[i])
                {
                    s[i] = so[i] = 1F;
                }
            }

            parmat = RigZerlegung.zusammenEuler(l, e, s, ziel.rotOrder());
            inverse = RigZerlegung.zusammenEuler(lo, eo, so, b.rotOrder());
        }

        float[] orig = RigMathe.kopie(cob);
        float[] r = RigMathe.mul(RigMathe.mul(parmat, inverse), orig);

        for (int i = 0; i < 3; i++)
        {
            if (!loc[i])
            {
                r[12 + i] = orig[12 + i];
            }
        }

        System.arraycopy(r, 0, cob, 0, 16);
    }

    /** {@code samevolume_evaluate}. */
    public static void maintainVolume(float[] cob, MapType c)
    {
        float volume = zahl(c, "volume", null, 1F);
        float[] obsize = RigMathe.groesse(cob);
        int frei = spurIndex(text(c, "free_axis", null, "SAMEVOL_Y").replace("SAMEVOL_", ""));
        float total = switch (text(c, "mode", null, "STRICT"))
        {
            case "UNIFORM" -> obsize[frei] * obsize[frei] * obsize[frei];
            case "SINGLE_AXIS" -> obsize[frei];
            default -> obsize[0] * obsize[1] * obsize[2];
        };
        float fac = total != 0F ? (float) Math.sqrt(volume / total) : 1F;

        for (int i = 0; i < 3; i++)
        {
            if (i != frei)
            {
                cob[i * 4] *= fac;
                cob[i * 4 + 1] *= fac;
                cob[i * 4 + 2] *= fac;
            }
        }
    }

    /** {@code minmax_evaluate} (Floor). */
    public static void floor(float[] cob, Ziel ziel, MapType c)
    {
        float[] obmat = RigMathe.kopie(cob);
        float[] tarmat = RigMathe.kopie(ziel.matrix());
        boolean rotation = flag(c, "use_rotation", null, false);

        if (rotation)
        {
            obmat = RigMathe.mul(RigMathe.invertiert(tarmat), obmat);
            tarmat = RigMathe.einheit();
        }

        int flag = spurIndex(text(c, "floor_location", null, "FLOOR_Z"));
        int index = flag % 3;
        float offset = zahl(c, "offset", null, 0F);
        float val1;
        float val2;

        if (flag < 3)
        {
            val1 = tarmat[12 + index];
            val2 = obmat[12 + index] - offset;
        }
        else
        {
            val2 = tarmat[12 + index];
            val1 = obmat[12 + index] - offset;
        }

        if (val1 > val2)
        {
            obmat[12 + index] = tarmat[12 + index] + offset;

            if (rotation)
            {
                System.arraycopy(RigMathe.mul(ziel.matrix(), obmat), 0, cob, 0, 16);
            }
            else
            {
                RigMathe.setzeOrt(cob, RigMathe.ort(obmat));
            }
        }
    }

    /** {@code pivotcon_evaluate}. */
    public static void pivot(float[] cob, Ziel ziel, MapType c, Besitzer b)
    {
        String range = text(c, "rotation_range", null, "ALWAYS_ACTIVE");

        if (!"ALWAYS_ACTIVE".equals(range))
        {
            float[] rot = RigZerlegung.euler(cob, b.rotOrder());
            boolean negativ = range.startsWith("N");
            int achse = range.charAt(range.length() - 1) - 'X';

            if (negativ ? rot[achse] > 0F : rot[achse] < 0F)
            {
                return;
            }
        }

        float[] offset = vektor(c, "offset", new float[3]);
        float[] pivot;

        if (ziel != null)
        {
            pivot = RigVektor.summe(RigMathe.ort(ziel.matrix()), offset);
        }
        else if (flag(c, "use_relative_location", null, true))
        {
            pivot = RigVektor.summe(RigMathe.ort(cob), offset);
        }
        else
        {
            pivot = offset.clone();
        }

        float[] rotMat = RigMatrix3.normalisiere3(RigMathe.drei(cob));
        float[] aw = RigAchse.achseWinkel(RigQuaternion.quaternion3(rotMat));

        if (aw[3] != 0F)
        {
            float[] achse = {aw[0], aw[1], aw[2]};
            float[] vec = RigVektor.differenz(pivot, RigMathe.ort(cob));

            pivot = RigVektor.differenz(pivot, RigVektor.projiziere(vec, achse));
        }

        float[] vec = RigMatrix3.mul3v(rotMat, RigVektor.differenz(RigMathe.ort(cob), pivot));

        RigMathe.setzeOrt(cob, RigVektor.summe(pivot, vec));
    }
}
