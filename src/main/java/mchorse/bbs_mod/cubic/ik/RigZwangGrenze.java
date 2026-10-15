package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.bobj.BOBJBasisMathe;
import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.pose.Transform;

import java.util.List;
import static mchorse.bbs_mod.cubic.ik.RigZwangBasis.*;

/**
 * Blenders Limit Location / Rotation / Scale / Distance ({@code constraint.cc}: loclimit_,
 * rotlimit_ mit clamp_angle, sizelimit_, distlimit_evaluate).
 */
public final class RigZwangGrenze
{
    private RigZwangGrenze()
    {}

    /** {@code loclimit_evaluate}. */
    public static void limitLocation(float[] cob, MapType c)
    {
        String[] a = {"x", "y", "z"};

        for (int i = 0; i < 3; i++)
        {
            if (flag(c, "use_min_" + a[i], "grenz_min_" + a[i], false))
            {
                cob[12 + i] = Math.max(cob[12 + i], zahl(c, "min_" + a[i], "min_" + a[i], 0F));
            }

            if (flag(c, "use_max_" + a[i], "grenz_max_" + a[i], false))
            {
                cob[12 + i] = Math.min(cob[12 + i], zahl(c, "max_" + a[i], "max_" + a[i], 0F));
            }
        }
    }

    static float wrapRad(float angle)
    {
        double b = angle * (0.5D / Math.PI) + 0.5D;

        return (float) (((b - Math.floor(b)) - 0.5D) * (2D * Math.PI));
    }

    /** Blenders {@code clamp_angle} — zyklisch, Bereich als gerichteter Bogen. */
    public static float klemmeWinkel(float angle, float min, float max)
    {
        if (max - min >= 2D * Math.PI)
        {
            return angle;
        }

        if (max <= min)
        {
            return min;
        }

        float minW = wrapRad(min - angle);
        float maxW = wrapRad(max - angle);

        if (minW < maxW)
        {
            return angle + Math.max(minW, Math.min(maxW, 0F));
        }

        if (maxW >= 0F || minW <= 0F)
        {
            return angle;
        }

        return angle + (Math.abs(maxW) < Math.abs(minW) ? maxW : minW);
    }

    /** {@code rotlimit_evaluate}. */
    public static void limitRotation(float[] cob, MapType c, Besitzer b)
    {
        BOBJBasisMathe.orthogonalisiereUmY(cob, false);

        String[] a = {"x", "y", "z"};
        boolean[] grenze = new boolean[3];
        boolean irgendeine = false;

        for (int i = 0; i < 3; i++)
        {
            grenze[i] = flag(c, "use_limit_" + a[i], "grenz_" + a[i], false);
            irgendeine |= grenze[i];
        }

        if (!irgendeine)
        {
            return;
        }

        String rotOrder = ordnung(c, "euler_order", b);
        float[] loc = RigMathe.ort(cob);
        float[] size = RigMathe.groesse(cob);
        float[] eul = RigZerlegung.euler(cob, rotOrder);
        boolean legacy = flag(c, "use_legacy_behavior", null, false);

        for (int i = 0; i < 3; i++)
        {
            if (!grenze[i])
            {
                continue;
            }

            float min = zahl(c, "min_" + a[i], "min_" + a[i], 0F);
            float max = zahl(c, "max_" + a[i], "max_" + a[i], 0F);

            eul[i] = legacy ? Math.max(min, Math.min(max, eul[i])) : klemmeWinkel(eul[i], min, max);
        }

        System.arraycopy(RigZerlegung.zusammenEuler(loc, eul, size, rotOrder), 0, cob, 0, 16);
    }

    /** {@code sizelimit_evaluate}. */
    public static void limitScale(float[] cob, MapType c)
    {
        float[] size = RigMathe.groesse(cob);
        float[] obsize = size.clone();
        String[] a = {"x", "y", "z"};

        for (int i = 0; i < 3; i++)
        {
            if (flag(c, "use_min_" + a[i], null, false))
            {
                size[i] = Math.max(size[i], zahl(c, "min_" + a[i], null, 0F));
            }

            if (flag(c, "use_max_" + a[i], null, false))
            {
                size[i] = Math.min(size[i], zahl(c, "max_" + a[i], null, 0F));
            }
        }

        for (int i = 0; i < 3; i++)
        {
            if (obsize[i] != 0F)
            {
                float f = size[i] / obsize[i];

                cob[i * 4] *= f;
                cob[i * 4 + 1] *= f;
                cob[i * 4 + 2] *= f;
            }
        }
    }

    /** {@code distlimit_evaluate}. {@code distanz} ist Blenders data->dist (beim ersten Lauf gemessen). */
    public static void limitDistance(float[] cob, Ziel ziel, MapType c)
    {
        float[] ort = RigMathe.ort(cob);
        float[] zielOrt = RigMathe.ort(ziel.matrix());
        float dist = RigVektor.laenge(RigVektor.differenz(ort, zielOrt));
        float soll = zahl(c, "distance", "distanz", 0F);

        /* data->dist == 0 heisst "beim ersten Lauf messen" — Blender schreibt den Wert zurueck. */
        if (soll == 0F)
        {
            soll = dist;
            merke(c, "distance", "distanz", dist);
        }

        String mode = text(c, "limit_mode", "modus", "LIMITDIST_INSIDE");
        boolean klemmen;
        float sfac = 1F;

        switch (mode)
        {
            case "LIMITDIST_OUTSIDE" -> klemmen = dist <= soll;
            case "LIMITDIST_ONSURFACE" -> klemmen = Math.abs(dist - soll) >= 1.1920929E-7F;
            default -> klemmen = dist >= soll;
        }

        if (!klemmen)
        {
            return;
        }

        if (dist != 0F)
        {
            sfac = soll / dist;
        }

        RigMathe.setzeOrt(cob, RigVektor.mischeV(zielOrt, ort, sfac));
    }
}
