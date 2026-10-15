package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.bobj.BOBJArmature;
import mchorse.bbs_mod.bobj.BOBJBBone;
import mchorse.bbs_mod.bobj.BOBJBone;
import static mchorse.bbs_mod.cubic.ik.RigBBone.*;

/**
 * Blenders CURVED-Mapping ({@code compute_bbone_segment_boundaries},
 * {@code find_bbone_segment_index_curved}): Segmentgrenzen als Ebenen und die Segmentwahl je Vertex.
 */
public final class RigBBoneKurve
{
    private RigBBoneKurve()
    {}

    /** {@code compute_bbone_segment_boundaries} fuer das CURVED-Mapping. */
    static void grenzen(RigRahmen rahmen, BOBJBone b)
    {
        BOBJBBone d = b.bbone;
        int n = d.segmente;
        float[] armMat = rahmen.armMat(b);

        d.grenzPunkt = new float[n + 1][];
        d.grenzNormale = new float[n + 1][];
        d.grenzVersatz = new float[n + 1];
        d.grenzTiefe = new float[n + 1];

        for (int i = 0; i <= n; i++)
        {
            d.grenzPunkt[i] = RigMathe.punkt(armMat, RigMathe.ort(d.ruheMatrizen[i]));
            d.grenzNormale[i] = RigVektor.einheitsvektor(RigMathe.richtung(armMat, RigMathe.spalte(d.ruheMatrizen[i], 1)));
            d.grenzVersatz[i] = RigVektor.punktprodukt(d.grenzPunkt[i], d.grenzNormale[i]);
        }

        float arc = 0F;

        for (int i = 0; i < n; i++)
        {
            arc += RigVektor.laenge(RigVektor.differenz(d.grenzPunkt[i + 1], d.grenzPunkt[i]));
        }

        d.bogenLaengeKehrwert = arc == 0F ? 0F : 1F / arc;

        int depth = (int) Math.ceil(Math.log(n + 2) / Math.log(2D));
        float tuning = 0.222F;
        float straight = RigVektor.laenge(RigVektor.differenz(d.grenzPunkt[0], d.grenzPunkt[n]));
        float maxDepthScale = n * (straight / arc) * tuning;
        float scaleFactor = (float) Math.pow(Math.max(maxDepthScale, 1F), 1D / Math.max(1, depth - 1));

        d.grenzTiefe[0] = n / maxDepthScale;

        for (int i = 1; i < depth && i <= n; i++)
        {
            d.grenzTiefe[i] = d.grenzTiefe[i - 1] * scaleFactor;
        }
    }

    /** {@code find_bbone_segment_index_curved}. */
    static void gekruemmt(BOBJBBone d, float[] co, int[] rIndex, float[] rBlend)
    {
        int segments = d.segmente;
        float[] boundaryDist = new float[MAX_SUBDIV + 2];
        int[] stack = new int[MAX_SUBDIV + 1];
        int top = -1;
        int start = -1;
        int end = segments + 1;
        int bias = 0;

        while (end - start > 1)
        {
            int mid = (start + end + bias) / 2;
            float dist = RigVektor.punktprodukt(co, d.grenzNormale[mid]) - d.grenzVersatz[mid];

            stack[++top] = mid;
            boundaryDist[mid] = dist;

            if (dist < 0F)
            {
                end = mid;
                bias = 1;
            }
            else
            {
                start = mid;
                bias = 0;
            }
        }

        float segmentSize = 1F / segments;
        float headTail;

        if (end <= 0)
        {
            headTail = 0F;
        }
        else if (start >= segments)
        {
            headTail = 1F;
        }
        else
        {
            float d1 = Math.abs(boundaryDist[start]);
            float d2 = Math.abs(boundaryDist[end]);
            float t = d1 + d2 == 0F ? 0F : d1 / (d1 + d2);

            headTail = segmentSize * (start + t);
        }

        float segmentScale = d.bogenLaengeKehrwert;

        for (int i = top; i >= 0; i--)
        {
            int idx = stack[i];
            float boundaryPos = idx * segmentSize;
            float pointDist = boundaryDist[idx] * segmentScale;
            float pointDistAbs = Math.abs(pointDist);
            float mappedDist = Math.abs(headTail - boundaryPos);
            float slopeGap = mappedDist - pointDistAbs;

            if (slopeGap <= 0F)
            {
                continue;
            }

            float distCoeff = 1F - pointDistAbs * d.grenzTiefe[i];

            if (distCoeff <= 0F)
            {
                continue;
            }

            float scaledGap = slopeGap * 3F;
            float slopeCoeff = scaledGap / (scaledGap + pointDistAbs);
            float distSmooth = distCoeff * distCoeff * (7F + 1F) / (7F * distCoeff + 1F);
            float target = boundaryPos + pointDist;

            headTail = headTail + (target - headTail) * (slopeCoeff * distSmooth);
        }

        klemmeSegmentIndex(segments, headTail, rIndex, rBlend);
    }
}
