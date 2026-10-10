/* Teil des Ports von Blenders intern/iksolver.
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Direct-Blender-Port: https://projects.blender.org/blender/blender/src/commit/67807e1800cc48cc7bff3c793525e1179a4d64ca/intern/iksolver/intern/IK_QJacobian.cpp
 * Package baseline only: independent Java support, not copied Eigen source.
 * BBS-Modification: 2026-10-10 - Re-audited against pinned Blender; original revision unknown.
 * See docs/BLENDER-IK-PROVENANCE-2026-10-10.md for scope and parity evidence. */
package mchorse.bbs_mod.cubic.ik.blender;

/**
 * Dynamische Matrix in doppelter Genauigkeit, zeilenmajor gespeichert und
 * {@code get(zeile, spalte)} adressiert — dieselbe Konvention wie {@link IKMat3},
 * damit beim Uebersetzen von Eigens {@code M(row, col)} nichts kippt.
 */
public final class IKMatN
{
    public final int zeilen;
    public final int spalten;
    public final double[] w;

    public IKMatN(int zeilen, int spalten)
    {
        this.zeilen = zeilen;
        this.spalten = spalten;
        this.w = new double[zeilen * spalten];
    }

    public double get(int zeile, int spalte)
    {
        return this.w[zeile * this.spalten + spalte];
    }

    public void set(int zeile, int spalte, double wert)
    {
        this.w[zeile * this.spalten + spalte] = wert;
    }

    public void addiere(int zeile, int spalte, double wert)
    {
        this.w[zeile * this.spalten + spalte] += wert;
    }

    public void nullen()
    {
        java.util.Arrays.fill(this.w, 0D);
    }

    public IKMatN kopie()
    {
        IKMatN r = new IKMatN(this.zeilen, this.spalten);

        System.arraycopy(this.w, 0, r.w, 0, this.w.length);

        return r;
    }

    public IKMatN transponiert()
    {
        IKMatN r = new IKMatN(this.spalten, this.zeilen);

        for (int i = 0; i < this.zeilen; i++)
        {
            for (int j = 0; j < this.spalten; j++)
            {
                r.set(j, i, this.get(i, j));
            }
        }

        return r;
    }

    /** this * other. */
    public IKMatN mal(IKMatN other)
    {
        if (this.spalten != other.zeilen)
        {
            throw new IllegalArgumentException(
                "Matrizen passen nicht: " + this.zeilen + "x" + this.spalten
                + " mal " + other.zeilen + "x" + other.spalten);
        }

        IKMatN r = new IKMatN(this.zeilen, other.spalten);

        for (int i = 0; i < this.zeilen; i++)
        {
            for (int k = 0; k < this.spalten; k++)
            {
                double a = this.get(i, k);

                if (a == 0D)
                {
                    continue;
                }

                for (int j = 0; j < other.spalten; j++)
                {
                    r.addiere(i, j, a * other.get(k, j));
                }
            }
        }

        return r;
    }

    /** this * v, v als Spaltenvektor der Laenge {@link #spalten}. */
    public double[] mal(double[] v)
    {
        double[] r = new double[this.zeilen];

        for (int i = 0; i < this.zeilen; i++)
        {
            double s = 0D;

            for (int j = 0; j < this.spalten; j++)
            {
                s += this.get(i, j) * v[j];
            }

            r[i] = s;
        }

        return r;
    }
}
