/* Uebersetzt aus Blenders intern/iksolver/intern/IK_QSegment.cpp.
 * SPDX-FileCopyrightText: 2001-2002 NaN Holding BV. All rights reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Direct-Blender-Port: https://projects.blender.org/blender/blender/src/commit/67807e1800cc48cc7bff3c793525e1179a4d64ca/intern/iksolver/intern/IK_QSegment.cpp
 * BBS-Modification: 2026-10-10 - Re-audited against pinned Blender; original revision unknown.
 * See docs/BLENDER-IK-PROVENANCE-2026-10-10.md for scope and parity evidence. */
package mchorse.bbs_mod.cubic.ik.blender;

/**
 * Schwinggelenk: zwei Freiheitsgrade um X und Z, KEIN Twist um die Knochenachse — Blenders
 * {@code IK_QSwingSegment} (nur die Y-Achse gesperrt). Wie das Kugelgelenk ueber Rodrigues
 * fortgeschrieben, der Twist wird nach jedem Schritt entfernt.
 *
 * <p>⛔ Die Grenzpruefung mit nur EINER gesperrten Achse steht so im Original: {@code ax}/{@code az}
 * bleiben dort 0 statt aus den Bereichsparametern zu kommen. Wortgetreu uebernommen — wer das
 * "repariert", rechnet anders als Blender.
 */
public final class IKQSwingSegment extends IKQSegment
{
    private IKMat3 neueBasis = IKMat3.identitaet();

    private boolean grenzeX;
    private boolean grenzeZ;

    private final double[] min = new double[2];
    private final double[] max = new double[2];

    public IKQSwingSegment()
    {
        super(2, false);
    }

    @Override
    public void setzeBasis(IKMat3 basis)
    {
        this.basis = IKMath.ohneTwist(new IKMat3(basis));
    }

    @Override
    public IKVec3 achse(int dof)
    {
        return this.globalTransform.linear.spalte(dof == 0 ? 0 : 2);
    }

    @Override
    public boolean aktualisiereWinkel(IKQJacobian jacobian, IKVec3 delta, boolean[] klemm)
    {
        if (this.locked[0] && this.locked[1])
        {
            return false;
        }

        double dqx = jacobian.winkelAenderung(this.doFId);
        double dqz = jacobian.winkelAenderung(this.doFId + 1);
        double theta = Math.sqrt(dqx * dqx + dqz * dqz);

        if (!IKMath.fuzzyNull(theta))
        {
            double wx = dqx / theta;
            double wz = dqz / theta;
            double sinus = Math.sin(theta);
            double kosinus = Math.cos(theta);
            double inv = 1D - kosinus;
            double xs = wx * sinus;
            double zs = wz * sinus;
            double xx = wx * wx * inv;
            double xz = wx * wz * inv;
            double zz = wz * wz * inv;
            IKMat3 m = IKMat3.von(
                kosinus + xx, -zs, xz,
                zs, kosinus, -xs,
                xz, xs, kosinus + zz);

            this.neueBasis = IKMath.ohneTwist(this.basis.mal(m));
        }
        else
        {
            this.neueBasis = new IKMat3(this.basis);
        }

        if (!this.grenzeX && !this.grenzeZ)
        {
            return false;
        }

        IKVec3 a = IKMath.sphaerischeBereichsParameter(this.neueBasis);
        double[] axz = {0D, 0D};

        klemm[0] = klemm[1] = false;

        if (this.grenzeX && this.grenzeZ)
        {
            axz[0] = a.x;
            axz[1] = a.z;

            if (IKMath.ellipseKlemmen(axz, this.min, this.max))
            {
                klemm[0] = klemm[1] = true;
            }
        }
        else if (this.grenzeX)
        {
            if (axz[0] < this.min[0])
            {
                axz[0] = this.min[0];
                klemm[0] = true;
            }
            else if (axz[0] > this.max[0])
            {
                axz[0] = this.max[0];
                klemm[0] = true;
            }
        }
        else
        {
            if (axz[1] < this.min[1])
            {
                axz[1] = this.min[1];
                klemm[1] = true;
            }
            else if (axz[1] > this.max[1])
            {
                axz[1] = this.max[1];
                klemm[1] = true;
            }
        }

        if (!klemm[0] && !klemm[1])
        {
            return false;
        }

        this.neueBasis = IKMath.swingMatrix(axz[0], axz[1]);

        IKVec3 d = IKMath.matrixZuAchsenwinkel(this.basis.transponiert().mal(this.neueBasis));

        delta.set(d.x, d.z, 0D);

        return true;
    }

    @Override
    public void sperren(int dof, IKQJacobian jacobian, IKVec3 delta)
    {
        this.locked[0] = this.locked[1] = true;
        jacobian.sperren(this.doFId, delta.x);
        jacobian.sperren(this.doFId + 1, delta.y);
    }

    @Override
    public void aktualisiereWinkelAnwenden()
    {
        this.basis = this.neueBasis;
    }

    @Override
    public void setzeGrenze(int achse, double lmin, double lmax)
    {
        if (lmin > lmax)
        {
            return;
        }

        /* Auf Achsenwinkel-Parameter umrechnen, Vorzeichen gedreht — wie im Original. */
        double a = Math.sin(IKMath.klemme(lmin, -Math.PI, Math.PI) * 0.5D);
        double b = Math.sin(IKMath.klemme(lmax, -Math.PI, Math.PI) * 0.5D);

        if (achse == 0)
        {
            this.min[0] = -b;
            this.max[0] = -a;
            this.grenzeX = true;
        }
        else if (achse == 2)
        {
            this.min[1] = -b;
            this.max[1] = -a;
            this.grenzeZ = true;
        }
    }

    @Override
    public void setzeGewicht(int achse, double gewicht)
    {
        if (achse == 0)
        {
            this.weight[0] = gewicht;
        }
        else if (achse == 2)
        {
            this.weight[1] = gewicht;
        }
    }
}
