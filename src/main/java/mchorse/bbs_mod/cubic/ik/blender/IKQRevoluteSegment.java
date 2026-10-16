/* Uebersetzt aus Blenders intern/iksolver/intern/IK_QSegment.cpp.
 * SPDX-FileCopyrightText: 2001-2002 NaN Holding BV. All rights reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Direct-Blender-Port: https://projects.blender.org/blender/blender/src/commit/67807e1800cc48cc7bff3c793525e1179a4d64ca/intern/iksolver/intern/IK_QSegment.cpp
 * BBS-Modification: 2026-10-15 - Revalidated against this baseline; original revision unknown.
 * See docs/BLENDER-IK-PROVENANCE-2026-10-15.md for scope and parity. */
package mchorse.bbs_mod.cubic.ik.blender;

/**
 * Scharnier: ein Freiheitsgrad um eine feste Achse.
 *
 * Das ist der Typ, den Knie und Ellbogen unseres Rigs bekommen — gemessen in
 * Blender: Knie {@code lock=(false,true,true)} laesst nur X frei, Ellbogen
 * {@code lock=(true,true,false)} nur Z. Genau daher weiss die Kette, in welche
 * Richtung sie knickt; ein Zweibein-IK ohne diese Sperren raet die Biegeebene.
 */
public final class IKQRevoluteSegment extends IKQSegment
{
    private final int achse;

    private double winkel;
    private double neuerWinkel;

    private boolean grenze;
    private double min;
    private double max;

    public IKQRevoluteSegment(int achse)
    {
        super(1, false);

        this.achse = achse;
    }

    @Override
    public void setzeBasis(IKMat3 basis)
    {
        /* Um Y ist die Drehung ein Twist — Euler-Ruecklesung waere dort mehrdeutig. */
        if (this.achse == 1)
        {
            this.winkel = IKMath.berechneTwist(basis);
            this.basis = IKMath.twistMatrix(this.winkel);
        }
        else
        {
            this.winkel = IKMath.eulerWinkelAusMatrix(basis, this.achse);
            this.basis = IKMath.drehmatrix(this.winkel, this.achse);
        }
    }

    @Override
    public IKVec3 achse(int dof)
    {
        return this.globalTransform.linear.spalte(this.achse);
    }

    @Override
    public boolean aktualisiereWinkel(IKQJacobian jacobian, IKVec3 delta, boolean[] klemm)
    {
        if (this.locked[0])
        {
            return false;
        }

        this.neuerWinkel = this.winkel + jacobian.winkelAenderung(this.doFId);

        klemm[0] = false;

        if (!this.grenze)
        {
            return false;
        }

        if (this.neuerWinkel > this.max)
        {
            delta.x = this.max - this.winkel;
        }
        else if (this.neuerWinkel < this.min)
        {
            delta.x = this.min - this.winkel;
        }
        else
        {
            return false;
        }

        klemm[0] = true;
        this.neuerWinkel = this.winkel + delta.x;

        return true;
    }

    @Override
    public void sperren(int dof, IKQJacobian jacobian, IKVec3 delta)
    {
        this.locked[0] = true;

        jacobian.sperren(this.doFId, delta.x);
    }

    @Override
    public void aktualisiereWinkelAnwenden()
    {
        this.winkel = this.neuerWinkel;
        this.basis = IKMath.drehmatrix(this.winkel, this.achse);
    }

    @Override
    public void setzeGrenze(int achse, double lmin, double lmax)
    {
        if (lmin > lmax || this.achse != achse)
        {
            return;
        }

        this.min = IKMath.klemme(lmin, -Math.PI, Math.PI);
        this.max = IKMath.klemme(lmax, -Math.PI, Math.PI);
        this.grenze = true;
    }

    @Override
    public void setzeGewicht(int achse, double gewicht)
    {
        if (achse == this.achse)
        {
            this.weight[0] = gewicht;
        }
    }
}
