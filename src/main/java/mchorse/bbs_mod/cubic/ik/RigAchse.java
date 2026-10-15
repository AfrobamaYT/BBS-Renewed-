package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.bobj.BOBJBasisMathe;
import mchorse.bbs_mod.cubic.ik.blender.IKMatN;
import mchorse.bbs_mod.cubic.ik.blender.IKSvd;

import org.joml.Matrix4f;
import static mchorse.bbs_mod.cubic.ik.RigMatrix3.*;
import static mchorse.bbs_mod.cubic.ik.RigQuaternion.*;
import static mchorse.bbs_mod.cubic.ik.RigVektor.*;

/**
 * Blenders Achse/Winkel, Swing/Twist und Knochen-Roll ({@code math_rotation_c.cc},
 * {@code armature.cc}): quat_to_axis_angle, axis_angle_normalized_to_mat3, quat_split_swing_and_twist,
 * vec_roll_to_mat3, mat3_vec_to_roll.
 */
public final class RigAchse
{
    private RigAchse()
    {}

    /** {@code quat_to_axis_angle}: Achse (3) und Winkel. */
    public static float[] achseWinkel(float[] q)
    {
        float ha = (float) Math.acos(Math.max(-1F, Math.min(1F, q[0])));
        float si = (float) Math.sin(ha);
        float[] r = new float[4];

        r[3] = ha * 2F;

        if (Math.abs(si) < 0.0005F)
        {
            si = 1F;
        }

        r[0] = q[1] / si;
        r[1] = q[2] / si;
        r[2] = q[3] / si;

        if (r[0] == 0F && r[1] == 0F && r[2] == 0F)
        {
            r[1] = 1F;
        }

        return r;
    }

    /** {@code axis_angle_normalized_to_mat3}. */
    public static float[] achseWinkelZuMatrix(float[] achse, float winkel)
    {
        float si = (float) Math.sin(winkel);
        float co = (float) Math.cos(winkel);
        float ico = 1F - co;
        float[] n = {achse[0] * si, achse[1] * si, achse[2] * si};
        float n00 = achse[0] * achse[0] * ico, n01 = achse[0] * achse[1] * ico, n11 = achse[1] * achse[1] * ico;
        float n02 = achse[0] * achse[2] * ico, n12 = achse[1] * achse[2] * ico, n22 = achse[2] * achse[2] * ico;
        float[] m = new float[9];

        m[0] = n00 + co;
        m[1] = n01 + n[2];
        m[2] = n02 - n[1];
        m[3] = n01 - n[2];
        m[4] = n11 + co;
        m[5] = n12 + n[0];
        m[6] = n02 + n[1];
        m[7] = n12 - n[0];
        m[8] = n22 + co;

        return m;
    }

    /** {@code quat_split_swing_and_twist} — nur der Twist-Winkel um {@code achse}. */
    public static float twist(float[] qIn, int achse)
    {
        float[] q = qIn[0] < 0F ? new float[] {-qIn[0], -qIn[1], -qIn[2], -qIn[3]} : qIn;

        return 2F * (float) Math.atan2(q[achse + 1], q[0]);
    }

    /** {@code quat_split_swing_and_twist} — Swing-Anteil (Quaternion) ohne den Twist. */
    public static float[] swing(float[] qIn, int achse)
    {
        float[] q = qIn[0] < 0F ? new float[] {-qIn[0], -qIn[1], -qIn[2], -qIn[3]} : qIn;
        float t = (float) Math.atan2(q[achse + 1], q[0]);
        float[] twistInv = new float[4];

        twistInv[0] = (float) Math.cos(t);
        twistInv[achse + 1] = -(float) Math.sin(t);

        return mulQuat(q, twistInv);
    }

    /** {@code quaternion_to_angles} — Pseudo-Winkel fuer den Transformation-Constraint. */
    public static void quaternionZuWinkeln(float[] q, int kanal)
    {
        if (kanal < 0)
        {
            q[0] = 2F * sicherAcos(q[0]);

            for (int i = 1; i < 4; i++)
            {
                q[i] = 2F * sicherAsin(q[i]);
            }
        }
        else if (kanal == 0)
        {
            q[0] = 2F * sicherAcos(q[0]);
        }
        else
        {
            q[kanal] = 2F * sicherAsin(q[kanal]);
        }
    }

    public static float sicherAcos(float v)
    {
        return (float) Math.acos(Math.max(-1F, Math.min(1F, v)));
    }

    public static float sicherAsin(float v)
    {
        return (float) Math.asin(Math.max(-1F, Math.min(1F, v)));
    }

    /** {@code vec_roll_to_mat3_normalized}. */
    public static float[] vektorRollZuMatrix(float[] nor, float roll)
    {
        final float SAFE = 6.1E-3F;
        final float CRIT = 2.5E-4F * 2.5E-4F;
        float x = nor[0], y = nor[1], z = nor[2];
        float theta = 1F + y;
        float thetaAlt = x * x + z * z;
        float[] b = new float[9];

        if (theta > SAFE || thetaAlt > CRIT)
        {
            b[1] = -x;
            b[3] = x;
            b[4] = y;
            b[5] = z;
            b[7] = -z;

            if (theta <= SAFE)
            {
                theta = thetaAlt * 0.5F + thetaAlt * thetaAlt * 0.125F;
            }

            b[0] = 1F - x * x / theta;
            b[8] = 1F - z * z / theta;
            b[6] = b[2] = -x * z / theta;
        }
        else
        {
            b = einheit3();
            b[0] = b[4] = -1F;
        }

        return mul3(achseWinkelZuMatrix(nor, roll), b);
    }

    /** {@code vec_roll_to_mat3}. */
    public static float[] richtungRollZuMatrix(float[] vec, float roll)
    {
        return vektorRollZuMatrix(einheitsvektor(vec), roll);
    }

    /** {@code mat3_vec_to_roll}. */
    public static float rollAusMatrix(float[] mat, float[] vec)
    {
        float[] vecmat = richtungRollZuMatrix(vec, 0F);
        float[] rollmat = mul3(invertiert3(vecmat), mat);

        return twist(quaternion3(normalisiere3(rollmat)), 1);
    }
}
