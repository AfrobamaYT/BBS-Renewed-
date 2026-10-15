package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.bobj.BOBJBasisMathe;
import mchorse.bbs_mod.cubic.ik.blender.IKMatN;
import mchorse.bbs_mod.cubic.ik.blender.IKSvd;

import org.joml.Matrix4f;
import static mchorse.bbs_mod.cubic.ik.RigMathe.*;
import static mchorse.bbs_mod.cubic.ik.RigMatrix3.*;
import static mchorse.bbs_mod.cubic.ik.RigZerlegung.*;

/**
 * Blenders Quaternionen (w, x, y, z) aus {@code math_rotation_c.cc}: aus Matrizen (mit
 * Blenders Pruefungen), in Matrizen, Produkt, Konjugation, Slerp.
 */
public final class RigQuaternion
{
    private RigQuaternion()
    {}

    /** {@code mat3_normalized_to_quat_with_checks} + {@code _fast} (Mike Day). */
    public static float[] quaternion3(float[] eingabe)
    {
        float[] m = eingabe.clone();
        float det = determinante(m);

        if (Float.isNaN(det) || Float.isInfinite(det))
        {
            m = einheit3();
        }
        else if (det < 0F)
        {
            negiere(m);
        }

        float[] q = new float[4];

        if (m[8] < 0F)
        {
            if (m[0] > m[4])
            {
                float trace = 1F + m[0] - m[4] - m[8];
                float s = 2F * (float) Math.sqrt(trace);

                if (m[5] < m[7])
                {
                    s = -s;
                }

                q[1] = 0.25F * s;
                s = 1F / s;
                q[0] = (m[5] - m[7]) * s;
                q[2] = (m[1] + m[3]) * s;
                q[3] = (m[6] + m[2]) * s;

                if (trace == 1F && q[0] == 0F && q[2] == 0F && q[3] == 0F)
                {
                    q[1] = 1F;
                }
            }
            else
            {
                float trace = 1F - m[0] + m[4] - m[8];
                float s = 2F * (float) Math.sqrt(trace);

                if (m[6] < m[2])
                {
                    s = -s;
                }

                q[2] = 0.25F * s;
                s = 1F / s;
                q[0] = (m[6] - m[2]) * s;
                q[1] = (m[1] + m[3]) * s;
                q[3] = (m[5] + m[7]) * s;

                if (trace == 1F && q[0] == 0F && q[1] == 0F && q[3] == 0F)
                {
                    q[2] = 1F;
                }
            }
        }
        else
        {
            if (m[0] < -m[4])
            {
                float trace = 1F - m[0] - m[4] + m[8];
                float s = 2F * (float) Math.sqrt(trace);

                if (m[1] < m[3])
                {
                    s = -s;
                }

                q[3] = 0.25F * s;
                s = 1F / s;
                q[0] = (m[1] - m[3]) * s;
                q[1] = (m[6] + m[2]) * s;
                q[2] = (m[5] + m[7]) * s;

                if (trace == 1F && q[0] == 0F && q[1] == 0F && q[2] == 0F)
                {
                    q[3] = 1F;
                }
            }
            else
            {
                float trace = 1F + m[0] + m[4] + m[8];
                float s = 2F * (float) Math.sqrt(trace);

                q[0] = 0.25F * s;
                s = 1F / s;
                q[1] = (m[5] - m[7]) * s;
                q[2] = (m[6] - m[2]) * s;
                q[3] = (m[1] - m[3]) * s;

                if (trace == 1F && q[1] == 0F && q[2] == 0F && q[3] == 0F)
                {
                    q[0] = 1F;
                }
            }
        }

        return q;
    }

    /** {@code mat4_to_quat} — normalisieren, dann Quaternion. */
    public static float[] quaternion(float[] m)
    {
        return quaternion3(normalisiere3(drei(m)));
    }

    /** {@code quat_to_mat3}. */
    public static float[] quaternionZuMatrix(float[] q)
    {
        double q0 = Math.sqrt(2D) * q[0];
        double q1 = Math.sqrt(2D) * q[1];
        double q2 = Math.sqrt(2D) * q[2];
        double q3 = Math.sqrt(2D) * q[3];
        double qda = q0 * q1, qdb = q0 * q2, qdc = q0 * q3;
        double qaa = q1 * q1, qab = q1 * q2, qac = q1 * q3;
        double qbb = q2 * q2, qbc = q2 * q3, qcc = q3 * q3;
        float[] m = new float[9];

        m[0] = (float) (1D - qbb - qcc);
        m[1] = (float) (qdc + qab);
        m[2] = (float) (-qdb + qac);
        m[3] = (float) (-qdc + qab);
        m[4] = (float) (1D - qaa - qcc);
        m[5] = (float) (qda + qbc);
        m[6] = (float) (qdb + qac);
        m[7] = (float) (-qda + qbc);
        m[8] = (float) (1D - qaa - qbb);

        return m;
    }

    public static float[] quaternionZuMatrix4(float[] q)
    {
        return vier(quaternionZuMatrix(q));
    }

    public static float punktprodukt4(float[] a, float[] b)
    {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3];
    }

    /** {@code mul_qt_qtqt}. */
    public static float[] mulQuat(float[] a, float[] b)
    {
        return new float[] {
            a[0] * b[0] - a[1] * b[1] - a[2] * b[2] - a[3] * b[3],
            a[0] * b[1] + a[1] * b[0] + a[2] * b[3] - a[3] * b[2],
            a[0] * b[2] + a[2] * b[0] + a[3] * b[1] - a[1] * b[3],
            a[0] * b[3] + a[3] * b[0] + a[1] * b[2] - a[2] * b[1]};
    }

    /** {@code invert_qt_normalized} — Konjugierte. */
    public static float[] konjugiert(float[] q)
    {
        return new float[] {q[0], -q[1], -q[2], -q[3]};
    }

    public static float[] normalisiereQuat(float[] q)
    {
        float l = (float) Math.sqrt(punktprodukt4(q, q));

        return l == 0F ? new float[] {1F, 0F, 0F, 0F} : new float[] {q[0] / l, q[1] / l, q[2] / l, q[3] / l};
    }

    /** {@code interp_qt_qtqt} samt {@code interp_dot_slerp}. */
    public static float[] slerp(float[] a, float[] b, float t)
    {
        float cosom = punktprodukt4(a, b);
        float[] quat = a.clone();

        if (cosom < 0F)
        {
            cosom = -cosom;

            for (int i = 0; i < 4; i++)
            {
                quat[i] = -quat[i];
            }
        }

        float w0;
        float w1;

        if (Math.abs(cosom) < 1F - 1.0E-4F)
        {
            float omega = (float) Math.acos(cosom);
            float sinom = (float) Math.sin(omega);

            w0 = (float) Math.sin((1F - t) * omega) / sinom;
            w1 = (float) Math.sin(t * omega) / sinom;
        }
        else
        {
            w0 = 1F - t;
            w1 = t;
        }

        return new float[] {w0 * quat[0] + w1 * b[0], w0 * quat[1] + w1 * b[1],
            w0 * quat[2] + w1 * b[2], w0 * quat[3] + w1 * b[3]};
    }
}
