package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.bobj.BOBJArmature;
import mchorse.bbs_mod.bobj.BOBJBBone;
import mchorse.bbs_mod.bobj.BOBJBone;

/**
 * Blenders {@code BBoneSplineParameters} und der Eigenschaftsteil von
 * {@code BKE_pchan_bbone_spline_params_get} (Ease, Roll, Kurve, Skala beider Ebenen).
 */
public final class RigBBoneParam
{
    private RigBBoneParam()
    {}

    /** Blenders {@code BBoneSplineParameters}. */
    static final class Param
    {
        int segments;
        float length;
        boolean doScale;
        float[] scale = {1F, 1F, 1F};
        boolean usePrev, prevBbone, useNext, nextBbone;
        float[] prevH = new float[3];
        float[] nextH = new float[3];
        float[] prevMat = RigMathe.einheit();
        float[] nextMat = RigMathe.einheit();
        float ease1, ease2, roll1, roll2;
        float[] scaleIn = {1F, 1F, 1F};
        float[] scaleOut = {1F, 1F, 1F};
        float curveInX, curveInZ, curveOutX, curveOutZ;
    }

    /** Der "bbone properties over the top"-Teil von {@code BKE_pchan_bbone_spline_params_get}:
     * Ease, Roll, Kurve, Skala aus Knochen- und Pose-Ebene, dazu Griff-Skalen. */
    static void eigenschaften(Param p, BOBJBBone d, boolean rest, BOBJBone prev, float[] prevScale, float[] nextScale)
    {
        p.ease1 = d.ruheEase[0] + (!rest ? d.poseEase[0] : 0F);
        p.ease2 = d.ruheEase[1] + (!rest ? d.poseEase[1] : 0F);
        p.roll1 = d.ruheRoll[0] + (!rest ? d.poseRoll[0] : 0F);
        p.roll2 = d.ruheRoll[1] + (!rest ? d.poseRoll[1] : 0F);

        if (d.endrollAlsInroll && prev != null && prev.bbone != null)
        {
            p.roll1 += prev.bbone.ruheRoll[1];

            if (!rest)
            {
                p.roll1 += prev.bbone.poseRoll[1];
            }
        }

        p.scaleIn = d.ruheSkalaIn.clone();
        p.scaleOut = d.ruheSkalaOut.clone();

        if (!rest)
        {
            for (int i = 0; i < 3; i++)
            {
                p.scaleIn[i] *= d.poseSkalaIn[i];
                p.scaleOut[i] *= d.poseSkalaOut[i];
            }
        }

        p.curveInX = d.ruheKurveIn[0] + (!rest ? d.poseKurveIn[0] : 0F);
        p.curveInZ = d.ruheKurveIn[1] + (!rest ? d.poseKurveIn[1] : 0F);
        p.curveOutX = d.ruheKurveOut[0] + (!rest ? d.poseKurveOut[0] : 0F);
        p.curveOutZ = d.ruheKurveOut[1] + (!rest ? d.poseKurveOut[1] : 0F);

        if (d.skaliertEasing)
        {
            p.ease1 *= p.scaleIn[1];
            p.curveInX *= p.scaleIn[1];
            p.curveInZ *= p.scaleIn[1];
            p.ease2 *= p.scaleOut[1];
            p.curveOutX *= p.scaleOut[1];
            p.curveOutZ *= p.scaleOut[1];
        }

        for (int i = 0; i < 3; i++)
        {
            if (d.griffSkala[0][i])
            {
                p.scaleIn[i] *= prevScale[i];
            }

            if (d.griffSkala[1][i])
            {
                p.scaleOut[i] *= nextScale[i];
            }
        }

        if (d.griffEase[0])
        {
            p.ease1 *= prevScale[1];
            p.curveInX *= prevScale[1];
            p.curveInZ *= prevScale[1];
        }

        if (d.griffEase[1])
        {
            p.ease2 *= nextScale[1];
            p.curveOutX *= nextScale[1];
            p.curveOutZ *= nextScale[1];
        }
    }
}
