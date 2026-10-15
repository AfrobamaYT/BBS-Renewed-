package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.bobj.BOBJArmature;
import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.utils.pose.Transform;
import java.util.Arrays;

/** Exact incoming channels, excluding only axes unconditionally replaced by channel drivers. */
public final class RigInputPose
{
    private final BOBJBone[] bones;
    private final BOBJBone[] parents;
    private final int[] bits;

    public RigInputPose(BOBJArmature armature, int[] masks)
    {
        this.bones = armature.orderedBones.toArray(BOBJBone[]::new);
        this.parents = new BOBJBone[this.bones.length];
        this.bits = new int[this.bones.length * 28];
        for (int i = 0; i < this.bones.length; i++)
        {
            this.parents[i] = this.bones[i].parentBone;
            this.read(this.bits, i, masks);
        }
    }

    public boolean matches(BOBJArmature armature, int[] masks, int[] scratch)
    {
        if (armature.orderedBones.size() != this.bones.length || scratch.length != this.bits.length)
        {
            return false;
        }
        for (int i = 0; i < this.bones.length; i++)
        {
            if (armature.orderedBones.get(i) != this.bones[i] || this.bones[i].parentBone != this.parents[i])
            {
                return false;
            }
            this.read(scratch, i, masks);
        }
        return Arrays.equals(this.bits, scratch);
    }

    private void read(int[] target, int i, int[] masks)
    {
        BOBJBone bone = this.bones[i];
        Transform t = bone.transform;
        int mask = masks == null || masks.length == 0 ? 0 : masks[bone.index];
        int p = i * 28;
        for (int axis = 0; axis < 3; axis++)
        {
            target[p + axis] = (mask & (1 << axis)) == 0 ? Float.floatToRawIntBits(t.translate.get(axis)) : 0;
            target[p + 3 + axis] = (mask & (1 << (axis + 3))) == 0 ? Float.floatToRawIntBits(t.scale.get(axis)) : 0;
            target[p + 6 + axis] = (mask & (1 << (axis + 6))) == 0 ? Float.floatToRawIntBits(t.rotate.get(axis)) : 0;
            target[p + 9 + axis] = Float.floatToRawIntBits(t.pivot.get(axis));
        }
        target[p + 12] = Float.floatToRawIntBits(t.quat.x);
        target[p + 13] = Float.floatToRawIntBits(t.quat.y);
        target[p + 14] = Float.floatToRawIntBits(t.quat.z);
        target[p + 15] = Float.floatToRawIntBits(t.quat.w);
        target[p + 16] = (mask & (1 << 9)) == 0 ? (t.rotationMode == null ? -1 : t.rotationMode.ordinal()) : 0;
        target[p + 17] = bone.orient == null ? 0 : 1;
        target[p + 18] = bone.orient == null ? 0 : Float.floatToRawIntBits(bone.orient.x);
        target[p + 19] = bone.orient == null ? 0 : Float.floatToRawIntBits(bone.orient.y);
        target[p + 20] = bone.orient == null ? 0 : Float.floatToRawIntBits(bone.orient.z);
        target[p + 21] = bone.orient == null ? 0 : Float.floatToRawIntBits(bone.orient.w);
        target[p + 22] = bone.offset == null ? 0 : 1;
        target[p + 23] = bone.offset == null ? 0 : Float.floatToRawIntBits(bone.offset.x);
        target[p + 24] = bone.offset == null ? 0 : Float.floatToRawIntBits(bone.offset.y);
        target[p + 25] = bone.offset == null ? 0 : Float.floatToRawIntBits(bone.offset.z);
        target[p + 26] = bone.index;
        target[p + 27] = mask;
    }
}
