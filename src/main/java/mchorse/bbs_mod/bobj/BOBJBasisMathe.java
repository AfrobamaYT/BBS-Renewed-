package mchorse.bbs_mod.bobj;

/**
 * Blenders Basis-Aufraeumer aus {@code blenlib/intern/math_matrix_c.cc}, gebraucht vom
 * Skalen-Erbe in {@link BOBJElternTransform}.
 *
 * ⛔ Anordnung ist Blenders: {@code a[i * 4 + j]} ist {@code R[i][j]}, also Komponente j
 * der Basis-SPALTE i. JOMLs {@code Matrix4f.get(float[])} liefert genau das, ohne
 * Umsortieren — wer hier zeilenweise denkt, vergleicht Transponierte und bekommt
 * plausible falsche Zahlen (an genau dem Fehler ist am 2026-08-14 eine ganze Messrunde
 * gestorben).
 */
public final class BOBJBasisMathe
{
    private BOBJBasisMathe()
    {}

    /**
     * {@code orthogonalize_m4_stable(R, 1, normalisieren)} — Scherung raus, Y als feste
     * Hauptachse, weil ein Knochen entlang Y liegt.
     *
     * ⛔ Nicht durch Gram-Schmidt ersetzen: das hier verteilt die Scherungskorrektur zur
     * HAELFTE auf beide Nebenachsen und erhaelt damit die Determinante. Gram-Schmidt
     * bevorzugt die erste Achse und kippt sichtbar, sobald die Scherung gross wird.
     */
    public static void orthogonalisiereUmY(float[] a, boolean normalisieren)
    {
        stabil(a, 1, 0, 2, normalisieren);
    }

    private static void stabil(float[] a, int i1, int i2, int i3, boolean normalisieren)
    {
        float lq = punkt(a, i1, i1);

        if (lq > 0F)
        {
            /* Nebenachsen per Ebenenprojektion auf die Hauptachse senkrecht stellen. */
            madd(a, i2, i1, -punkt(a, i2, i1) / lq);
            madd(a, i3, i1, -punkt(a, i3, i1) / lq);

            if (normalisieren)
            {
                mul(a, i1, (float) (1D / Math.sqrt(lq)));
            }
        }

        float[] n2 = new float[3];
        float[] n3 = new float[3];
        float l2 = einheit(a, i2, n2);
        float l3 = einheit(a, i3, n3);
        float cos = n2[0] * n3[0] + n2[1] * n3[1] + n2[2] * n3[2];
        float betrag = Math.abs(cos);

        /* Nur korrigieren, wenn die Scherung nennenswert und nicht entartet ist. */
        if (betrag > 1e-4F && betrag < 1F - 1.1920929E-7F)
        {
            float winkel = (float) Math.acos(cos);
            float ziel = winkel + ((float) (Math.PI / 2D) - winkel) / 2F;

            for (int k = 0; k < 3; k++)
            {
                n2[k] += n3[k] * -cos;
            }

            float f = (float) (Math.sin(ziel) / laenge(n2));

            for (int k = 0; k < 3; k++)
            {
                n2[k] = n2[k] * f + n3[k] * (float) Math.cos(ziel);
            }

            float[] t = kreuz(n2, n3);

            n3 = kreuz(t, n2);

            float rl = laenge(n3);

            if (rl != 0F)
            {
                for (int k = 0; k < 3; k++)
                {
                    n3[k] /= rl;
                }
            }

            if (!normalisieren)
            {
                /* Skalierung zurueck, aber flaechen- und verhaeltnistreu. */
                float sf = (float) Math.sqrt(Math.sin(winkel));

                setze(a, i2, n2, l2 * sf);
                setze(a, i3, n3, l3 * sf);
            }
        }

        if (normalisieren)
        {
            setze(a, i2, n2, 1F);
            setze(a, i3, n3, 1F);
        }
    }

    /** {@code normalize_m4_ex}: Spaltenlaengen raus nach {@code skala}, Spalten auf 1. */
    public static void normalisiereMitSkala(float[] a, float[] skala)
    {
        for (int i = 0; i < 3; i++)
        {
            float l = laenge(new float[] {a[i * 4], a[i * 4 + 1], a[i * 4 + 2]});

            skala[i] = l;

            if (l != 0F)
            {
                mul(a, i, 1F / l);
                a[i * 4 + 3] /= l;
            }
        }
    }

    /** {@code determinant_m4_mat3_array} — der Volumenfaktor der 3x3. */
    public static float determinante3(float[] a)
    {
        return a[0] * (a[5] * a[10] - a[6] * a[9])
             - a[4] * (a[1] * a[10] - a[2] * a[9])
             + a[8] * (a[1] * a[6] - a[2] * a[5]);
    }

    /** {@code mul_mat3_m4_fl} — nur die drei Basisspalten skalieren, den Ort nicht. */
    public static void skaliereBasis(float[] a, float f)
    {
        for (int i = 0; i < 3; i++)
        {
            mul(a, i, f);
        }
    }

    private static float punkt(float[] a, int i, int j)
    {
        return a[i * 4] * a[j * 4] + a[i * 4 + 1] * a[j * 4 + 1] + a[i * 4 + 2] * a[j * 4 + 2];
    }

    private static void madd(float[] a, int ziel, int quelle, float f)
    {
        for (int k = 0; k < 3; k++)
        {
            a[ziel * 4 + k] += a[quelle * 4 + k] * f;
        }
    }

    private static void mul(float[] a, int i, float f)
    {
        for (int k = 0; k < 3; k++)
        {
            a[i * 4 + k] *= f;
        }
    }

    private static float einheit(float[] a, int i, float[] raus)
    {
        for (int k = 0; k < 3; k++)
        {
            raus[k] = a[i * 4 + k];
        }

        float l = laenge(raus);

        for (int k = 0; k < 3; k++)
        {
            raus[k] = l == 0F ? 0F : raus[k] / l;
        }

        return l;
    }

    private static void setze(float[] a, int i, float[] v, float f)
    {
        for (int k = 0; k < 3; k++)
        {
            a[i * 4 + k] = v[k] * f;
        }
    }

    private static float laenge(float[] v)
    {
        return (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    }

    private static float[] kreuz(float[] a, float[] b)
    {
        return new float[] {a[1] * b[2] - a[2] * b[1],
                            a[2] * b[0] - a[0] * b[2],
                            a[0] * b[1] - a[1] * b[0]};
    }
}
