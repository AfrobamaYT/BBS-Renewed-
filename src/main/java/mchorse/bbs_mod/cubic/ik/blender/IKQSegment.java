/* Uebersetzt aus Blenders intern/iksolver/intern/IK_QSegment.{h,cpp}.
 * SPDX-FileCopyrightText: 2001-2002 NaN Holding BV. All rights reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Direct-Blender-Port: https://projects.blender.org/blender/blender/src/commit/67807e1800cc48cc7bff3c793525e1179a4d64ca/intern/iksolver/intern/IK_QSegment.cpp
 * BBS-Modification: 2026-10-15 - Revalidated against this baseline; original revision unknown.
 * See docs/BLENDER-IK-PROVENANCE-2026-10-15.md for scope and parity. */
package mchorse.bbs_mod.cubic.ik.blender;

/**
 * Ein Kettenglied. <b>Segmente zeigen immer entlang der Y-Achse</b> — das ist
 * Blenders Konvention, keine Wahl: Knochen werden vor dem Loesen in dieses
 * System gedreht und danach zurueck.
 *
 * Die lokale Abbildung ist, in dieser Reihenfolge:
 * {@code translate(start) * rotation(rest_basis) * rotation(basis) * translate(0, laenge, 0)}
 * ⛔ Die Reihenfolge ist bindend. Wer sie vertauscht, bekommt eine Kette, die
 * bei ungedrehter Ruhepose richtig aussieht und sich unter Drehung verbiegt.
 */
public abstract class IKQSegment
{
    IKQSegment parent;
    IKQSegment child;
    IKQSegment sibling;
    IKQSegment composite;

    IKVec3 start = new IKVec3();
    IKMat3 restBasis = IKMat3.identitaet();
    IKMat3 basis = IKMat3.identitaet();
    IKVec3 translation = new IKVec3();

    IKMat3 origBasis = IKMat3.identitaet();
    IKVec3 origTranslation = new IKVec3();

    IKVec3 globalStart = new IKVec3();
    IKAffine globalTransform = new IKAffine();

    final int numDoF;
    final boolean translational;
    int doFId;
    double maxExtension;

    final boolean[] locked = new boolean[3];
    final double[] weight = {1D, 1D, 1D};

    protected IKQSegment(int numDoF, boolean translational)
    {
        this.numDoF = numDoF;
        this.translational = translational;
    }

    public int numberOfDoF()
    {
        return this.numDoF;
    }

    public int doFId()
    {
        return this.doFId;
    }

    public void setDoFId(int id)
    {
        this.doFId = id;
    }

    public double maxExtension()
    {
        return this.maxExtension;
    }

    public boolean translational()
    {
        return this.translational;
    }

    public IKQSegment child()
    {
        return this.child;
    }

    public IKQSegment sibling()
    {
        return this.sibling;
    }

    public IKQSegment composite()
    {
        return this.composite;
    }

    public void setComposite(IKQSegment seg)
    {
        this.composite = seg;
    }

    public boolean gesperrt(int dof)
    {
        return this.locked[dof];
    }

    public void entsperren()
    {
        this.locked[0] = this.locked[1] = this.locked[2] = false;
    }

    public double gewicht(int dof)
    {
        return this.weight[dof];
    }

    public void skaliereGewicht(int dof, double skala)
    {
        this.weight[dof] *= skala;
    }

    public IKVec3 globalStart()
    {
        return this.globalStart;
    }

    public IKVec3 globalEnd()
    {
        return this.globalTransform.translation;
    }

    public IKAffine globalTransform()
    {
        return this.globalTransform;
    }

    public void setzeTransform(IKVec3 start, IKMat3 restBasis, IKMat3 basis, double laenge)
    {
        this.maxExtension = start.laenge() + laenge;

        this.start = new IKVec3(start);
        this.restBasis = new IKMat3(restBasis);

        this.origBasis = new IKMat3(basis);
        this.setzeBasis(basis);

        this.translation = new IKVec3(0D, laenge, 0D);
        this.origTranslation = new IKVec3(this.translation);
    }

    public void setzeElter(IKQSegment parent)
    {
        if (this.parent == parent)
        {
            return;
        }

        if (parent != null)
        {
            this.sibling = parent.child;
            parent.child = this;
        }

        this.parent = parent;
    }

    public IKMat3 basisAenderung()
    {
        return this.origBasis.transponiert().mal(this.basis);
    }

    public IKVec3 translationsAenderung()
    {
        return this.translation.minus(this.origTranslation);
    }

    public void aktualisiereTransform(IKAffine global)
    {
        this.globalStart = global.translation.plus(global.linear.mal(this.start));

        this.globalTransform.translation = new IKVec3(this.globalStart);
        this.globalTransform.linear = global.linear.mal(this.restBasis).mal(this.basis);
        this.globalTransform.verschiebeLokal(this.translation);

        for (IKQSegment seg = this.child; seg != null; seg = seg.sibling)
        {
            seg.aktualisiereTransform(this.globalTransform);
        }
    }

    /** basis := restBasis^-1 * mat * restBasis * basis (restBasis ist orthogonal). */
    public void basisVoranstellen(IKMat3 mat)
    {
        this.basis = this.restBasis.transponiert().mal(mat).mal(this.restBasis).mal(this.basis);
    }

    public void zuruecksetzen()
    {
        this.entsperren();

        this.basis = new IKMat3(this.origBasis);
        this.translation = new IKVec3(this.origTranslation);
        this.setzeBasis(this.basis);

        for (IKQSegment seg = this.child; seg != null; seg = seg.sibling)
        {
            seg.zuruecksetzen();
        }
    }

    public void skalieren(double skala)
    {
        this.start = this.start.mal(skala);
        this.translation = this.translation.mal(skala);
        this.origTranslation = this.origTranslation.mal(skala);
        this.globalStart = this.globalStart.mal(skala);
        this.globalTransform.translation = this.globalTransform.translation.mal(skala);
        this.maxExtension *= skala;
    }

    public void setzeBasis(IKMat3 basis)
    {
        this.basis = new IKMat3(basis);
    }

    public void setzeGrenze(int achse, double lmin, double lmax)
    {}

    public void setzeGewicht(int achse, double gewicht)
    {}

    public void sperren(int dof, IKQJacobian jacobian, IKVec3 delta)
    {}

    /** Drehachse fuer die Ableitung im Jacobi. */
    public abstract IKVec3 achse(int dof);

    /** Winkel aus dTheta lesen und klemmen; true, wenn eine Grenze verletzt wurde. */
    public abstract boolean aktualisiereWinkel(IKQJacobian jacobian, IKVec3 delta, boolean[] klemm);

    public abstract void aktualisiereWinkelAnwenden();
}
