package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.bobj.BOBJArmature;
import mchorse.bbs_mod.bobj.BOBJBBone;
import mchorse.bbs_mod.bobj.BOBJBone;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
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
    static volatile boolean ENABLE_REST_CACHE = true;
    private static final Map<BOBJBBone, LinkedHashMap<Long, RestState>> REST = new WeakHashMap<>();

    /** Rest spline, segment boundaries and inverse rest matrix are independent of pose. */
    private record RestState(float[][] matrices, float[][] grenzPunkt, float[][] grenzNormale,
        float[] grenzVersatz, float[] grenzTiefe, float bogenLaengeKehrwert,
        float[] armMat, float[] armInv)
    {
        private void restore(BOBJBBone d)
        {
            d.ruheMatrizen = this.matrices;
            d.grenzPunkt = this.grenzPunkt;
            d.grenzNormale = this.grenzNormale;
            d.grenzVersatz = this.grenzVersatz;
            d.grenzTiefe = this.grenzTiefe;
            d.bogenLaengeKehrwert = this.bogenLaengeKehrwert;
        }
    }

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

        long restKey = ENABLE_REST_CACHE ? restSignature(rahmen, b, d) : 0L;
        LinkedHashMap<Long, RestState> entries = ENABLE_REST_CACHE ? REST.get(d) : null;
        RestState cached = entries == null ? null : entries.get(restKey);
        d.poseMatrizen = spline(params(rahmen, b, false), true);

        int n = d.segmente;
        float[] armMat;
        float[] armInv;

        if (cached != null)
        {
            cached.restore(d);
            armMat = cached.armMat;
            armInv = cached.armInv;
        }
        else
        {
            d.ruheMatrizen = spline(params(rahmen, b, true), true);

            if ("CURVED".equals(d.mapping))
            {
                grenzen(rahmen, b);
            }
            else
            {
                d.grenzPunkt = null;
            }

            armMat = rahmen.armMat(b);
            armInv = RigMathe.invertiert(armMat);

            if (ENABLE_REST_CACHE)
            {
                if (entries == null)
                {
                    entries = new LinkedHashMap<>(8, 0.75F, true);
                    REST.put(d, entries);
                }

                entries.put(restKey, new RestState(d.ruheMatrizen, d.grenzPunkt,
                    d.grenzNormale, d.grenzVersatz, d.grenzTiefe,
                    d.bogenLaengeKehrwert, armMat, armInv));

                if (entries.size() > 4)
                {
                    Iterator<Long> oldest = entries.keySet().iterator();
                    oldest.next();
                    oldest.remove();
                }
            }
        }

        float[] poseMat = rahmen.poseMat(b);
        float[] chanMat = RigMathe.mul(poseMat, armInv);

        d.verformMatrizen = new float[n + 2][];
        d.verformBBS = new float[n + 2][];
        d.verformJoml = new org.joml.Matrix4f[n + 2];
        d.dualQuats = new Object[n + 1];
        d.verformMatrizen[0] = armInv;

        /* These two products are identical for every segment of this bone.
         * Keep the original multiplication order and rounding, without a persistent cache. */
        float[] poseArmMat = RigMathe.mul(chanMat, armMat);
        float[] poseIdentity = rahmen.zuPose(RigMathe.einheit());

        for (int a = 0; a <= n; a++)
        {
            float[] tmat = RigMathe.invertiert(d.ruheMatrizen[a]);
            float[] m = RigMathe.mul(RigMathe.mul(RigMathe.mul(poseArmMat, d.poseMatrizen[a]), tmat), armInv);

            d.verformMatrizen[a + 1] = m;
            /* Pose-Raum -> BBS-Raum fuer eine ABBILDUNG: GW · M · inv(GW). */
            d.verformBBS[a + 1] = rahmen.ausPose(RigMathe.mul(m, poseIdentity));
            d.verformJoml[a + 1] = RigMathe.zuJoml(d.verformBBS[a + 1]);

            float[] basis = RigMathe.mul(armMat, d.ruheMatrizen[a]);

            RigMathe.normalisiere(basis);
            d.dualQuats[a] = RigDualQuat.aus(basis, m);
        }
    }

    private static long restSignature(RigRahmen rahmen, BOBJBone b, BOBJBBone d)
    {
        long h = 17L;
        h = h * 31L + System.identityHashCode(rahmen);
        h = h * 31L + b.boneMat.hashCode();
        h = h * 31L + Float.floatToIntBits(b.restLength);
        h = h * 31L + d.segmente;
        h = h * 31L + (d.mapping == null ? 0 : d.mapping.hashCode());
        h = h * 31L + Arrays.hashCode(d.griffTyp);
        h = h * 31L + Arrays.deepHashCode(d.griffSkala);
        h = h * 31L + Arrays.hashCode(d.griffEase);
        h = h * 31L + Boolean.hashCode(d.endrollAlsInroll);
        h = h * 31L + Boolean.hashCode(d.skaliertEasing);
        h = h * 31L + Arrays.hashCode(d.ruheEase);
        h = h * 31L + Arrays.hashCode(d.ruheRoll);
        h = h * 31L + Arrays.hashCode(d.ruheKurveIn);
        h = h * 31L + Arrays.hashCode(d.ruheKurveOut);
        h = h * 31L + Arrays.hashCode(d.ruheSkalaIn);
        h = h * 31L + Arrays.hashCode(d.ruheSkalaOut);

        BOBJBone[] grips = griffe(b);

        for (BOBJBone grip : grips)
        {
            h = h * 31L + (grip == null ? 0 : grip.name.hashCode());

            if (grip != null)
            {
                h = h * 31L + grip.boneMat.hashCode();
                h = h * 31L + Float.floatToIntBits(grip.restLength);
                h = h * 31L + (grip.bbone == null ? 0 : grip.bbone.segmente);
            }
        }

        if (d.endrollAlsInroll)
        {
            BOBJBone prev = grips[0];
            h = h * 31L + (prev == null || prev.bbone == null
                ? 0 : Float.floatToIntBits(prev.bbone.ruheRoll[1]));
        }

        return h;
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
