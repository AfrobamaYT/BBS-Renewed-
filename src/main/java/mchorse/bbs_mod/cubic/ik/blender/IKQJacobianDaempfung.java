/* Uebersetzt aus Blenders intern/iksolver/intern/IK_QJacobian.cpp
 * (InvertSDLS / InvertDLS).
 * SPDX-FileCopyrightText: 2001-2002 NaN Holding BV. All rights reserved.
 * SPDX-License-ResourceLocation: GPL-2.0-or-later */
package mchorse.bbs_mod.cubic.ik.blender;

/**
 * Die gedaempfte Pseudoinverse. Blenders Legacy-Solver faehrt per Vorgabe
 * <b>SDLS</b> ({@code m_sdls(true)}) — selektiv gedaempft je Singulaerwert statt
 * mit einem globalen Lambda.
 *
 * ⛔ Die 0.80 in SDLS und die Reihenfolge der Daempfungen sehen wie Stellschrauben
 * aus und sind es nicht: Blender nennt sie selbst einen Hack gegen Oszillation an
 * Gelenkgrenzen. Wer sie "aufraeumt", bekommt zappelnde Ketten.
 */
final class IKQJacobianDaempfung
{
    private IKQJacobianDaempfung()
    {}

    static void sdls(IKQJacobian j)
    {
        double maxWinkelAenderung = Math.PI / 4D;
        double epsilon = 1e-10;

        java.util.Arrays.fill(j.dTheta, 0D);
        j.minDamp = 1D;

        for (int i = 0; i < j.dof; i++)
        {
            j.norm[i] = 0D;

            for (int k = 0; k < j.taskSize; k += 3)
            {
                double n = 0D;

                n += j.jacobian.get(k, i) * j.jacobian.get(k, i);
                n += j.jacobian.get(k + 1, i) * j.jacobian.get(k + 1, i);
                n += j.jacobian.get(k + 2, i) * j.jacobian.get(k + 2, i);

                j.norm[i] += Math.sqrt(n);
            }
        }

        for (int i = 0; i < j.svdW.length; i++)
        {
            if (j.svdW[i] <= epsilon)
            {
                continue;
            }

            double wInv = 1D / j.svdW[i];
            double alpha = 0D;
            double n = 0D;

            for (int k = 0; k < j.svdU.zeilen; k += 3)
            {
                alpha += j.svdU.get(k, i) * j.beta[k];
                alpha += j.svdU.get(k + 1, i) * j.beta[k + 1];
                alpha += j.svdU.get(k + 2, i) * j.beta[k + 2];

                double tmp = j.svdU.get(k, i) * j.svdU.get(k, i);

                tmp += j.svdU.get(k + 1, i) * j.svdU.get(k + 1, i);
                tmp += j.svdU.get(k + 2, i) * j.svdU.get(k + 2, i);

                n += Math.sqrt(tmp);
            }

            alpha *= wInv;

            double m = 0D;
            double maxDTheta = 0D;

            for (int k = 0; k < j.dTheta.length; k++)
            {
                double v = j.svdV.get(k, i);

                m += Math.abs(v) * j.norm[k];
                j.dThetaTmp[k] = v * alpha;

                maxDTheta = Math.max(Math.abs(j.dThetaTmp[k]) * j.weightSqrt[k], maxDTheta);
            }

            m *= wInv;

            double gamma = maxWinkelAenderung;

            if (n < m)
            {
                gamma *= n / m;
            }

            double damp = gamma < maxDTheta ? gamma / maxDTheta : 1D;

            for (int k = 0; k < j.dTheta.length; k++)
            {
                double dofDamp = Math.min(damp / j.weight[k], 1D);

                /* 0.80: Blenders Hack, damit das System bei Oszillation an
                 * Gelenkgrenzen noch konvergiert. Lieber etwas zu langsam. */
                j.dTheta[k] += 0.80D * dofDamp * j.dThetaTmp[k];
            }

            j.minDamp = Math.min(damp, j.minDamp);
        }

        double maxWinkel = 0D;

        for (int k = 0; k < j.dof; k++)
        {
            j.dTheta[k] *= j.weight[k];
            maxWinkel = Math.max(Math.abs(j.dTheta[k]), maxWinkel);
        }

        if (maxWinkel > maxWinkelAenderung)
        {
            double damp = maxWinkelAenderung / (maxWinkelAenderung + maxWinkel);

            for (int k = 0; k < j.dof; k++)
            {
                j.dTheta[k] *= damp;
            }
        }
    }

    /** Globales Lambda statt selektiver Daempfung — im Legacy-Pfad ungenutzt,
     *  aber uebersetzt, damit der Port vollstaendig ist. */
    static void dls(IKQJacobian j)
    {
        double epsilon = 1e-10;
        double maxWinkelAenderung = 0.1D;
        double xLength = 0D;

        for (double b : j.beta)
        {
            xLength += b * b;
        }

        xLength = Math.sqrt(xLength);

        double wMin = Double.MAX_VALUE;

        for (double w : j.svdW)
        {
            if (w > epsilon && w < wMin)
            {
                wMin = w;
            }
        }

        double d = xLength / maxWinkelAenderung;
        double lambda;

        if (wMin <= d / 2D) lambda = d / 2D;
        else if (wMin < d) lambda = Math.sqrt(wMin * (d - wMin));
        else lambda = 0D;

        lambda = Math.min(lambda * lambda, 10D);

        /* U^T * beta */
        for (int i = 0; i < j.svdUBeta.length; i++)
        {
            double s = 0D;

            for (int k = 0; k < j.svdU.zeilen; k++)
            {
                s += j.svdU.get(k, i) * j.beta[k];
            }

            j.svdUBeta[i] = s;
        }

        java.util.Arrays.fill(j.dTheta, 0D);

        for (int i = 0; i < j.svdW.length; i++)
        {
            if (j.svdW[i] <= epsilon)
            {
                continue;
            }

            double wInv = j.svdW[i] / (j.svdW[i] * j.svdW[i] + lambda);

            j.svdUBeta[i] *= wInv;

            for (int k = 0; k < j.dTheta.length; k++)
            {
                j.dTheta[k] += j.svdV.get(k, i) * j.svdUBeta[i];
            }
        }

        for (int k = 0; k < j.dTheta.length; k++)
        {
            j.dTheta[k] *= j.weight[k];
        }
    }
}
