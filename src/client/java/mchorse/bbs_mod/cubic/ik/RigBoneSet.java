package mchorse.bbs_mod.cubic.ik;

import java.util.BitSet;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import mchorse.bbs_mod.bobj.BOBJArmature;
import mchorse.bbs_mod.bobj.BOBJBone;

/** Evaluation-local indexed lookups; HashSet remains authoritative for unusual inputs. */
final class RigBoneSet extends HashSet<BOBJBone>
{
    private transient BOBJBone[] slots;
    private transient BitSet membership;

    static java.util.Set<BOBJBone> processed(BOBJArmature armature)
    {
        /* Compatibility fallback is selected once for the complete evaluation. */
        return Boolean.getBoolean("bbs.rig.hashsets") ? new HashSet<>() : new RigBoneSet(armature);
    }

    static java.util.Set<BOBJBone> chain(java.util.Set<BOBJBone> processed, Collection<BOBJBone> initial)
    {
        return processed instanceof RigBoneSet indexed
            ? new RigBoneSet(indexed, initial) : new HashSet<>(initial);
    }

    RigBoneSet(BOBJArmature armature)
    {
        this.slots = new BOBJBone[armature.bones.size()];
        this.membership = new BitSet(this.slots.length);

        for (BOBJBone bone : armature.bones.values())
        {
            if (bone != null && bone.getClass() == BOBJBone.class
                && bone.index >= 0 && bone.index < this.slots.length)
            {
                this.slots[bone.index] = bone;
            }
        }
    }

    RigBoneSet(RigBoneSet basis, Collection<BOBJBone> initial)
    {
        this.slots = basis.slots;
        this.membership = this.slots == null ? null : new BitSet(this.slots.length);
        this.addAll(initial);
    }

    @Override
    public boolean contains(Object value)
    {
        if (this.slots != null && value != null && value.getClass() == BOBJBone.class)
        {
            BOBJBone bone = (BOBJBone) value;
            int index = bone.index;

            if (index >= 0 && index < this.slots.length && this.slots[index] == bone)
            {
                return this.membership.get(index);
            }
        }

        return super.contains(value);
    }

    @Override
    public boolean add(BOBJBone bone)
    {
        boolean changed = super.add(bone);

        if (this.slots != null && bone != null && bone.getClass() == BOBJBone.class)
        {
            int index = bone.index;

            if (index >= 0 && index < this.slots.length && this.slots[index] == bone)
            {
                this.membership.set(index);
            }
            else
            {
                /* A changed/foreign index could later return to an old slot. */
                this.slots = null;
            }
        }

        return changed;
    }

    @Override
    public boolean remove(Object value)
    {
        this.slots = null;
        return super.remove(value);
    }

    @Override
    public void clear()
    {
        super.clear();
        if (this.membership != null)
        {
            this.membership.clear();
        }
    }

    @Override
    public Iterator<BOBJBone> iterator()
    {
        /* Iterator.remove bypasses HashSet.remove; retain its exact mutation semantics. */
        this.slots = null;
        return super.iterator();
    }

    @Override
    public Object clone()
    {
        RigBoneSet copy = (RigBoneSet) super.clone();
        copy.membership = this.membership == null ? null : (BitSet) this.membership.clone();
        return copy;
    }
}
