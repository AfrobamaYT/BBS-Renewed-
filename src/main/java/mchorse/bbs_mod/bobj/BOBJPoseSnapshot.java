package mchorse.bbs_mod.bobj;

import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Evaluated rig state for one input pose. Original model/rig data is never copied here. */
public final class BOBJPoseSnapshot
{
    private final float[] mat;
    private final float[] origin;
    private final float[] skin;
    private final float[] channels;
    private final Transform.RotationMode[] modes;
    private final Quaternionf[] orient;
    private final Vector3f[] offset;
    private final BBoneState[] bendy;

    private BOBJPoseSnapshot(BOBJArmature armature)
    {
        int count = armature.orderedBones.size();

        this.mat = new float[count * 16];
        this.origin = new float[count * 16];
        this.skin = new float[count * 16];
        this.channels = new float[count * 16];
        this.modes = new Transform.RotationMode[count];
        this.orient = new Quaternionf[count];
        this.offset = new Vector3f[count];
        this.bendy = new BBoneState[count];
        this.fuellen(armature);
    }

    /**
     * Refill with the armature's current state. A recycled snapshot reuses its own arrays and
     * vector objects; everything it holds is only ever copied out by {@link #restore}.
     */
    private BOBJPoseSnapshot fuellen(BOBJArmature armature)
    {
        int count = armature.orderedBones.size();

        for (int i = 0; i < count; i++)
        {
            BOBJBone bone = armature.orderedBones.get(i);
            Transform t = bone.transform;
            int p = i * 16;

            bone.mat.get(this.mat, p);
            bone.originMat.get(this.origin, p);
            armature.matrices[bone.index].get(this.skin, p);
            this.channels[p] = t.translate.x;
            this.channels[p + 1] = t.translate.y;
            this.channels[p + 2] = t.translate.z;
            this.channels[p + 3] = t.rotate.x;
            this.channels[p + 4] = t.rotate.y;
            this.channels[p + 5] = t.rotate.z;
            this.channels[p + 6] = t.scale.x;
            this.channels[p + 7] = t.scale.y;
            this.channels[p + 8] = t.scale.z;
            this.channels[p + 9] = t.pivot.x;
            this.channels[p + 10] = t.pivot.y;
            this.channels[p + 11] = t.pivot.z;
            this.channels[p + 12] = t.quat.x;
            this.channels[p + 13] = t.quat.y;
            this.channels[p + 14] = t.quat.z;
            this.channels[p + 15] = t.quat.w;
            this.modes[i] = t.rotationMode;
            this.orient[i] = bone.orient == null ? null
                : this.orient[i] == null ? new Quaternionf(bone.orient) : this.orient[i].set(bone.orient);
            this.offset[i] = bone.offset == null ? null
                : this.offset[i] == null ? new Vector3f(bone.offset) : this.offset[i].set(bone.offset);
            this.bendy[i] = bone.bbone == null ? null
                : this.bendy[i] == null ? new BBoneState(bone.bbone) : this.bendy[i].fuellen(bone.bbone);
        }

        return this;
    }

    /**
     * @param frei an evicted snapshot of this armature to refill instead of allocating (may be
     *             {@code null}): playback evaluates a new pose every frame, and allocating ~55 KB
     *             per rig actor and frame was 8 % of the render thread's allocations (27.09.2026)
     */
    static BOBJPoseSnapshot capture(BOBJArmature armature, BOBJPoseSnapshot frei)
    {
        if (armature.matrices == null || armature.matrices.length != armature.orderedBones.size())
        {
            return null;
        }

        for (BOBJBone bone : armature.orderedBones)
        {
            if (armature.matrices[bone.index] == null)
            {
                return null;
            }
        }

        if (frei != null && frei.modes.length == armature.orderedBones.size())
        {
            return frei.fuellen(armature);
        }

        return new BOBJPoseSnapshot(armature);
    }

    void restore(BOBJArmature armature)
    {
        this.restoreExcept(armature, null);
    }

    /** Reuse completed output only outside the dependency closure being evaluated now. */
    public void restoreExcept(BOBJArmature armature, java.util.BitSet dirty)
    {
        for (int i = 0; i < armature.orderedBones.size(); i++)
        {
            BOBJBone bone = armature.orderedBones.get(i);
            if (dirty != null && dirty.get(bone.index))
            {
                continue;
            }
            Transform t = bone.transform;
            int p = i * 16;

            bone.mat.set(this.mat, p);
            bone.originMat.set(this.origin, p);

            Matrix4f matrix = armature.matrices[bone.index];
            armature.matrices[bone.index] = (matrix == null ? new Matrix4f() : matrix).set(this.skin, p);

            t.translate.set(this.channels[p], this.channels[p + 1], this.channels[p + 2]);
            t.rotate.set(this.channels[p + 3], this.channels[p + 4], this.channels[p + 5]);
            t.scale.set(this.channels[p + 6], this.channels[p + 7], this.channels[p + 8]);
            t.pivot.set(this.channels[p + 9], this.channels[p + 10], this.channels[p + 11]);
            t.quat.set(this.channels[p + 12], this.channels[p + 13], this.channels[p + 14], this.channels[p + 15]);
            t.rotationMode = this.modes[i];

            Quaternionf q = this.orient[i];
            bone.orient = q == null ? null : bone.orient == null ? new Quaternionf(q) : bone.orient.set(q);
            Vector3f shift = this.offset[i];
            bone.offset = shift == null ? null : bone.offset == null ? new Vector3f(shift) : bone.offset.set(shift);

            if (this.bendy[i] != null)
            {
                this.bendy[i].restore(bone.bbone);
            }
        }
    }

    /** Same-pose fast path: only fields replaced by channel drivers need canonicalizing. */
    void restoreDrivenChannels(BOBJArmature armature, int[] masks)
    {
        for (int i = 0; i < armature.orderedBones.size(); i++)
        {
            BOBJBone bone = armature.orderedBones.get(i);
            int mask = masks[bone.index];

            if (mask == 0)
            {
                continue;
            }

            Transform t = bone.transform;
            int p = i * 16;

            for (int axis = 0; axis < 3; axis++)
            {
                if ((mask & (1 << axis)) != 0)
                    t.translate.setComponent(axis, this.channels[p + axis]);
                if ((mask & (1 << (axis + 3))) != 0)
                    t.scale.setComponent(axis, this.channels[p + 6 + axis]);
                if ((mask & (1 << (axis + 6))) != 0)
                    t.rotate.setComponent(axis, this.channels[p + 3 + axis]);
            }

            if ((mask & (1 << 9)) != 0)
            {
                t.rotationMode = this.modes[i];
            }
        }
    }

    private static final class BBoneState
    {
        private final float[] channels = new float[28];
        private float[][] ruheMatrizen, poseMatrizen, verformMatrizen, verformBBS;
        private Matrix4f[] verformJoml;
        private Object[] dualQuats;
        private float[][] grenzPunkt, grenzNormale;
        private float[] grenzVersatz, grenzTiefe;
        private float bogenLaengeKehrwert;

        private BBoneState(BOBJBBone d)
        {
            this.fuellen(d);
        }

        private BBoneState fuellen(BOBJBBone d)
        {
            int p = 0;
            p = copy(this.channels, p, d.ruheEase);
            p = copy(this.channels, p, d.ruheRoll);
            p = copy(this.channels, p, d.ruheKurveIn);
            p = copy(this.channels, p, d.ruheKurveOut);
            p = copy(this.channels, p, d.ruheSkalaIn);
            p = copy(this.channels, p, d.ruheSkalaOut);
            p = copy(this.channels, p, d.poseEase);
            p = copy(this.channels, p, d.poseRoll);
            p = copy(this.channels, p, d.poseKurveIn);
            p = copy(this.channels, p, d.poseKurveOut);
            p = copy(this.channels, p, d.poseSkalaIn);
            copy(this.channels, p, d.poseSkalaOut);

            this.ruheMatrizen = d.ruheMatrizen;
            this.poseMatrizen = d.poseMatrizen;
            this.verformMatrizen = d.verformMatrizen;
            this.verformBBS = d.verformBBS;
            this.verformJoml = d.verformJoml;
            this.dualQuats = d.dualQuats;
            this.grenzPunkt = d.grenzPunkt;
            this.grenzNormale = d.grenzNormale;
            this.grenzVersatz = d.grenzVersatz;
            this.grenzTiefe = d.grenzTiefe;
            this.bogenLaengeKehrwert = d.bogenLaengeKehrwert;

            return this;
        }

        private void restore(BOBJBBone d)
        {
            int p = 0;
            p = copy(d.ruheEase, 0, this.channels, p);
            p = copy(d.ruheRoll, 0, this.channels, p);
            p = copy(d.ruheKurveIn, 0, this.channels, p);
            p = copy(d.ruheKurveOut, 0, this.channels, p);
            p = copy(d.ruheSkalaIn, 0, this.channels, p);
            p = copy(d.ruheSkalaOut, 0, this.channels, p);
            p = copy(d.poseEase, 0, this.channels, p);
            p = copy(d.poseRoll, 0, this.channels, p);
            p = copy(d.poseKurveIn, 0, this.channels, p);
            p = copy(d.poseKurveOut, 0, this.channels, p);
            p = copy(d.poseSkalaIn, 0, this.channels, p);
            copy(d.poseSkalaOut, 0, this.channels, p);

            d.ruheMatrizen = this.ruheMatrizen;
            d.poseMatrizen = this.poseMatrizen;
            d.verformMatrizen = this.verformMatrizen;
            d.verformBBS = this.verformBBS;
            d.verformJoml = this.verformJoml;
            d.dualQuats = this.dualQuats;
            d.grenzPunkt = this.grenzPunkt;
            d.grenzNormale = this.grenzNormale;
            d.grenzVersatz = this.grenzVersatz;
            d.grenzTiefe = this.grenzTiefe;
            d.bogenLaengeKehrwert = this.bogenLaengeKehrwert;
        }

        private static int copy(float[] target, int offset, float[] source)
        {
            System.arraycopy(source, 0, target, offset, source.length);
            return offset + source.length;
        }

        private static int copy(float[] target, int offset, float[] source, int sourceOffset)
        {
            System.arraycopy(source, sourceOffset, target, offset, target.length);
            return sourceOffset + target.length;
        }
    }
}
