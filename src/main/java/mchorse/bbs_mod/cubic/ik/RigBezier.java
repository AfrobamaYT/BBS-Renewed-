package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.bobj.BOBJBasisMathe;
import mchorse.bbs_mod.cubic.ik.blender.IKMatN;
import mchorse.bbs_mod.cubic.ik.blender.IKSvd;

import org.joml.Matrix4f;

/**
 * Blenders kubische Bezier-Helfer ({@code curve.cc}, {@code armature.cc}): Vorwaertsdifferenz,
 * Punkt samt Tangente, gleichmaessige Unterteilung nach Bogenlaenge.
 */
public final class RigBezier
{
    private RigBezier()
    {}

    /** {@code BKE_curve_forward_diff_bezier} fuer EINE Komponente, {@code it + 1} Werte. */
    public static void vorwaertsDifferenzBezier(float q0, float q1, float q2, float q3, float[] raus, int it)
    {
        float f = it;
        float rt0 = q0;
        float rt1 = 3F * (q1 - q0) / f;

        f *= f;

        float rt2 = 3F * (q0 - 2F * q1 + q2) / f;

        f *= it;

        float rt3 = (q3 - q0 + 3F * (q1 - q2)) / f;

        q0 = rt0;
        q1 = rt1 + rt2 + rt3;
        q2 = 2F * rt2 + 6F * rt3;
        q3 = 6F * rt3;

        for (int a = 0; a <= it; a++)
        {
            raus[a] = q0;
            q0 += q1;
            q1 += q2;
            q2 += q3;
        }
    }

    /** {@code evaluate_cubic_bezier}: Ort und Tangente bei t (De Casteljau). */
    static void bezierPunkt(float[][] control, float t, float[] pos, float[] tangent)
    {
        float[][] l1 = new float[3][];
        float[][] l2 = new float[2][];

        for (int i = 0; i < 3; i++)
        {
            l1[i] = RigVektor.mischeV(control[i], control[i + 1], t);
        }

        for (int i = 0; i < 2; i++)
        {
            l2[i] = RigVektor.mischeV(l1[i], l1[i + 1], t);
        }

        float[] tan = RigVektor.differenz(l2[1], l2[0]);

        System.arraycopy(tan, 0, tangent, 0, 3);

        for (int i = 0; i < 3; i++)
        {
            pos[i] = l2[0][i] + tan[i] * t;
        }
    }

    /** {@code equalize_cubic_bezier}. */
    static float[] gleichmaessig(float[][] control, int tempSegments, int finalSegments, float[] segmentScales)
    {
        float[][] coords = new float[tempSegments + 1][3];
        float[] tmp = new float[tempSegments + 1];

        for (int i = 0; i < 3; i++)
        {
            RigBezier.vorwaertsDifferenzBezier(control[0][i], control[1][i], control[2][i], control[3][i], tmp, tempSegments);

            for (int k = 0; k <= tempSegments; k++)
            {
                coords[k][i] = tmp[k];
            }
        }

        float[] pdist = new float[tempSegments + 1];

        for (int i = 0; i < tempSegments; i++)
        {
            pdist[i + 1] = pdist[i] + RigVektor.laenge(RigVektor.differenz(coords[i], coords[i + 1]));
        }

        float distStep = pdist[tempSegments];
        float dist = 0F;
        float sum = 0F;

        for (int i = 0; i < finalSegments; i++)
        {
            sum += segmentScales[i];
        }

        distStep /= sum;

        float[] tPoints = new float[finalSegments + 1];

        for (int i = 1, nr = 1; i <= finalSegments; i++)
        {
            dist += segmentScales[i - 1] * distStep;

            while (nr < tempSegments && dist >= pdist[nr])
            {
                nr++;
            }

            float fac = (pdist[nr] - dist) / (pdist[nr] - pdist[nr - 1]);

            tPoints[i] = (nr - fac) / tempSegments;
        }

        tPoints[finalSegments] = 1F;

        return tPoints;
    }
}
