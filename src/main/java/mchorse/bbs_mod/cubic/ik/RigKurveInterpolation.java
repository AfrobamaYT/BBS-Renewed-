package mchorse.bbs_mod.cubic.ik;

import java.util.List;

/**
 * {@code fcurve_eval_keyframes_interpolate}: der Wert zwischen zwei Stuetzpunkten — Suche
 * ({@code BKE_fcurve_bezt_binarysearch_index_ex}), Griffkorrektur ({@code BKE_fcurve_correct_bezpart}),
 * BEZIER ueber die Nullstelle aus {@link RigKurveKubisch}, sonst LINEAR oder {@link RigEasing}.
 */
final class RigKurveInterpolation
{
    private RigKurveInterpolation()
    {}

    /** {@code BKE_fcurve_bezt_binarysearch_index_ex} mit Schwelle 0.0001. */
    static int suche(List<RigKurve.Punkt> punkte, float frame, boolean[] exakt)
    {
        final float schwelle = 0.0001F;
        int n = punkte.size();

        exakt[0] = false;

        if (Math.abs(punkte.get(0).x - frame) < schwelle)
        {
            exakt[0] = true;

            return 0;
        }

        if (frame < punkte.get(0).x)
        {
            return 0;
        }

        if (Math.abs(punkte.get(n - 1).x - frame) < schwelle)
        {
            exakt[0] = true;

            return n - 1;
        }

        if (frame > punkte.get(n - 1).x)
        {
            return n;
        }

        int start = 0;
        int end = n;

        for (int loop = 0; loop < 1000 && start != end; loop++)
        {
            int mid = start + (end - start) / 2;
            float x = punkte.get(mid).x;

            if (Math.abs(x - frame) < schwelle)
            {
                exakt[0] = true;

                return mid;
            }

            if (x < frame)
            {
                start = mid + 1;
            }
            else
            {
                end = mid;
            }
        }

        return start;
    }

    /** {@code fcurve_eval_keyframes_interpolate}. */
    static float interpoliere(List<RigKurve.Punkt> punkte, float evaltime)
    {
        boolean[] exakt = new boolean[1];
        int a = suche(punkte, evaltime, exakt);
        RigKurve.Punkt bezt = punkte.get(Math.min(a, punkte.size() - 1));

        if (exakt[0])
        {
            return bezt.y;
        }

        RigKurve.Punkt prev = a > 0 ? punkte.get(a - 1) : bezt;

        if (Math.abs(bezt.x - evaltime) < 1.0E-8F)
        {
            return bezt.y;
        }

        if (evaltime < prev.x || bezt.x < evaltime)
        {
            return 0F;
        }

        float begin = prev.y;
        float change = bezt.y - prev.y;
        float duration = bezt.x - prev.x;
        float time = evaltime - prev.x;

        if ("CONSTANT".equals(prev.interp) || duration == 0F)
        {
            return prev.y;
        }

        switch (prev.interp)
        {
            case "BEZIER" ->
            {
                float[] v1 = {prev.x, prev.y};
                float[] v2 = {prev.hrx, prev.hry};
                float[] v3 = {bezt.hlx, bezt.hly};
                float[] v4 = {bezt.x, bezt.y};

                if (Math.abs(v1[1] - v4[1]) < 1.1920929E-7F && Math.abs(v2[1] - v3[1]) < 1.1920929E-7F
                    && Math.abs(v3[1] - v4[1]) < 1.1920929E-7F)
                {
                    return v1[1];
                }

                korrigiereGriffe(v1, v2, v3, v4);

                double[] t = new double[3];
                int n = RigKurveKubisch.nullstellen(evaltime, v1[0], v2[0], v3[0], v4[0], t);

                if (n == 0)
                {
                    return 0F;
                }

                return (float) RigKurveKubisch.bezierWert(v1[1], v2[1], v3[1], v4[1], t[0]);
            }
            case "LINEAR" ->
            {
                return change * time / duration + begin;
            }
            default ->
            {
                return RigEasing.wert(prev.interp, prev.easing, time, begin, change, duration,
                    prev.back, prev.amplitude, prev.period);
            }
        }
    }

    /** {@code BKE_fcurve_correct_bezpart}. */
    private static void korrigiereGriffe(float[] v1, float[] v2, float[] v3, float[] v4)
    {
        float h1x = v1[0] - v2[0], h1y = v1[1] - v2[1];
        float h2x = v4[0] - v3[0], h2y = v4[1] - v3[1];
        float len = v4[0] - v1[0];
        float len1 = Math.abs(h1x);
        float len2 = Math.abs(h2x);

        if (len1 + len2 == 0F)
        {
            return;
        }

        if (len1 > len)
        {
            float fac = len / len1;

            v2[0] = v1[0] - fac * h1x;
            v2[1] = v1[1] - fac * h1y;
        }

        if (len2 > len)
        {
            float fac = len / len2;

            v3[0] = v4[0] - fac * h2x;
            v3[1] = v4[1] - fac * h2y;
        }
    }
}
