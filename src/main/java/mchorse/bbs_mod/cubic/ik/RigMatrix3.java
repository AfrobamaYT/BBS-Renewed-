package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.bobj.BOBJBasisMathe;
import mchorse.bbs_mod.cubic.ik.blender.IKMatN;
import mchorse.bbs_mod.cubic.ik.blender.IKSvd;

import org.joml.Matrix4f;
import static mchorse.bbs_mod.cubic.ik.RigMathe.*;
import static mchorse.bbs_mod.cubic.ik.RigVektor.*;

/**
 * Blenders 3x3-Mathematik auf float[9] (Spalte i = {@code r[i*3 .. i*3+2]}): Produkte,
 * Inverse, Normalisierung, Skalenmatrizen, Orthogonalisierung. Layout wie {@link RigMathe}.
 */
public final class RigMatrix3
{
    private RigMatrix3()
    {}

    public static float determinante(float[] r)
    {
        return r[0] * (r[4] * r[8] - r[5] * r[7])
             - r[3] * (r[1] * r[8] - r[2] * r[7])
             + r[6] * (r[1] * r[5] - r[2] * r[4]);
    }

    /** {@code mul_m3_m3m3(R, A, B)} — R = A x B. */
    public static float[] mul3(float[] a, float[] b)
    {
        float[] r = new float[9];

        for (int c = 0; c < 3; c++)
        {
            for (int z = 0; z < 3; z++)
            {
                float s = 0F;

                for (int t = 0; t < 3; t++)
                {
                    s += a[t * 3 + z] * b[c * 3 + t];
                }

                r[c * 3 + z] = s;
            }
        }

        return r;
    }

    public static float[] mul3v(float[] r, float[] v)
    {
        return new float[] {
            r[0] * v[0] + r[3] * v[1] + r[6] * v[2],
            r[1] * v[0] + r[4] * v[1] + r[7] * v[2],
            r[2] * v[0] + r[5] * v[1] + r[8] * v[2]};
    }

    public static float[] transponiert3(float[] r)
    {
        float[] t = new float[9];

        for (int i = 0; i < 3; i++)
        {
            for (int j = 0; j < 3; j++)
            {
                t[i * 3 + j] = r[j * 3 + i];
            }
        }

        return t;
    }

    public static float[] invertiert3(float[] r)
    {
        float det = determinante(r);

        if (det == 0F)
        {
            return einheit3();
        }

        float[] t = new float[9];

        t[0] = (r[4] * r[8] - r[5] * r[7]) / det;
        t[1] = (r[2] * r[7] - r[1] * r[8]) / det;
        t[2] = (r[1] * r[5] - r[2] * r[4]) / det;
        t[3] = (r[5] * r[6] - r[3] * r[8]) / det;
        t[4] = (r[0] * r[8] - r[2] * r[6]) / det;
        t[5] = (r[2] * r[3] - r[0] * r[5]) / det;
        t[6] = (r[3] * r[7] - r[4] * r[6]) / det;
        t[7] = (r[1] * r[6] - r[0] * r[7]) / det;
        t[8] = (r[0] * r[4] - r[1] * r[3]) / det;

        return t;
    }

    /** {@code normalize_m3} — Spalten auf 1 (keine Orthogonalisierung). */
    public static float[] normalisiere3(float[] r)
    {
        float[] n = r.clone();

        for (int i = 0; i < 3; i++)
        {
            float l = (float) Math.sqrt(n[i * 3] * n[i * 3] + n[i * 3 + 1] * n[i * 3 + 1] + n[i * 3 + 2] * n[i * 3 + 2]);

            if (l != 0F)
            {
                n[i * 3] /= l;
                n[i * 3 + 1] /= l;
                n[i * 3 + 2] /= l;
            }
        }

        return n;
    }

    public static float[] groesse3(float[] r)
    {
        float[] s = new float[3];

        for (int i = 0; i < 3; i++)
        {
            s[i] = (float) Math.sqrt(r[i * 3] * r[i * 3] + r[i * 3 + 1] * r[i * 3 + 1] + r[i * 3 + 2] * r[i * 3 + 2]);
        }

        return s;
    }

    public static float[] skalenMatrix3(float[] s)
    {
        return new float[] {s[0], 0F, 0F, 0F, s[1], 0F, 0F, 0F, s[2]};
    }

    public static float[] skalenMatrix4(float[] s)
    {
        return vier(skalenMatrix3(s));
    }

    /**
     * {@code orthogonalize_m4(R, 1)} — die NICHT-stabile Variante aus {@code mat4_to_dquat}:
     * Gram-Schmidt um die Y-Spalte, Spaltenlaengen bleiben.
     */
    public static void orthogonalisiereUmY(float[] m)
    {
        float[] size = groesse(m);
        float[] r0 = spalte(m, 0);
        float[] r1 = einheitsvektor(spalte(m, 1));
        float[] r2 = spalte(m, 2);

        if (punktprodukt(r1, r0) < 1F)
        {
            r2 = einheitsvektor(kreuz(r0, r1));
            r0 = kreuz(r1, r2);
        }
        else if (punktprodukt(r1, r2) < 1F)
        {
            r0 = einheitsvektor(kreuz(r1, r2));
            r2 = kreuz(r0, r1);
        }
        else
        {
            float[] vec = {r1[1], r1[2], r1[0]};

            r2 = einheitsvektor(kreuz(vec, r1));
            r0 = kreuz(r1, r2);
        }

        setzeSpalte(m, 0, mal(r0, size[0]));
        setzeSpalte(m, 1, mal(r1, size[1]));
        setzeSpalte(m, 2, mal(r2, size[2]));
    }

    /** {@code is_orthonormal_m3} auf die 3x3 einer 4x4. */
    public static boolean orthonormal(float[] m)
    {
        for (int i = 0; i < 3; i++)
        {
            float[] a = spalte(m, i);

            if (Math.abs(punktprodukt(a, a) - 1F) > 1.0E-5F)
            {
                return false;
            }

            for (int j = 0; j < i; j++)
            {
                if (Math.abs(punktprodukt(a, spalte(m, j))) > 1.0E-5F)
                {
                    return false;
                }
            }
        }

        return true;
    }

    /** {@code mat4_to_size_fix_shear}. */
    public static float[] groesseOhneScherung(float[] m)
    {
        float[] s = groesse(m);
        float vol = s[0] * s[1] * s[2];

        if (vol != 0F)
        {
            float f = (float) Math.cbrt(Math.abs(volumen(m) / vol));

            s[0] *= f;
            s[1] *= f;
            s[2] *= f;
        }

        return s;
    }

    public static float[] einheit3()
    {
        return new float[] {1F, 0F, 0F, 0F, 1F, 0F, 0F, 0F, 1F};
    }
}
