/* Ersatz fuer Eigen::JacobiSVD im Port von Blenders intern/iksolver.
 * SPDX-License-Identifier: GPL-2.0-or-later */
package mchorse.bbs_mod.cubic.ik.blender;

/**
 * Einseitiges Jacobi-SVD: A = U * diag(w) * V^T, "thin" wie
 * {@code Eigen::ComputeThinU | ComputeThinV}.
 *
 * ⛔ Vorbedingung ZEILEN >= SPALTEN. Blender haelt sie immer ein: bei
 * {@code task_size >= dof} zerlegt es J, sonst J^T ({@code m_transpose}) — beide
 * Male hat die uebergebene Matrix mindestens so viele Zeilen wie Spalten. Faellt
 * die Vorbedingung, wird hier laut geworfen statt still falsch gerechnet.
 *
 * Singulaerwerte kommen ABSTEIGEND heraus, wie bei Eigen — SDLS laeuft zwar ueber
 * alle Werte, aber {@code ComputeNullProjection} zaehlt den Rang ueber eine
 * Schwelle, und Rangzaehlung auf unsortierten Werten ist eine stille Falle.
 *
 * Vorzeichen: U- und V-Spalte kippen immer gemeinsam, das Produkt bleibt gleich.
 * SDLS benutzt nur Produkte aus beiden, deshalb ist die Vorzeichenwahl egal.
 */
public final class IKSvd
{
    /** Spaltenorthogonale U (zeilen x spalten). */
    public final IKMatN u;
    /** Orthogonale V (spalten x spalten). */
    public final IKMatN v;
    /** Singulaerwerte, absteigend. */
    public final double[] w;

    private static final int MAX_DURCHGAENGE = 60;
    private static final double TOLERANZ = 1e-15;

    public IKSvd(IKMatN a)
    {
        if (a.zeilen < a.spalten)
        {
            throw new IllegalArgumentException(
                "IKSvd braucht zeilen >= spalten, bekam " + a.zeilen + "x" + a.spalten);
        }

        int m = a.zeilen;
        int n = a.spalten;

        this.u = a.kopie();
        this.v = new IKMatN(n, n);
        this.w = new double[n];

        for (int i = 0; i < n; i++)
        {
            this.v.set(i, i, 1D);
        }

        this.drehen(m, n);
        this.laengenAbspalten(m, n);
        this.sortierenAbsteigend(m, n);
    }

    /** Spaltenpaare orthogonalisieren, bis nichts mehr zu drehen ist. */
    private void drehen(int m, int n)
    {
        for (int durchgang = 0; durchgang < MAX_DURCHGAENGE; durchgang++)
        {
            double groessteAbweichung = 0D;

            for (int p = 0; p < n - 1; p++)
            {
                for (int q = p + 1; q < n; q++)
                {
                    double alpha = 0D;
                    double beta = 0D;
                    double gamma = 0D;

                    for (int i = 0; i < m; i++)
                    {
                        double up = this.u.get(i, p);
                        double uq = this.u.get(i, q);

                        alpha += up * up;
                        beta += uq * uq;
                        gamma += up * uq;
                    }

                    if (gamma == 0D || alpha == 0D || beta == 0D)
                    {
                        continue;
                    }

                    double abweichung = Math.abs(gamma) / Math.sqrt(alpha * beta);

                    groessteAbweichung = Math.max(groessteAbweichung, abweichung);

                    if (abweichung <= TOLERANZ)
                    {
                        continue;
                    }

                    /* Jacobi-Winkel, numerisch stabile Form (ohne tan/atan). */
                    double zeta = (beta - alpha) / (2D * gamma);
                    double t = Math.signum(zeta) / (Math.abs(zeta) + Math.sqrt(1D + zeta * zeta));

                    if (zeta == 0D)
                    {
                        t = 1D;
                    }

                    double c = 1D / Math.sqrt(1D + t * t);
                    double s = c * t;

                    this.spaltenDrehen(this.u, m, p, q, c, s);
                    this.spaltenDrehen(this.v, n, p, q, c, s);
                }
            }

            if (groessteAbweichung <= TOLERANZ)
            {
                break;
            }
        }
    }

    private void spaltenDrehen(IKMatN mat, int zeilen, int p, int q, double c, double s)
    {
        for (int i = 0; i < zeilen; i++)
        {
            double a = mat.get(i, p);
            double b = mat.get(i, q);

            mat.set(i, p, c * a - s * b);
            mat.set(i, q, s * a + c * b);
        }
    }

    /** Spaltenlaenge ist der Singulaerwert; die Spalte wird darauf normiert. */
    private void laengenAbspalten(int m, int n)
    {
        for (int j = 0; j < n; j++)
        {
            double laenge = 0D;

            for (int i = 0; i < m; i++)
            {
                laenge += this.u.get(i, j) * this.u.get(i, j);
            }

            laenge = Math.sqrt(laenge);
            this.w[j] = laenge;

            if (laenge <= 0D)
            {
                continue;
            }

            for (int i = 0; i < m; i++)
            {
                this.u.set(i, j, this.u.get(i, j) / laenge);
            }
        }
    }

    private void sortierenAbsteigend(int m, int n)
    {
        for (int j = 0; j < n - 1; j++)
        {
            int groesstes = j;

            for (int k = j + 1; k < n; k++)
            {
                if (this.w[k] > this.w[groesstes])
                {
                    groesstes = k;
                }
            }

            if (groesstes == j)
            {
                continue;
            }

            double tmp = this.w[j];

            this.w[j] = this.w[groesstes];
            this.w[groesstes] = tmp;

            this.spaltenTauschen(this.u, m, j, groesstes);
            this.spaltenTauschen(this.v, n, j, groesstes);
        }
    }

    private void spaltenTauschen(IKMatN mat, int zeilen, int a, int b)
    {
        for (int i = 0; i < zeilen; i++)
        {
            double tmp = mat.get(i, a);

            mat.set(i, a, mat.get(i, b));
            mat.set(i, b, tmp);
        }
    }
}
