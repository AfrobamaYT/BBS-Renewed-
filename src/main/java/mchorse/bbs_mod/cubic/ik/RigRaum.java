package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.bobj.BOBJArmature;
import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.bobj.BOBJElternTransform;
import mchorse.bbs_mod.data.types.MapType;

/**
 * Blenders Raum-Umrechnung fuer Knochen-Constraints — {@code BKE_constraint_mat_convertspace}
 * (constraint.cc) mit {@code BKE_armature_mat_pose_to_bone} / {@code _bone_to_pose}
 * (armature.cc), wortgetreu. Alle Matrizen hier sind Blender-Matrizen (Layout wie
 * {@link RigMathe}), der Rahmenwechsel zu BBS geschieht in {@link RigRahmen}.
 *
 * <p>Raeume: WORLD, POSE, LOCAL, LOCAL_WITH_PARENT, LOCAL_OWNER_ORIENT (nur Ziele), CUSTOM.
 */
public final class RigRaum
{
    public static final String WELT = "WORLD";

    public static final String POSE = "POSE";

    public static final String LOKAL = "LOCAL";

    public static final String ELTERNLOKAL = "LOCAL_WITH_PARENT";

    public static final String BESITZERLOKAL = "LOCAL_OWNER_ORIENT";

    public static final String EIGEN = "CUSTOM";

    private RigRaum()
    {}

    /** {@code BKE_bone_parent_transform_calc_from_pchan} im Blender-Pose-Raum. */
    public static BOBJElternTransform.Bpt bpt(RigRahmen rahmen, BOBJBone b)
    {
        float[] offs = b.relBoneMat.get(new float[16]);

        if (b.parentBone == null)
        {
            return BOBJElternTransform.berechne(rahmen.armMat(b), null, null,
                !b.keineElternDrehung, b.skalenErbe, !b.elternraumVerschiebung);
        }

        return BOBJElternTransform.berechne(offs, rahmen.poseMat(b.parentBone), rahmen.armMat(b.parentBone),
            !b.keineElternDrehung, b.skalenErbe, !b.elternraumVerschiebung);
    }

    /** {@code BKE_armature_mat_pose_to_bone}. */
    public static float[] poseZuKnochen(RigRahmen rahmen, BOBJBone b, float[] inmat)
    {
        return bpt(rahmen, b).invertiert().anwenden(inmat);
    }

    /** {@code BKE_armature_mat_bone_to_pose}. */
    public static float[] knochenZuPose(RigRahmen rahmen, BOBJBone b, float[] inmat)
    {
        return bpt(rahmen, b).anwenden(inmat);
    }

    /**
     * {@code BKE_constraint_mat_convertspace} fuer einen Knochen.
     *
     * @param pchan      der Knochen, dessen Raum gemeint ist (Ziel oder Besitzer)
     * @param besitzer   der Constraint-Besitzer (fuer LOCAL_OWNER_ORIENT), darf pchan sein
     * @param eigenRaum  Weltmatrix des CUSTOM-Raums (oder null = Einheit)
     */
    public static float[] wandle(RigRahmen rahmen, BOBJBone pchan, BOBJBone besitzer, float[] eigenRaum,
                                 float[] mat, String von, String nach, boolean skalaBehalten)
    {
        if (von.equals(nach))
        {
            return mat;
        }

        if (pchan == null)
        {
            return wandleObjekt(rahmen, eigenRaum, mat, von, nach, skalaBehalten);
        }

        switch (von)
        {
            case WELT ->
            {
                if (EIGEN.equals(nach))
                {
                    return RigMathe.mul(RigMathe.invertiert(eigen(eigenRaum)), mat);
                }

                float[] pose = rahmen.weltZuPose(mat);

                if (LOKAL.equals(nach) || ELTERNLOKAL.equals(nach) || BESITZERLOKAL.equals(nach))
                {
                    return wandle(rahmen, pchan, besitzer, eigenRaum, pose, POSE, nach, skalaBehalten);
                }

                return pose;
            }
            case POSE ->
            {
                if (LOKAL.equals(nach))
                {
                    return poseZuKnochen(rahmen, pchan, mat);
                }

                if (BESITZERLOKAL.equals(nach))
                {
                    return wandle(rahmen, pchan, besitzer, eigenRaum, poseZuKnochen(rahmen, pchan, mat),
                        LOKAL, nach, skalaBehalten);
                }

                if (ELTERNLOKAL.equals(nach))
                {
                    return RigMathe.mul(RigMathe.invertiert(rahmen.armMat(pchan)), mat);
                }

                float[] welt = rahmen.poseZuWelt(mat);

                return WELT.equals(nach) ? welt : wandle(rahmen, pchan, besitzer, eigenRaum, welt, WELT, nach, skalaBehalten);
            }
            case LOKAL ->
            {
                if (BESITZERLOKAL.equals(nach))
                {
                    float[] diff = besitzerDifferenz(rahmen, pchan, besitzer);

                    return RigMathe.mul(RigMathe.mul(diff, mat), RigMathe.invertiert(diff));
                }

                float[] pose = knochenZuPose(rahmen, pchan, mat);

                if (WELT.equals(nach) || ELTERNLOKAL.equals(nach) || EIGEN.equals(nach))
                {
                    return wandle(rahmen, pchan, besitzer, eigenRaum, pose, POSE, nach, skalaBehalten);
                }

                return pose;
            }
            case BESITZERLOKAL ->
            {
                float[] diff = besitzerDifferenz(rahmen, pchan, besitzer);
                float[] lokal = RigMathe.mul(RigMathe.mul(RigMathe.invertiert(diff), mat), diff);

                return LOKAL.equals(nach) ? lokal : wandle(rahmen, pchan, besitzer, eigenRaum, lokal, LOKAL, nach, skalaBehalten);
            }
            case ELTERNLOKAL ->
            {
                float[] pose = RigMathe.mul(rahmen.armMat(pchan), mat);

                if (WELT.equals(nach) || LOKAL.equals(nach) || BESITZERLOKAL.equals(nach) || EIGEN.equals(nach))
                {
                    return wandle(rahmen, pchan, besitzer, eigenRaum, pose, POSE, nach, skalaBehalten);
                }

                return pose;
            }
            case EIGEN ->
            {
                float[] welt = RigMathe.mul(eigen(eigenRaum), mat);

                return WELT.equals(nach) ? welt : wandle(rahmen, pchan, besitzer, eigenRaum, welt, WELT, nach, skalaBehalten);
            }
            default ->
            {
                return mat;
            }
        }
    }

    /**
     * Der Objekt-Zweig von {@code BKE_constraint_mat_convertspace} fuer das Armature-Objekt
     * ohne Elter: LOCAL ist die eigene Weltlage ohne Ort (wahlweise normalisiert) — Blenders
     * eigener Kommentar nennt das einen Hack, es ist aber, was gerechnet wird. POSE und die
     * Eltern-Raeume gibt es fuer Objekte nicht; sie fallen auf WORLD.
     */
    static float[] wandleObjekt(RigRahmen rahmen, float[] eigenRaum, float[] mat, String von, String nach,
                                        boolean skalaBehalten)
    {
        float[] diff = RigMathe.kopie(rahmen.welt);

        if (!skalaBehalten)
        {
            RigMathe.normalisiere(diff);
        }

        RigMathe.setzeOrt(diff, new float[3]);

        float[] welt = switch (von)
        {
            case LOKAL, ELTERNLOKAL, BESITZERLOKAL -> RigMathe.mul(diff, mat);
            case EIGEN -> RigMathe.mul(eigen(eigenRaum), mat);
            default -> mat;
        };

        return switch (nach)
        {
            case LOKAL, ELTERNLOKAL, BESITZERLOKAL -> RigMathe.mul(RigMathe.invertiert(diff), welt);
            case EIGEN -> RigMathe.mul(RigMathe.invertiert(eigen(eigenRaum)), welt);
            default -> welt;
        };
    }

    static float[] eigen(float[] eigenRaum)
    {
        return eigenRaum == null ? RigMathe.einheit() : eigenRaum;
    }

    /** {@code diff_mat} aus LOCAL <-> OWNER LOCAL: inv(owner.arm_mat) x target.arm_mat, Ort 0. */
    static float[] besitzerDifferenz(RigRahmen rahmen, BOBJBone pchan, BOBJBone besitzer)
    {
        float[] diff = rahmen.armMat(pchan);

        if (besitzer != null)
        {
            diff = RigMathe.mul(RigMathe.invertiert(rahmen.armMat(besitzer)), diff);
        }

        RigMathe.setzeOrt(diff, new float[3]);

        return diff;
    }
}

