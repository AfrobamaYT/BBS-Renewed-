/* Uebersetzt aus Blenders intern/iksolver (IK_Math.h).
 * SPDX-FileCopyrightText: 2001-2002 NaN Holding BV. All rights reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Direct-Blender-Port: https://projects.blender.org/blender/blender/src/commit/67807e1800cc48cc7bff3c793525e1179a4d64ca/intern/iksolver/intern/IK_Math.h
 * BBS-Modification: 2026-10-10 - Re-audited against pinned Blender; original revision unknown.
 * See docs/BLENDER-IK-PROVENANCE-2026-10-10.md for scope and parity evidence. */
package mchorse.bbs_mod.cubic.ik.blender;

/**
 * Veraenderlicher 3er-Vektor in doppelter Genauigkeit — Blenders Solver rechnet
 * durchgehend {@code double}, und in {@code float} weicht die Jacobi-Iteration
 * sichtbar ab. Eigener Typ statt JOMLs Vector3d, damit er zu {@link IKMat3}
 * (zeilenmajor) passt und keine Konvention dazwischen steht.
 */
public final class IKVec3
{
    public double x;
    public double y;
    public double z;

    public IKVec3()
    {}

    public IKVec3(double x, double y, double z)
    {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public IKVec3(IKVec3 other)
    {
        this(other.x, other.y, other.z);
    }

    public IKVec3 set(double x, double y, double z)
    {
        this.x = x;
        this.y = y;
        this.z = z;

        return this;
    }

    public IKVec3 set(IKVec3 other)
    {
        return this.set(other.x, other.y, other.z);
    }

    public double get(int i)
    {
        return i == 0 ? this.x : i == 1 ? this.y : this.z;
    }

    public void set(int i, double wert)
    {
        if (i == 0) this.x = wert;
        else if (i == 1) this.y = wert;
        else this.z = wert;
    }

    public IKVec3 plus(IKVec3 o)
    {
        return new IKVec3(this.x + o.x, this.y + o.y, this.z + o.z);
    }

    public IKVec3 minus(IKVec3 o)
    {
        return new IKVec3(this.x - o.x, this.y - o.y, this.z - o.z);
    }

    public IKVec3 mal(double s)
    {
        return new IKVec3(this.x * s, this.y * s, this.z * s);
    }

    public double punkt(IKVec3 o)
    {
        return this.x * o.x + this.y * o.y + this.z * o.z;
    }

    public IKVec3 kreuz(IKVec3 o)
    {
        return new IKVec3(
            this.y * o.z - this.z * o.y,
            this.z * o.x - this.x * o.z,
            this.x * o.y - this.y * o.x);
    }

    public double laenge()
    {
        return Math.sqrt(this.punkt(this));
    }

    @Override
    public String toString()
    {
        return String.format("(%.6f, %.6f, %.6f)", this.x, this.y, this.z);
    }
}
