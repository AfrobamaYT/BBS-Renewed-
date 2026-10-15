package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.bobj.BOBJBasisMathe;
import mchorse.bbs_mod.cubic.ik.blender.IKMatN;
import mchorse.bbs_mod.cubic.ik.blender.IKSvd;

import org.joml.Matrix4f;
import static mchorse.bbs_mod.cubic.ik.RigVektor.*;

/**
 * Blenders Matrix- und Quaternion-Mathematik aus {@code blenlib/intern/math_matrix_c.cc} und
 * {@code math_rotation_c.cc}, wortgetreu portiert — die Constraint-Laufzeit rechnet damit so,
 * wie Blender rechnet, nicht "gleichwertig".
 *
 * <p>⛔ Anordnung ist Blenders: {@code m[i * 4 + j]} ist {@code mat[i][j]}, Komponente j der
 * Basis-SPALTE i; 3x3 entsprechend {@code m[i * 3 + j]}. JOMLs {@code Matrix4f.get(float[])}
 * liefert genau dieses Layout. Quaternionen sind {@code (w, x, y, z)} wie in Blender.
 * {@code mul(r, a, b)} ist Blenders {@code mul_m4_m4m4(R, A, B)}: erst B, dann A.
 */

public final class RigMathe
{
    private RigMathe()
    {}

    public static float[] einheit()
    {
        float[] m = new float[16];

        m[0] = m[5] = m[10] = m[15] = 1F;

        return m;
    }

    public static float[] kopie(float[] a)
    {
        return a.clone();
    }

    public static float[] von(Matrix4f m)
    {
        return m.get(new float[16]);
    }

    public static Matrix4f zuJoml(float[] m)
    {
        return new Matrix4f().set(m);
    }

    /** {@code mul_m4_m4m4(R, A, B)} — R = A x B. */
    public static float[] mul(float[] a, float[] b)
    {
        float[] r = new float[16];

        for (int c = 0; c < 4; c++)
        {
            for (int z = 0; z < 4; z++)
            {
                float s = 0F;

                for (int t = 0; t < 4; t++)
                {
                    s += a[t * 4 + z] * b[c * 4 + t];
                }

                r[c * 4 + z] = s;
            }
        }

        return r;
    }

    public static float[] invertiert(float[] a)
    {
        Matrix4f m = new Matrix4f().set(a);

        if (Math.abs(m.determinant()) < 1.0E-20F)
        {
            return einheit();
        }

        return m.invert().get(new float[16]);
    }

    /** {@code mul_v3_m4v3} — Punkt transformieren. */
    public static float[] punkt(float[] m, float[] v)
    {
        return new float[] {
            m[0] * v[0] + m[4] * v[1] + m[8] * v[2] + m[12],
            m[1] * v[0] + m[5] * v[1] + m[9] * v[2] + m[13],
            m[2] * v[0] + m[6] * v[1] + m[10] * v[2] + m[14]};
    }

    /** {@code mul_mat3_m4_v3} — Richtung transformieren (ohne Ort). */
    public static float[] richtung(float[] m, float[] v)
    {
        return new float[] {
            m[0] * v[0] + m[4] * v[1] + m[8] * v[2],
            m[1] * v[0] + m[5] * v[1] + m[9] * v[2],
            m[2] * v[0] + m[6] * v[1] + m[10] * v[2]};
    }

    public static float[] ort(float[] m)
    {
        return new float[] {m[12], m[13], m[14]};
    }

    public static void setzeOrt(float[] m, float[] o)
    {
        m[12] = o[0];
        m[13] = o[1];
        m[14] = o[2];
    }

    public static float[] spalte(float[] m, int i)
    {
        return new float[] {m[i * 4], m[i * 4 + 1], m[i * 4 + 2]};
    }

    public static void setzeSpalte(float[] m, int i, float[] v)
    {
        m[i * 4] = v[0];
        m[i * 4 + 1] = v[1];
        m[i * 4 + 2] = v[2];
    }

    /** {@code copy_m3_m4} in Blenders 3x3-Layout. */
    public static float[] drei(float[] m)
    {
        float[] r = new float[9];

        for (int i = 0; i < 3; i++)
        {
            System.arraycopy(m, i * 4, r, i * 3, 3);
        }

        return r;
    }

    /** {@code copy_m4_m3} — 3x3 in eine 4x4 mit Ort 0. */
    public static float[] vier(float[] r3)
    {
        float[] m = einheit();

        for (int i = 0; i < 3; i++)
        {
            System.arraycopy(r3, i * 3, m, i * 4, 3);
        }

        return m;
    }

    /**
     * {@code mul_m4_m3m4(R, M3, M4)}: nur der 3x3-Block wird M3 x M4, die Ortsspalte bleibt
     * UNVERAENDERT. ⛔ Bis 2026-09-13 drehte der Port den Ort mit — Track To und Locked Track
     * verschoben den Knochen um bis zu 3,75 (Testrig).
     */
    public static float[] mul3x4(float[] r3, float[] m)
    {
        float[] r = mul(vier(r3), m);

        setzeOrt(r, ort(m));

        return r;
    }

    /** {@code mat4_to_size} — Spaltenlaengen. */
    public static float[] groesse(float[] m)
    {
        return new float[] {laenge(spalte(m, 0)), laenge(spalte(m, 1)), laenge(spalte(m, 2))};
    }

    /** {@code normalize_m4_ex} — Spalten auf 1, Laengen zurueck. */
    public static float[] normalisiere(float[] m)
    {
        float[] skala = new float[3];

        for (int i = 0; i < 3; i++)
        {
            float l = laenge(spalte(m, i));

            skala[i] = l;

            if (l != 0F)
            {
                m[i * 4] /= l;
                m[i * 4 + 1] /= l;
                m[i * 4 + 2] /= l;
                m[i * 4 + 3] /= l;
            }
        }

        return skala;
    }

    /** {@code rescale_m4}. */
    public static void skaliere(float[] m, float[] s)
    {
        for (int i = 0; i < 3; i++)
        {
            m[i * 4] *= s[i];
            m[i * 4 + 1] *= s[i];
            m[i * 4 + 2] *= s[i];
        }
    }

    /** {@code mat4_to_volume_scale}. */
    public static float volumen(float[] m)
    {
        return BOBJBasisMathe.determinante3(m);
    }

    public static boolean negativ(float[] m)
    {
        return volumen(m) < 0F;
    }

}

