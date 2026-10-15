/* Uebersetzt aus Blenders intern/iksolver (IK_Math.h).
 * SPDX-FileCopyrightText: 2001-2002 NaN Holding BV. All rights reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later */
package mchorse.bbs_mod.cubic.ik.blender;

/**
 * 3x3 in doppelter Genauigkeit, ZEILENMAJOR adressiert wie Eigens {@code M(row, col)}.
 *
 * ⛔ Warum nicht JOMLs Matrix3d: die ist SPALTENMAJOR ({@code m01} = Spalte 0, Zeile 1).
 * Eine verwechselte Konvention transponiert still und erzeugt plausible, falsche
 * Biegungen — genau der Fehler, den man erst am fertigen Bild sieht. Hier steht die
 * Konvention im Zugriff selbst: {@code get(zeile, spalte)}.
 */
public final class IKMat3
{
    /** m[zeile][spalte] */
    public final double[][] m = new double[3][3];

    public IKMat3()
    {}

    public IKMat3(IKMat3 other)
    {
        this.set(other);
    }

    public static IKMat3 identitaet()
    {
        IKMat3 r = new IKMat3();

        r.m[0][0] = r.m[1][1] = r.m[2][2] = 1D;

        return r;
    }

    /** Wie {@code CreateMatrix(xx, xy, …)} in IK_Math.h — Argumente zeilenweise. */
    public static IKMat3 von(double xx, double xy, double xz,
                             double yx, double yy, double yz,
                             double zx, double zy, double zz)
    {
        IKMat3 r = new IKMat3();

        r.m[0][0] = xx; r.m[0][1] = xy; r.m[0][2] = xz;
        r.m[1][0] = yx; r.m[1][1] = yy; r.m[1][2] = yz;
        r.m[2][0] = zx; r.m[2][1] = zy; r.m[2][2] = zz;

        return r;
    }

    public double get(int zeile, int spalte)
    {
        return this.m[zeile][spalte];
    }

    public void set(int zeile, int spalte, double wert)
    {
        this.m[zeile][spalte] = wert;
    }

    public IKMat3 set(IKMat3 other)
    {
        for (int i = 0; i < 3; i++)
        {
            System.arraycopy(other.m[i], 0, this.m[i], 0, 3);
        }

        return this;
    }

    public IKVec3 zeile(int i)
    {
        return new IKVec3(this.m[i][0], this.m[i][1], this.m[i][2]);
    }

    public void setzeZeile(int i, IKVec3 v)
    {
        this.m[i][0] = v.x;
        this.m[i][1] = v.y;
        this.m[i][2] = v.z;
    }

    public IKVec3 spalte(int j)
    {
        return new IKVec3(this.m[0][j], this.m[1][j], this.m[2][j]);
    }

    /** this * other (neues Objekt). */
    public IKMat3 mal(IKMat3 other)
    {
        IKMat3 r = new IKMat3();

        for (int i = 0; i < 3; i++)
        {
            for (int j = 0; j < 3; j++)
            {
                r.m[i][j] = this.m[i][0] * other.m[0][j]
                          + this.m[i][1] * other.m[1][j]
                          + this.m[i][2] * other.m[2][j];
            }
        }

        return r;
    }

    /** this * v (Spaltenvektor). */
    public IKVec3 mal(IKVec3 v)
    {
        return new IKVec3(
            this.m[0][0] * v.x + this.m[0][1] * v.y + this.m[0][2] * v.z,
            this.m[1][0] * v.x + this.m[1][1] * v.y + this.m[1][2] * v.z,
            this.m[2][0] * v.x + this.m[2][1] * v.y + this.m[2][2] * v.z);
    }

    public IKMat3 transponiert()
    {
        IKMat3 r = new IKMat3();

        for (int i = 0; i < 3; i++)
        {
            for (int j = 0; j < 3; j++)
            {
                r.m[i][j] = this.m[j][i];
            }
        }

        return r;
    }

    @Override
    public String toString()
    {
        StringBuilder b = new StringBuilder("IKMat3[");

        for (int i = 0; i < 3; i++)
        {
            b.append(i > 0 ? " | " : "");

            for (int j = 0; j < 3; j++)
            {
                b.append(j > 0 ? ", " : "").append(String.format("%.6f", this.m[i][j]));
            }
        }

        return b.append(']').toString();
    }
}
