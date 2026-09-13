package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.bobj.BOBJArmature;
import mchorse.bbs_mod.bobj.BOBJBBone;
import mchorse.bbs_mod.bobj.BOBJBone;
import static mchorse.bbs_mod.cubic.ik.RigBBoneGriffe.*;
import static mchorse.bbs_mod.cubic.ik.RigBBoneKurve.*;
import static mchorse.bbs_mod.cubic.ik.RigBBoneSpline.*;

/**
 * Blenders Bendy Bones — {@code BKE_pchan_bbone_*} aus {@code blenkernel/intern/armature.cc}
 * (v5.2.1), wortgetreu: Griffe, Spline-Parameter, Segment-Matrizen, Verform-Matrizen und die
 * Segmentwahl je Vertex (STRAIGHT und CURVED). Alles im Blender-POSE-Raum ueber
 * {@link RigRahmen}; die Verform-Matrizen werden zusaetzlich in den BBS-Raum gespiegelt.
 *
 * <p>⛔ Die Segmente werden NACH der ganzen Pose gerechnet (Blender: {@code BONE_SEGMENTS}
 * haengt am BONE_DONE des Knochens UND seiner Griffe): die Griffe sind fremde Knochen, die
 * ihre Endlage haben muessen.
 */
public final class RigBBone
{
    public static final int MAX_SUBDIV = 32;

    private RigBBone()
    {}

    /** {@code pchan->child}: das letzte verbundene Kind in Knochenreihenfolge. */
    public static void kinderZuweisen(BOBJArmature armature)
    {
        for (BOBJBone b : armature.orderedBones)
        {
            b.verbundenesKind = null;
        }

        for (BOBJBone b : armature.orderedBones)
        {
            if (b.verbunden && b.parentBone != null)
            {
                b.parentBone.verbundenesKind = b;
            }
        }
    }

    /** {@code BKE_pchan_bbone_segments_cache_compute} — Pose-, Ruhe- und Verform-Matrizen. */
    public static void cacheBerechnen(RigRahmen rahmen, BOBJBone b)
    {
        BOBJBBone d = b.bbone;

        if (d == null || d.segmente <= 1)
        {
            return;
        }

        d.poseMatrizen = spline(params(rahmen, b, false), true);
        d.ruheMatrizen = spline(params(rahmen, b, true), true);

        int n = d.segmente;

        if ("CURVED".equals(d.mapping))
        {
            grenzen(rahmen, b);
        }
        else
        {
            d.grenzPunkt = null;
        }

        float[] armMat = rahmen.armMat(b);
        float[] poseMat = rahmen.poseMat(b);
        float[] armInv = RigMathe.invertiert(armMat);
        float[] chanMat = RigMathe.mul(poseMat, armInv);

        d.verformMatrizen = new float[n + 2][];
        d.verformBBS = new float[n + 2][];
        d.verformJoml = new org.joml.Matrix4f[n + 2];
        d.dualQuats = new Object[n + 1];
        d.verformMatrizen[0] = armInv;

        for (int a = 0; a <= n; a++)
        {
            float[] tmat = RigMathe.invertiert(d.ruheMatrizen[a]);
            float[] m = RigMathe.mul(RigMathe.mul(RigMathe.mul(RigMathe.mul(chanMat, armMat), d.poseMatrizen[a]), tmat), armInv);

            d.verformMatrizen[a + 1] = m;
            /* Pose-Raum -> BBS-Raum fuer eine ABBILDUNG: GW · M · inv(GW). */
            d.verformBBS[a + 1] = rahmen.ausPose(RigMathe.mul(m, rahmen.zuPose(RigMathe.einheit())));
            d.verformJoml[a + 1] = RigMathe.zuJoml(d.verformBBS[a + 1]);

            float[] basis = RigMathe.mul(armMat, d.ruheMatrizen[a]);

            RigMathe.normalisiere(basis);
            d.dualQuats[a] = RigDualQuat.aus(basis, m);
        }
    }

    /** {@code BKE_pchan_bbone_deform_clamp_segment_index}. */
    public static void klemmeSegmentIndex(int segments, float headTail, int[] rIndex, float[] rBlend)
    {
        headTail = Math.max(0F, Math.min(1F, headTail));

        float pre = headTail * segments;
        int index = (int) Math.floor(pre);

        index = Math.max(0, Math.min(segments - 1, index));

        float blend = Math.max(0F, Math.min(1F, pre - index));

        rIndex[0] = index;
        rBlend[0] = blend;
    }

    /** {@code BKE_pchan_bbone_deform_segment_index} — {@code co} im Blender-Pose-Raum. */
    public static void segmentIndex(BOBJBone b, float[] co, int[] rIndex, float[] rBlend)
    {
        BOBJBBone d = b.bbone;

        if (d.grenzPunkt != null)
        {
            gekruemmt(d, co, rIndex, rBlend);

            return;
        }

        float[] m = d.verformMatrizen[0];
        float y = m[1] * co[0] + m[5] * co[1] + m[9] * co[2] + m[13];

        klemmeSegmentIndex(d.segmente, y / b.restLength, rIndex, rBlend);
    }
}

