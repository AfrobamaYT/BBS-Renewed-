package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.bobj.BOBJArmature;
import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.bobj.BOBJElternTransform;
import mchorse.bbs_mod.data.types.MapType;
import static mchorse.bbs_mod.cubic.ik.RigRaum.*;

/**
 * Blenders Zielmatrix eines Constraints ({@code constraint_target_to_mat4}: Kopf/Schwanz, B-Bone-
 * Form, Zielraum) und der CUSTOM-Raum ({@code BKE_constraint_custom_object_space_init}).
 */
public final class RigZiel
{
    private RigZiel()
    {}

    /**
     * {@code BKE_constraint_custom_object_space_init}: die Weltmatrix des CUSTOM-Raums —
     * ein Knochen ({@code space_subtarget}) oder das Armature-Objekt; null = nicht gebraucht.
     */
    public static float[] eigenRaum(RigRahmen rahmen, BOBJArmature armature, MapType c)
    {
        if (!EIGEN.equals(c.getString("raum_o", "")) && !EIGEN.equals(c.getString("raum_z", "")))
        {
            return null;
        }

        String rb = c.getString("raum_bone", "");

        if (rb != null && !rb.isEmpty())
        {
            BOBJBone b = armature.bones.get(rb);

            if (b != null)
            {
                return rahmen.weltMat(b);
            }
        }

        MapType rna = c.has("rna") ? c.getMap("rna") : null;
        String objekt = rna == null ? null : rna.getString("space_object");

        return objekt == null || objekt.isEmpty() ? RigMathe.einheit() : RigMathe.kopie(rahmen.welt);
    }

    /**
     * {@code constraint_target_to_mat4} fuer ein Knochenziel: Weltmatrix samt {@code head_tail}
     * (mit B-Bone-Form, wenn verlangt und vorhanden), dann in den Zielraum gewandelt.
     *
     * @param ziel      null = das Armature-Objekt selbst (Objektmatrix)
     * @param bboneForm {@code use_bbone_shape}
     * @param bboneVoll {@code CONSTRAINT_BBONE_SHAPE_FULL} (Copy Transforms)
     */
    public static float[] zielMatrix(RigRahmen rahmen, BOBJBone ziel, BOBJBone besitzer, float[] eigenRaum,
                                     String raum, float headTail, boolean bboneForm, boolean bboneVoll)
    {
        float[] welt;

        if (ziel == null)
        {
            welt = RigMathe.kopie(rahmen.welt);

            return wandle(rahmen, null, besitzer, eigenRaum, welt, WELT, raum, false);
        }

        float[] pose = rahmen.poseMat(ziel);
        boolean istBbone = ziel.bbone != null && ziel.bbone.aktiv() && bboneForm;

        if (headTail < 0.000001F && !(istBbone && bboneVoll))
        {
            welt = rahmen.poseZuWelt(pose);
        }
        else if (istBbone)
        {
            int[] index = new int[1];
            float[] fac = new float[1];

            RigBBone.klemmeSegmentIndex(ziel.bbone.segmente, headTail, index, fac);

            float[][] bb = ziel.bbone.poseMatrizen;
            float[] tempmat;

            if (bboneVoll)
            {
                tempmat = RigMathe.mul(pose, RigZerlegung.interpoliere(bb[index[0]], bb[index[0] + 1], fac[0]));
            }
            else
            {
                float[] loc = RigVektor.mischeV(RigMathe.ort(bb[index[0]]), RigMathe.ort(bb[index[0] + 1]), fac[0]);

                tempmat = RigMathe.kopie(pose);
                RigMathe.setzeOrt(tempmat, RigMathe.punkt(pose, loc));
            }

            welt = rahmen.poseZuWelt(tempmat);
        }
        else
        {
            float[] kopf = RigMathe.ort(pose);
            float[] schwanz = RigVektor.summe(kopf, RigVektor.mal(RigMathe.spalte(pose, 1), ziel.restLength));
            float[] tempmat = RigMathe.kopie(pose);

            RigMathe.setzeOrt(tempmat, RigVektor.mischeV(kopf, schwanz, headTail));
            welt = rahmen.poseZuWelt(tempmat);
        }

        return wandle(rahmen, ziel, besitzer, eigenRaum, welt, WELT, raum, false);
    }
}
