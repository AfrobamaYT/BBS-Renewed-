package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.bobj.BOBJBasisMathe;
import mchorse.bbs_mod.cubic.ik.blender.IKMatN;
import mchorse.bbs_mod.cubic.ik.blender.IKSvd;

import org.joml.Matrix4f;
import static mchorse.bbs_mod.cubic.ik.RigMathe.*;
import static mchorse.bbs_mod.cubic.ik.RigMatrix3.*;
import static mchorse.bbs_mod.cubic.ik.RigQuaternion.*;
import static mchorse.bbs_mod.cubic.ik.RigVektor.*;

/**
 * Blenders Zerlegen, Zusammensetzen und Mischen von Matrizen ({@code math_matrix_c.cc},
 * {@code math_rotation_c.cc}): loc/rot/size, Euler-Ordnungen, Polarzerlegung, interp_m4_m4m4,
 * blend_m3_m3m3, aligned-scale und split-channels Produkte. Layout wie {@link RigMathe}.
 */
public final class RigZerlegung
{
    private RigZerlegung()
    {}

    /**
     * {@code mat4_to_loc_rot_size}: {@code rot} (float[9]) bekommt die normalisierten Spalten,
     * {@code size} die Laengen; bei negativer Determinante werden beide negiert.
     */
    public static float[] zerlege(float[] m, float[] rot, float[] size)
    {
        for (int i = 0; i < 3; i++)
        {
            float[] c = spalte(m, i);

            size[i] = laenge(c);

            for (int j = 0; j < 3; j++)
            {
                rot[i * 3 + j] = size[i] == 0F ? 0F : c[j] / size[i];
            }
        }

        if (determinante(rot) < 0F)
        {
            for (int k = 0; k < 9; k++)
            {
                rot[k] = -rot[k];
            }

            for (int k = 0; k < 3; k++)
            {
                size[k] = -size[k];
            }
        }

        return ort(m);
    }

    /** {@code loc_rot_size_to_mat4}. */
    public static float[] zusammen(float[] loc, float[] rot, float[] size)
    {
        float[] m = vier(rot);

        skaliere(m, size);
        setzeOrt(m, loc);

        return m;
    }

    /** {@code loc_eulO_size_to_mat4}. */
    public static float[] zusammenEuler(float[] loc, float[] eul, float[] size, String ordnung)
    {
        return zusammen(loc, RigEuler.zuMatrix(eul, ordnung), size);
    }

    /** {@code mat4_to_eulO} — normalisiert die 3x3, dann Euler in der Ordnung. */
    public static float[] euler(float[] m, String ordnung)
    {
        return RigEuler.ausMatrix(normalisiere3(drei(m)), ordnung);
    }

    /** {@code mat4_to_compatible_eulO}. */
    public static float[] eulerNahe(float[] m, float[] bezug, String ordnung)
    {
        return RigEuler.ausMatrixNahe(normalisiere3(drei(m)), bezug, ordnung);
    }

    /** {@code mat3_polar_decompose} ueber die SVD des IK-Ports: U = W V^T, P = V S V^T. */
    public static void polarzerlegung(float[] r, float[] rU, float[] rP)
    {
        IKMatN a = new IKMatN(3, 3);

        for (int i = 0; i < 3; i++)
        {
            for (int j = 0; j < 3; j++)
            {
                /* IKMatN ist zeilenweise (Zeile i, Spalte j) = mat[j][i] in Blenders Layout. */
                a.set(i, j, r[j * 3 + i]);
            }
        }

        IKSvd svd = new IKSvd(a);
        double[][] w = new double[3][3];
        double[][] v = new double[3][3];

        for (int i = 0; i < 3; i++)
        {
            for (int j = 0; j < 3; j++)
            {
                w[i][j] = svd.u.get(i, j);
                v[i][j] = svd.v.get(i, j);
            }
        }

        for (int i = 0; i < 3; i++)
        {
            for (int j = 0; j < 3; j++)
            {
                double u = 0D;
                double p = 0D;

                for (int k = 0; k < 3; k++)
                {
                    u += w[i][k] * v[j][k];
                    p += v[i][k] * svd.w[k] * v[j][k];
                }

                /* Zeile i, Spalte j -> Blenders mat[j][i]. */
                rU[j * 3 + i] = (float) u;
                rP[j * 3 + i] = (float) p;
            }
        }
    }

    /** {@code interp_m3_m3m3} — Polarzerlegung, Drehung als Slerp, Streckung linear. */
    public static float[] interpoliere3(float[] a, float[] b, float t)
    {
        float[] uA = new float[9];
        float[] pA = new float[9];
        float[] uB = new float[9];
        float[] pB = new float[9];

        polarzerlegung(a, uA, pA);
        polarzerlegung(b, uB, pB);

        if (determinante(uA) < 0F)
        {
            negiere(uA);
            negiere(pA);
        }

        if (determinante(uB) < 0F)
        {
            negiere(uB);
            negiere(pB);
        }

        float[] q = slerp(quaternion3(uA), quaternion3(uB), t);
        float[] u = quaternionZuMatrix(q);
        float[] p = new float[9];

        for (int i = 0; i < 9; i++)
        {
            p[i] = pA[i] + (pB[i] - pA[i]) * t;
        }

        return mul3(u, p);
    }

    /** {@code interp_m4_m4m4} — Blenders Einfluss-Mischung seit 4.2. */
    public static float[] interpoliere(float[] a, float[] b, float t)
    {
        float[] r = vier(interpoliere3(drei(a), drei(b), t));
        float[] oa = ort(a);
        float[] ob = ort(b);

        setzeOrt(r, new float[] {oa[0] + (ob[0] - oa[0]) * t, oa[1] + (ob[1] - oa[1]) * t,
            oa[2] + (ob[2] - oa[2]) * t});

        return r;
    }

    /** {@code blend_m3_m3m3} — Drehung (Slerp) und Spaltenlaengen (linear) getrennt. */
    public static float[] mische3(float[] dst, float[] src, float t)
    {
        float[] drot = new float[9];
        float[] dsize = new float[3];
        float[] srot = new float[9];
        float[] ssize = new float[3];

        zerlege(vier(dst), drot, dsize);
        zerlege(vier(src), srot, ssize);

        float[] q = slerp(quaternion3(drot), quaternion3(srot), t);
        float[] fsize = {dsize[0] + (ssize[0] - dsize[0]) * t, dsize[1] + (ssize[1] - dsize[1]) * t,
            dsize[2] + (ssize[2] - dsize[2]) * t};

        return mul3(quaternionZuMatrix(q), skalenMatrix3(fsize));
    }

    /** {@code mul_m4_m4m4_aligned_scale}. */
    public static float[] mulAusgerichtet(float[] a, float[] b)
    {
        float[] rotA = new float[9];
        float[] sizeA = new float[3];
        float[] rotB = new float[9];
        float[] sizeB = new float[3];

        zerlege(a, rotA, sizeA);
        float[] locB = zerlege(b, rotB, sizeB);

        return zusammen(punkt(a, locB), mul3(rotA, rotB),
            new float[] {sizeA[0] * sizeB[0], sizeA[1] * sizeB[1], sizeA[2] * sizeB[2]});
    }

    /** {@code mul_m4_m4m4_split_channels}. */
    public static float[] mulGetrennt(float[] a, float[] b)
    {
        float[] rotA = new float[9];
        float[] sizeA = new float[3];
        float[] rotB = new float[9];
        float[] sizeB = new float[3];
        float[] locA = zerlege(a, rotA, sizeA);
        float[] locB = zerlege(b, rotB, sizeB);

        return zusammen(new float[] {locA[0] + locB[0], locA[1] + locB[1], locA[2] + locB[2]},
            mul3(rotA, rotB), new float[] {sizeA[0] * sizeB[0], sizeA[1] * sizeB[1], sizeA[2] * sizeB[2]});
    }

    static void negiere(float[] r)
    {
        for (int i = 0; i < r.length; i++)
        {
            r[i] = -r[i];
        }
    }
}
