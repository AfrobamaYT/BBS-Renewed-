package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.bobj.BOBJBasisMathe;
import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.pose.Transform;

import java.util.List;

/**
 * Blenders Armature-Constraint ({@code constraint.cc}: armdef_evaluate): gewichtete
 * Verform-Matrizen der Ziele, linear oder als Dual-Quaternion, mit Huellen und B-Bone-Segmenten.
 */
public final class RigZwangArmature
{
    private RigZwangArmature()
    {}

    /**
     * {@code armdef_evaluate}: gewichtete Summe der VERFORM-Matrizen der Ziele (chan_mat =
     * pose x inv(arm), bei B-Bones je Segment), linear oder als Dual-Quaternion.
     *
     * @param ziele   Zielknochen in Blender-Pose-Raum samt Gewicht (schon getrieben)
     * @param eigenKopf Ruhekopf des Besitzers (Welt) bzw. sein Ort fuer Huellen/B-Bone-Wahl
     */
    public static void armature(float[] cob, List<BOBJBone> ziele, float[] gewichte, RigRahmen rahmen,
                                boolean quaternion, boolean huellen, float[] eigenKopf)
    {
        float[] sum = new float[16];
        RigDualQuat dq = quaternion ? new RigDualQuat() : null;
        float weight = 0F;
        float[] iobmat = RigMathe.invertiert(rahmen.welt);
        float[] co = RigMathe.punkt(iobmat, eigenKopf);

        for (int i = 0; i < ziele.size(); i++)
        {
            float w = gewichte[i];
            BOBJBone pchan = ziele.get(i);

            if (w <= 0F)
            {
                continue;
            }

            if (pchan == null)
            {
                return;
            }

            float[] armMat = rahmen.armMat(pchan);

            if (huellen || pchan.huelleMult)
            {
                float[] huelle = pchan.huelle;
                float[] kopf = RigMathe.ort(armMat);
                float[] schwanz = RigVektor.summe(kopf, RigVektor.mal(RigMathe.spalte(armMat, 1), pchan.restLength));

                w *= RigVektor.huellenFaktor(co, kopf, schwanz, huelle == null ? 0F : huelle[0],
                    huelle == null ? 0F : huelle[1], huelle == null ? 0F : huelle[2]);
            }

            if (w <= 0F)
            {
                continue;
            }

            if (pchan.bbone != null && pchan.bbone.aktiv())
            {
                int[] index = new int[1];
                float[] blend = new float[1];

                RigBBone.segmentIndex(pchan, co, index, blend);

                float[][] mats = pchan.bbone.verformMatrizen;
                float[][] rest = pchan.bbone.ruheMatrizen;

                armatureSummand(sum, dq, rahmen, iobmat, RigMathe.mul(armMat, rest[index[0]]), mats[index[0] + 1],
                    eigenKopf, w * (1F - blend[0]));
                armatureSummand(sum, dq, rahmen, iobmat, RigMathe.mul(armMat, rest[index[0] + 1]), mats[index[0] + 2],
                    eigenKopf, w * blend[0]);
            }
            else
            {
                float[] chanMat = RigMathe.mul(rahmen.poseMat(pchan), RigMathe.invertiert(armMat));

                armatureSummand(sum, dq, rahmen, iobmat, armMat, chanMat, eigenKopf, w);
            }

            weight += w;
        }

        if (weight <= 0F)
        {
            return;
        }

        float[] gesamt;

        if (dq != null)
        {
            dq.normalisiere(weight);
            gesamt = dq.zuMatrix();
        }
        else
        {
            gesamt = new float[16];

            for (int k = 0; k < 16; k++)
            {
                gesamt[k] = sum[k] / weight;
            }
        }

        System.arraycopy(RigMathe.mul(gesamt, cob), 0, cob, 0, 16);
    }

    /** {@code armdef_accumulate_matrix}. */
    static void armatureSummand(float[] sum, RigDualQuat dq, RigRahmen rahmen, float[] iobmat,
                                        float[] basemat, float[] bonemat, float[] pivot, float weight)
    {
        if (weight == 0F)
        {
            return;
        }

        float[] mat = RigMathe.mul(RigMathe.mul(rahmen.welt, bonemat), iobmat);

        if (dq != null)
        {
            float[] basematWelt = RigMathe.mul(rahmen.welt, basemat);

            BOBJBasisMathe.orthogonalisiereUmY(basematWelt, true);
            dq.addiereMitPivot(RigDualQuat.aus(basematWelt, mat), pivot, weight, true);
        }
        else
        {
            for (int k = 0; k < 16; k++)
            {
                sum[k] += mat[k] * weight;
            }
        }
    }
}
