package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.bobj.BOBJBBone;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Reuses complete native segment output only for identical spline and pose inputs.
 * Like BOBJPoseSnapshot, entries retain immutable completed arrays, never mutable bones.
 * The existing rest-cache entry identifies the rest spline and coordinate conversion.
 */
final class RigBBonePoseCache
{
    private static final Map<BOBJBBone, List<Entry>> CACHE = new WeakHashMap<>();
    private static final int CAPACITY = 4;

    private RigBBonePoseCache()
    {}

    /** Capture before spline() clamps/mutates its private parameter object. */
    static int[] input(RigBBoneParam.Param p, float[] pose)
    {
        int[] values = new int[78];
        values[0] = p.segments;
        values[1] = Float.floatToRawIntBits(p.length);
        values[2] = p.doScale ? 1 : 0;
        values[3] = p.usePrev ? 1 : 0;
        values[4] = p.prevBbone ? 1 : 0;
        values[5] = p.useNext ? 1 : 0;
        values[6] = p.nextBbone ? 1 : 0;
        values[7] = Float.floatToRawIntBits(p.ease1);
        values[8] = Float.floatToRawIntBits(p.ease2);
        values[9] = Float.floatToRawIntBits(p.roll1);
        values[10] = Float.floatToRawIntBits(p.roll2);
        values[11] = Float.floatToRawIntBits(p.curveInX);
        values[12] = Float.floatToRawIntBits(p.curveInZ);
        values[13] = Float.floatToRawIntBits(p.curveOutX);
        values[14] = Float.floatToRawIntBits(p.curveOutZ);
        int offset = copy(values, 15, p.scale);
        offset = copy(values, offset, p.prevH);
        offset = copy(values, offset, p.nextH);
        offset = copy(values, offset, p.prevMat);
        offset = copy(values, offset, p.nextMat);
        offset = copy(values, offset, p.scaleIn);
        offset = copy(values, offset, p.scaleOut);
        copy(values, offset, pose);
        return values;
    }

    private static int copy(int[] target, int offset, float[] source)
    {
        for (float value : source)
        {
            target[offset++] = Float.floatToRawIntBits(value);
        }

        return offset;
    }

    static boolean restore(BOBJBBone bone, Object rest, int[] input)
    {
        synchronized (CACHE)
        {
            List<Entry> entries = CACHE.get(bone);

            if (entries != null)
            {
                for (int i = 0; i < entries.size(); i++)
                {
                    Entry entry = entries.get(i);

                    if (entry.rest == rest && Arrays.equals(entry.input, input))
                    {
                        entry.restore(bone);
                        entries.remove(i);
                        entries.add(entry);
                        return true;
                    }
                }
            }

            return false;
        }
    }

    static void store(BOBJBBone bone, Object rest, int[] input)
    {
        synchronized (CACHE)
        {
            List<Entry> entries = CACHE.computeIfAbsent(bone, ignored -> new ArrayList<>(CAPACITY));

            if (entries.size() == CAPACITY)
            {
                entries.remove(0);
            }

            entries.add(new Entry(bone, rest, input));
        }
    }

    private static final class Entry
    {
        private final Object rest;
        private final int[] input;
        private final float[][] pose, deformation, bbs;
        private final Matrix4f[] matrices;
        private final Object[] dualQuats;

        private Entry(BOBJBBone bone, Object rest, int[] input)
        {
            this.rest = rest;
            this.input = input;
            this.pose = bone.poseMatrizen;
            this.deformation = bone.verformMatrizen;
            this.bbs = bone.verformBBS;
            this.matrices = bone.verformJoml;
            this.dualQuats = bone.dualQuats;
        }

        private void restore(BOBJBBone bone)
        {
            bone.poseMatrizen = this.pose;
            bone.verformMatrizen = this.deformation;
            bone.verformBBS = this.bbs;
            bone.verformJoml = this.matrices;
            bone.dualQuats = this.dualQuats;
        }
    }
}
