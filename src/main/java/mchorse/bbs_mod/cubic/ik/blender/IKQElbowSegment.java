/* Uebersetzt aus Blenders intern/iksolver/intern/IK_QSegment.cpp.
 * SPDX-FileCopyrightText: 2001-2002 NaN Holding BV. All rights reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Direct-Blender-Port: https://projects.blender.org/blender/blender/src/commit/67807e1800cc48cc7bff3c793525e1179a4d64ca/intern/iksolver/intern/IK_QSegment.cpp
 * BBS-Modification: 2026-10-15 - Revalidated against this baseline; original revision unknown.
 * See docs/BLENDER-IK-PROVENANCE-2026-10-15.md for scope and parity. */
package mchorse.bbs_mod.cubic.ik.blender;

/**
 * Ellbogengelenk: ein Scharnier um X oder Z PLUS Twist um die Knochenachse Y — Blenders
 * {@code IK_QElbowSegment} (frei sind X+Y oder Y+Z). Winkel und Twist werden getrennt
 * gefuehrt; die Basis ist {@code Dreh(winkel, achse) * Twist(twist)}.
 */
public final class IKQElbowSegment extends IKQSegment
{
    private final int achse;

    private double twist;
    private double winkel;
    private double cosTwist = 1D;
    private double sinTwist;
    private double neuerWinkel;
    private double neuerTwist;

    private boolean grenze;
    private boolean grenzeTwist;
    private double min;
    private double max;
    private double minTwist;
    private double maxTwist;

    public IKQElbowSegment(int achse)
    {
        super(2, false);

        this.achse = achse;
    }

    @Override
    public void setzeBasis(IKMat3 basis)
    {
        this.twist = IKMath.berechneTwist(basis);
        this.winkel = IKMath.eulerWinkelAusMatrix(basis, this.achse);
        this.basis = IKMath.drehmatrix(this.winkel, this.achse).mal(IKMath.twistMatrix(this.twist));
    }

    @Override
    public IKVec3 achse(int dof)
    {
        if (dof == 0)
        {
            IKVec3 v = this.achse == 0
                ? new IKVec3(this.cosTwist, 0D, this.sinTwist)
                : new IKVec3(-this.sinTwist, 0D, this.cosTwist);

            return this.globalTransform.linear.mal(v);
        }

        return this.globalTransform.linear.spalte(1);
    }

    @Override
    public boolean aktualisiereWinkel(IKQJacobian jacobian, IKVec3 delta, boolean[] klemm)
    {
        if (this.locked[0] && this.locked[1])
        {
            return false;
        }

        klemm[0] = klemm[1] = false;

        if (!this.locked[0])
        {
            this.neuerWinkel = this.winkel + jacobian.winkelAenderung(this.doFId);

            if (this.grenze)
            {
                if (this.neuerWinkel > this.max)
                {
                    delta.x = this.max - this.winkel;
                    this.neuerWinkel = this.max;
                    klemm[0] = true;
                }
                else if (this.neuerWinkel < this.min)
                {
                    delta.x = this.min - this.winkel;
                    this.neuerWinkel = this.min;
                    klemm[0] = true;
                }
            }
        }

        if (!this.locked[1])
        {
            this.neuerTwist = this.twist + jacobian.winkelAenderung(this.doFId + 1);

            if (this.grenzeTwist)
            {
                if (this.neuerTwist > this.maxTwist)
                {
                    delta.y = this.maxTwist - this.twist;
                    this.neuerTwist = this.maxTwist;
                    klemm[1] = true;
                }
                else if (this.neuerTwist < this.minTwist)
                {
                    delta.y = this.minTwist - this.twist;
                    this.neuerTwist = this.minTwist;
                    klemm[1] = true;
                }
            }
        }

        return klemm[0] || klemm[1];
    }

    @Override
    public void sperren(int dof, IKQJacobian jacobian, IKVec3 delta)
    {
        if (dof == 0)
        {
            this.locked[0] = true;
            jacobian.sperren(this.doFId, delta.x);
        }
        else
        {
            this.locked[1] = true;
            jacobian.sperren(this.doFId + 1, delta.y);
        }
    }

    @Override
    public void aktualisiereWinkelAnwenden()
    {
        this.winkel = this.neuerWinkel;
        this.twist = this.neuerTwist;
        this.sinTwist = Math.sin(this.twist);
        this.cosTwist = Math.cos(this.twist);
        this.basis = IKMath.drehmatrix(this.winkel, this.achse).mal(IKMath.drehmatrix(this.sinTwist, this.cosTwist, 1));
    }

    @Override
    public void setzeGrenze(int achse, double lmin, double lmax)
    {
        if (lmin > lmax)
        {
            return;
        }

        lmin = IKMath.klemme(lmin, -Math.PI, Math.PI);
        lmax = IKMath.klemme(lmax, -Math.PI, Math.PI);

        if (achse == 1)
        {
            this.minTwist = lmin;
            this.maxTwist = lmax;
            this.grenzeTwist = true;
        }
        else if (achse == this.achse)
        {
            this.min = lmin;
            this.max = lmax;
            this.grenze = true;
        }
    }

    @Override
    public void setzeGewicht(int achse, double gewicht)
    {
        if (achse == this.achse)
        {
            this.weight[0] = gewicht;
        }
        else if (achse == 1)
        {
            this.weight[1] = gewicht;
        }
    }
}
