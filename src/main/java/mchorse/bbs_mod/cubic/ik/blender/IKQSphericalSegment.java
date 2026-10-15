/* Uebersetzt aus Blenders intern/iksolver/intern/IK_QSegment.cpp.
 * SPDX-FileCopyrightText: 2001-2002 NaN Holding BV. All rights reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later */
package mchorse.bbs_mod.cubic.ik.blender;

/**
 * Kugelgelenk: drei Freiheitsgrade. In unserem Rig sind das Huefte und Schulter
 * (gemessen: keine Sperren gesetzt).
 *
 * ⛔ Die Basis wird ueber die <b>Rodrigues-Formel</b> direkt als Matrix
 * fortgeschrieben, nicht ueber Euler-Winkel — Blender begruendet das mit
 * Singularitaeten und weicher Integration. Wer hier auf Euler umbaut, bekommt
 * Gimbal-Sprunge in genau den Posen, in denen es niemand nachstellt.
 */
public final class IKQSphericalSegment extends IKQSegment
{
    private IKMat3 neueBasis = IKMat3.identitaet();

    private boolean grenzeX;
    private boolean grenzeY;
    private boolean grenzeZ;

    private final double[] min = new double[2];
    private final double[] max = new double[2];
    private double minY;
    private double maxY;

    private double gesperrtAx;
    private double gesperrtAy;
    private double gesperrtAz;

    public IKQSphericalSegment()
    {
        super(3, false);
    }

    @Override
    public IKVec3 achse(int dof)
    {
        return this.globalTransform.linear.spalte(dof);
    }

    @Override
    public void setzeGrenze(int achse, double lmin, double lmax)
    {
        if (lmin > lmax)
        {
            return;
        }

        if (achse == 1)
        {
            this.minY = IKMath.klemme(lmin, -Math.PI, Math.PI);
            this.maxY = IKMath.klemme(lmax, -Math.PI, Math.PI);
            this.grenzeY = true;

            return;
        }

        /* Auf Achsenwinkel-Parameter umrechnen — und dabei das Vorzeichen drehen,
         * so steht es im Original. */
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
        this.weight[achse] = gewicht;
    }

    @Override
    public boolean aktualisiereWinkel(IKQJacobian jacobian, IKVec3 delta, boolean[] klemm)
    {
        if (this.locked[0] && this.locked[1] && this.locked[2])
        {
            return false;
        }

        IKVec3 dq = new IKVec3(
            jacobian.winkelAenderung(this.doFId),
            jacobian.winkelAenderung(this.doFId + 1),
            jacobian.winkelAenderung(this.doFId + 2));

        this.neueBasis = this.basis.mal(rodrigues(dq));

        if (!this.grenzeY && !this.grenzeX && !this.grenzeZ)
        {
            return false;
        }

        IKVec3 a = IKMath.sphaerischeBereichsParameter(this.neueBasis);

        if (this.locked[0]) a.x = this.gesperrtAx;
        if (this.locked[1]) a.y = this.gesperrtAy;
        if (this.locked[2]) a.z = this.gesperrtAz;

        double[] axz = {a.x, a.z};
        double ay = a.y;

        klemm[0] = klemm[1] = klemm[2] = false;

        if (this.grenzeY)
        {
            if (a.y > this.maxY) { ay = this.maxY; klemm[1] = true; }
            else if (a.y < this.minY) { ay = this.minY; klemm[1] = true; }
        }

        if (this.grenzeX && this.grenzeZ)
        {
            if (IKMath.ellipseKlemmen(axz, this.min, this.max))
            {
                klemm[0] = klemm[2] = true;
            }
        }
        else if (this.grenzeX)
        {
            if (axz[0] < this.min[0]) { axz[0] = this.min[0]; klemm[0] = true; }
            else if (axz[0] > this.max[0]) { axz[0] = this.max[0]; klemm[0] = true; }
        }
        else if (this.grenzeZ)
        {
            if (axz[1] < this.min[1]) { axz[1] = this.min[1]; klemm[2] = true; }
            else if (axz[1] > this.max[1]) { axz[1] = this.max[1]; klemm[2] = true; }
        }

        if (!klemm[0] && !klemm[1] && !klemm[2])
        {
            if (this.locked[0] || this.locked[1] || this.locked[2])
            {
                this.neueBasis = IKMath.swingMatrix(axz[0], axz[1]).mal(IKMath.twistMatrix(ay));
            }

            return false;
        }

        this.neueBasis = IKMath.swingMatrix(axz[0], axz[1]).mal(IKMath.twistMatrix(ay));

        delta.set(IKMath.matrixZuAchsenwinkel(this.basis.transponiert().mal(this.neueBasis)));

        if (!(this.locked[0] || this.locked[2]) && (klemm[0] || klemm[2]))
        {
            this.gesperrtAx = axz[0];
            this.gesperrtAz = axz[1];
        }

        if (!this.locked[1] && klemm[1])
        {
            this.gesperrtAy = ay;
        }

        return true;
    }

    /** Drehmatrix zum Achsenwinkel-Vektor dq (Rodrigues), wie im Original. */
    private static IKMat3 rodrigues(IKVec3 dq)
    {
        double theta = dq.laenge();

        if (IKMath.fuzzyNull(theta))
        {
            return IKMat3.identitaet();
        }

        IKVec3 w = dq.mal(1D / theta);

        double sinus = Math.sin(theta);
        double kosinus = Math.cos(theta);
        double inv = 1D - kosinus;

        double xs = w.x * sinus;
        double ys = w.y * sinus;
        double zs = w.z * sinus;

        double xx = w.x * w.x * inv;
        double xy = w.x * w.y * inv;
        double xz = w.x * w.z * inv;
        double yy = w.y * w.y * inv;
        double yz = w.y * w.z * inv;
        double zz = w.z * w.z * inv;

        return IKMat3.von(
            kosinus + xx, -zs + xy, ys + xz,
            zs + xy, kosinus + yy, -xs + yz,
            -ys + xz, xs + yz, kosinus + zz);
    }

    @Override
    public void sperren(int dof, IKQJacobian jacobian, IKVec3 delta)
    {
        if (dof == 1)
        {
            this.locked[1] = true;
            jacobian.sperren(this.doFId + 1, delta.y);
        }
        else
        {
            this.locked[0] = this.locked[2] = true;
            jacobian.sperren(this.doFId, delta.x);
            jacobian.sperren(this.doFId + 2, delta.z);
        }
    }

    @Override
    public void aktualisiereWinkelAnwenden()
    {
        this.basis = this.neueBasis;
    }
}
