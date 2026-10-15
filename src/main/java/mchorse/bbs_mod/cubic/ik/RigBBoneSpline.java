package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.bobj.BOBJArmature;
import mchorse.bbs_mod.bobj.BOBJBBone;
import mchorse.bbs_mod.bobj.BOBJBone;
import static mchorse.bbs_mod.cubic.ik.RigBBone.*;
import static mchorse.bbs_mod.cubic.ik.RigBBoneGriffweite.*;
import static mchorse.bbs_mod.cubic.ik.RigBBoneParam.*;
import static mchorse.bbs_mod.cubic.ik.RigBezier.*;

/**
 * Blenders {@code BKE_pchan_bbone_spline_compute}: die Segmentmatrizen entlang der
 * Bezier-Kurve (Verform-Variante mit N+1 Stuetzpunkten, Anzeige-Variante mit N Segmenten).
 */
public final class RigBBoneSpline
{
    private RigBBoneSpline()
    {}

    /** {@code make_bbone_spline_matrix}. */
    static float[] splineMatrix(Param p, float[] scaleInv, float[] scaleMat, float[] pos, float[] axis,
                                        float roll, float scalex, float scalez)
    {
        float[] result = RigMathe.vier(RigAchse.richtungRollZuMatrix(axis, roll));

        RigMathe.setzeOrt(result, pos);

        if (p.doScale)
        {
            result = RigMathe.mul(RigMathe.mul(scaleInv, result), scaleMat);
        }

        for (int k = 0; k < 3; k++)
        {
            result[k] *= scalex;
            result[8 + k] *= scalez;
        }

        return result;
    }

    /** {@code ease_handle_axis}. */
    static float[] easeAchse(float[] deriv1, float[] deriv2)
    {
        final float gap = 0.1F;
        float[] axis = deriv1.clone();
        float len2 = RigVektor.punktprodukt(deriv2, deriv2);

        if (len2 == 0F)
        {
            return axis;
        }

        float len1 = RigVektor.punktprodukt(deriv1, deriv1);
        float ratio = len1 / len2;

        if (ratio < gap * gap)
        {
            float f = gap - (float) Math.sqrt(ratio);

            for (int i = 0; i < 3; i++)
            {
                axis[i] += deriv2[i] * f;
            }
        }

        return axis;
    }

    /** {@code BKE_pchan_bbone_spline_compute} (for_deform: Stuetzpunkte, sonst Segmente). */
    static float[][] spline(Param p, boolean forDeform)
    {
        float[] scaleMat = RigMatrix3.skalenMatrix4(p.scale);
        float[] scaleInv = RigMathe.invertiert(scaleMat);
        float length = p.length;

        if (p.doScale)
        {
            length *= p.scale[1];
        }

        float[] h1 = new float[3];
        float[] h2 = new float[3];
        float[] rolls = griffeBerechnen(p, h1, h2);

        p.segments = Math.min(p.segments, MAX_SUBDIV);

        float[][] control = new float[4][];

        control[3] = new float[] {0F, length, 0F};
        control[2] = RigVektor.summe(control[3], h2);
        control[1] = h1.clone();
        control[0] = new float[3];

        p.scaleIn[1] = Math.max(p.scaleIn[1], 0.0001F);
        p.scaleOut[1] = Math.max(p.scaleOut[1], 0.0001F);

        float logIn = (float) Math.log(p.scaleIn[1]);
        float logOut = (float) Math.log(p.scaleOut[1]);
        float[] segmentScales = new float[p.segments];

        for (int i = 0; i < p.segments; i++)
        {
            float fac = p.segments > 1 ? (float) i / (p.segments - 1) : 0F;

            segmentScales[i] = (float) Math.exp(logIn + (logOut - logIn) * fac);
        }

        float[] points = gleichmaessig(control, MAX_SUBDIV, p.segments, segmentScales);
        float[][] result;
        float[] cur = new float[3];
        float[] axis = new float[3];

        if (forDeform)
        {
            result = new float[p.segments + 1][];

            float[][] d1 = new float[3][];
            float[][] d2 = new float[2][];

            for (int i = 0; i < 3; i++)
            {
                d1[i] = RigVektor.differenz(control[i + 1], control[i]);
            }

            for (int i = 0; i < 2; i++)
            {
                d2[i] = RigVektor.differenz(d1[i + 1], d1[i]);
            }

            result[0] = splineMatrix(p, scaleInv, scaleMat, control[0], easeAchse(d1[0], d2[0]), rolls[0],
                p.scaleIn[0], p.scaleIn[2]);

            for (int a = 1; a < p.segments; a++)
            {
                bezierPunkt(control, points[a], cur, axis);

                float fac = (float) a / p.segments;
                /* interpf(roll2, roll1, fac) = roll1 + (roll2 - roll1) * fac — Blenders Argumentfolge. */
                float roll = rolls[0] + (rolls[1] - rolls[0]) * fac;
                float scalex = p.scaleIn[0] + (p.scaleOut[0] - p.scaleIn[0]) * fac;
                float scalez = p.scaleIn[2] + (p.scaleOut[2] - p.scaleIn[2]) * fac;

                result[a] = splineMatrix(p, scaleInv, scaleMat, cur.clone(), axis.clone(), roll, scalex, scalez);
            }

            float[] d2n = RigVektor.mal(d2[1], -1F);

            result[p.segments] = splineMatrix(p, scaleInv, scaleMat, control[3], easeAchse(d1[2], d2n), rolls[1],
                p.scaleOut[0], p.scaleOut[2]);
        }
        else
        {
            result = new float[p.segments][];

            float[] prev = new float[3];

            for (int a = 0; a < p.segments; a++)
            {
                bezierPunkt(control, points[a + 1], cur, axis);

                float[] ax = RigVektor.differenz(cur, prev);
                float fac = (a + 0.5F) / p.segments;
                float roll = rolls[0] + (rolls[1] - rolls[0]) * fac;
                float scalex = p.scaleIn[0] + (p.scaleOut[0] - p.scaleIn[0]) * fac;
                float scalez = p.scaleIn[2] + (p.scaleOut[2] - p.scaleIn[2]) * fac;

                result[a] = splineMatrix(p, scaleInv, scaleMat, prev.clone(), ax, roll, scalex, scalez);
                prev = cur.clone();
            }
        }

        return result;
    }
}
