package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.bobj.BOBJArmature;
import mchorse.bbs_mod.bobj.BOBJBBone;
import mchorse.bbs_mod.bobj.BOBJBone;
import static mchorse.bbs_mod.cubic.ik.RigBBoneParam.*;

/**
 * Blenders {@code BKE_pchan_bbone_handles_compute}: Griffvektoren h1/h2 mit Ease-Laenge
 * und die Rolls an beiden Enden.
 */
public final class RigBBoneGriffweite
{
    private RigBBoneGriffweite()
    {}

    /** {@code BKE_pchan_bbone_handles_compute}: h1/h2 und die Rolls. */
    static float[] griffeBerechnen(Param p, float[] h1, float[] h2)
    {
        float length = p.length;
        float epsilon = 1.0E-5F * length;
        float roll1 = 0F;
        float roll2 = 0F;

        if (p.doScale)
        {
            length *= p.scale[1];
        }

        if (p.usePrev)
        {
            System.arraycopy(p.prevH, 0, h1, 0, 3);

            if (p.prevBbone)
            {
                h1[1] -= length;
            }

            if (RigVektor.normalisiereV(h1) < epsilon)
            {
                h1[0] = 0F;
                h1[1] = -1F;
                h1[2] = 0F;
            }

            h1[0] = -h1[0];
            h1[1] = -h1[1];
            h1[2] = -h1[2];

            if (!p.prevBbone)
            {
                roll1 = RigAchse.rollAusMatrix(RigMathe.drei(p.prevMat), h1);
            }
        }
        else
        {
            h1[0] = 0F;
            h1[1] = 1F;
            h1[2] = 0F;
        }

        if (p.useNext)
        {
            System.arraycopy(p.nextH, 0, h2, 0, 3);

            if (!p.nextBbone)
            {
                h2[1] -= length;
            }

            if (RigVektor.normalisiereV(h2) < epsilon)
            {
                h2[0] = 0F;
                h2[1] = 1F;
                h2[2] = 0F;
            }

            roll2 = RigAchse.rollAusMatrix(RigMathe.drei(p.nextMat), h2);
        }
        else
        {
            h2[0] = 0F;
            h2[1] = 1F;
            h2[2] = 0F;
        }

        float circle = length * (RigVektor.kreisTangentenFaktor(h1, h2) / 0.75F);
        float hlength1 = p.ease1 * circle;
        float hlength2 = p.ease2 * circle;

        for (int i = 0; i < 3; i++)
        {
            h1[i] *= hlength1;
            h2[i] *= -hlength2;
        }

        roll1 += p.roll1;
        roll2 += p.roll2;

        float xs = p.doScale ? p.scale[0] : 1F;
        float zs = p.doScale ? p.scale[2] : 1F;

        h1[0] += p.curveInX * xs;
        h1[2] += p.curveInZ * zs;
        h2[0] += p.curveOutX * xs;
        h2[2] += p.curveOutZ * zs;

        return new float[] {roll1, roll2};
    }
}
