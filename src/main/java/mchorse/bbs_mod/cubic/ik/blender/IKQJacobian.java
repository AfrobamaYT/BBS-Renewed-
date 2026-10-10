/* Uebersetzt aus Blenders intern/iksolver/intern/IK_QJacobian.cpp.
 * SPDX-FileCopyrightText: 2001-2002 NaN Holding BV. All rights reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Direct-Blender-Port: https://projects.blender.org/blender/blender/src/commit/67807e1800cc48cc7bff3c793525e1179a4d64ca/intern/iksolver/intern/IK_QJacobian.cpp
 * BBS-Modification: 2026-10-10 - Re-audited against pinned Blender; original revision unknown.
 * See docs/BLENDER-IK-PROVENANCE-2026-10-10.md for scope and parity evidence. */
package mchorse.bbs_mod.cubic.ik.blender;

/**
 * Die Jacobi-Matrix der Kette und ihre gedaempfte Pseudoinverse.
 *
 * Die Daempfung selbst steht in {@link IKQJacobianDaempfung} — sonst waere die
 * Datei ueber der Monolithgrenze. Aufgeteilt ist nur die Ablage, nicht die Logik.
 */
public final class IKQJacobian
{
    int dof;
    int taskSize;

    IKMatN jacobian;
    /** Bei taskSize < dof wird J^T zerlegt; U und V tauschen dann die Rollen. */
    boolean transponiert;

    IKMatN svdU;
    IKMatN svdV;
    double[] svdW;
    double[] svdUBeta;

    double[] beta;
    double[] dTheta;
    double[] dThetaTmp;
    double[] dNormWeight;
    double[] norm;
    double[] weight;
    double[] weightSqrt;

    IKMatN nullspace;

    boolean sdls = true;
    double minDamp = 1D;

    public void armMatrices(int dof, int taskSize)
    {
        this.dof = dof;
        this.taskSize = taskSize;

        this.jacobian = new IKMatN(taskSize, dof);

        this.dTheta = new double[dof];
        this.dThetaTmp = new double[dof];
        this.dNormWeight = new double[dof];
        this.norm = new double[dof];
        this.beta = new double[taskSize];
        this.nullspace = new IKMatN(dof, dof);

        this.weight = new double[dof];
        this.weightSqrt = new double[dof];

        java.util.Arrays.fill(this.weight, 1D);
        java.util.Arrays.fill(this.weightSqrt, 1D);

        this.transponiert = taskSize < dof;
        this.svdUBeta = new double[this.transponiert ? taskSize : dof];
    }

    public void setzeDoFGewicht(int dof, double gewicht)
    {
        this.weight[dof] = gewicht;
        this.weightSqrt[dof] = Math.sqrt(gewicht);
    }

    public void setzeBetas(int id, IKVec3 v)
    {
        this.beta[id] = v.x;
        this.beta[id + 1] = v.y;
        this.beta[id + 2] = v.z;
    }

    public void setzeAbleitungen(int id, int dofId, IKVec3 v, double normGewicht)
    {
        this.jacobian.set(id, dofId, v.x * this.weightSqrt[dofId]);
        this.jacobian.set(id + 1, dofId, v.y * this.weightSqrt[dofId]);
        this.jacobian.set(id + 2, dofId, v.z * this.weightSqrt[dofId]);

        this.dNormWeight[dofId] = normGewicht;
    }

    public void invertieren()
    {
        if (this.transponiert)
        {
            /* SVD zerlegt J^T in V*W*U^T, also J = U*W*V^T und Jinv = V*Winv*U^T.
             * Eigen liefert dann matrixV() als unser U und matrixU() als unser V —
             * bei uns entsprechend svd.v und svd.u vertauscht. */
            IKSvd svd = new IKSvd(this.jacobian.transponiert());

            this.svdU = svd.v;
            this.svdV = svd.u;
            this.svdW = svd.w;
        }
        else
        {
            IKSvd svd = new IKSvd(this.jacobian);

            this.svdU = svd.u;
            this.svdV = svd.v;
            this.svdW = svd.w;
        }

        if (this.sdls)
        {
            IKQJacobianDaempfung.sdls(this);
        }
        else
        {
            IKQJacobianDaempfung.dls(this);
        }
    }

    /** Nullraum-Projektion aus V; false, wenn der Rang die Aufgabe nicht traegt. */
    public boolean berechneNullProjektion()
    {
        double epsilon = 1e-10;
        int rang = 0;

        for (double v : this.svdW)
        {
            if (v > epsilon)
            {
                rang++;
            }
        }

        if (rang < this.taskSize)
        {
            return false;
        }

        IKMatN basis = new IKMatN(this.svdV.zeilen, rang);
        int b = 0;

        for (int i = 0; i < this.svdW.length; i++)
        {
            if (this.svdW[i] <= epsilon)
            {
                continue;
            }

            for (int j = 0; j < this.svdV.zeilen; j++)
            {
                basis.set(j, b, this.svdV.get(j, i));
            }

            b++;
        }

        this.nullspace = basis.mal(basis.transponiert());

        for (int i = 0; i < this.nullspace.zeilen; i++)
        {
            for (int j = 0; j < this.nullspace.spalten; j++)
            {
                double w = this.nullspace.get(i, j);

                this.nullspace.set(i, j, i == j ? 1D - w : -w);
            }
        }

        return true;
    }

    public void unteraufgabe(IKQJacobian andere)
    {
        if (!this.berechneNullProjektion())
        {
            return;
        }

        andere.einschraenken(this.dTheta, this.nullspace);
        andere.invertieren();

        /* Blender daempft die Nebenaufgabe bewusst NICHT mit minDamp — der
         * auskommentierte Faktor steht dort mit Begruendung ("shaking"). */
        for (int i = 0; i < this.dTheta.length; i++)
        {
            this.dTheta[i] = this.dTheta[i] + andere.winkelAenderung(i);
        }
    }

    public void einschraenken(double[] dTheta, IKMatN nullspace)
    {
        double[] betaSub = this.jacobian.mal(dTheta);

        for (int i = 0; i < this.taskSize; i++)
        {
            this.beta[i] -= betaSub[i];
        }

        this.jacobian = this.jacobian.mal(nullspace);
    }

    public void sperren(int dofId, double delta)
    {
        for (int i = 0; i < this.taskSize; i++)
        {
            this.beta[i] -= this.jacobian.get(i, dofId) * delta;
            this.jacobian.set(i, dofId, 0D);
        }

        this.norm[dofId] = 0D;
        this.dTheta[dofId] = 0D;
    }

    public double winkelAenderung(int dofId)
    {
        return this.dTheta[dofId];
    }

    public double winkelAenderungsNorm()
    {
        double mx = 0D;

        for (int i = 0; i < this.dTheta.length; i++)
        {
            mx = Math.max(Math.abs(this.dTheta[i] * this.dNormWeight[i]), mx);
        }

        return mx;
    }
}
