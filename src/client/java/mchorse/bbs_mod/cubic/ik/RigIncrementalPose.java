package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.bobj.BOBJArmature;
import mchorse.bbs_mod.bobj.BOBJPoseSnapshot;
import mchorse.bbs_mod.data.types.MapType;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/** Property-only incremental evaluation. Any changed ordinary pose input retains the full native graph. */
final class RigIncrementalPose
{
    private final MapType source;
    private BOBJArmature owner;
    private RigDependencyPlan plan;
    private final List<RigDependencyPlan.Property> keys = new ArrayList<>();
    private BOBJPoseSnapshot snapshot;
    private RigInputPose inputs;
    private int[] values;
    private int frame;
    private RigInputPose pendingInputs;
    private int[] pendingValues;
    private int pendingFrame;
    private int[] scratch;

    RigIncrementalPose(MapType source)
    {
        this.source = source;
    }

    BitSet begin(BOBJArmature armature, RigMechanik solver)
    {
        if (Boolean.getBoolean("bbs.rig.fullGraph") || !BOBJArmature.ENABLE_POSE_CACHE || !solver.poseCacheSicher())
        {
            this.remember(null);
            return null;
        }
        if (this.owner != armature || this.plan == null)
        {
            this.remember(null);
            this.owner = armature;
            this.plan = new RigDependencyPlan(armature, this.source);
            this.scratch = new int[armature.orderedBones.size() * 28];
            this.keys.clear();
            for (String bone : solver.treiber.regler.knochen())
            {
                for (String property : solver.treiber.regler.reglerVon(bone).keySet())
                {
                    this.keys.add(new RigDependencyPlan.Property(bone, property));
                }
            }
        }
        int[] masks = solver.eingabeMasken(armature);
        int currentFrame = Float.floatToRawIntBits(solver.treiber.bild);
        int[] currentValues = new int[this.keys.size()];
        List<RigDependencyPlan.Property> changed = new ArrayList<>();
        for (int i = 0; i < this.keys.size(); i++)
        {
            RigDependencyPlan.Property key = this.keys.get(i);
            currentValues[i] = Float.floatToRawIntBits(solver.treiber.regler.wertVon(key.bone(), key.name(), 0F));
            if (this.values != null && currentValues[i] != this.values[i])
            {
                changed.add(key);
            }
        }
        boolean samePose = this.inputs != null && this.inputs.matches(armature, masks, this.scratch);
        this.pendingInputs = samePose ? this.inputs : new RigInputPose(armature, masks);
        this.pendingValues = currentValues;
        this.pendingFrame = currentFrame;
        if (samePose && this.snapshot != null && this.frame == currentFrame && !changed.isEmpty())
        {
            BitSet dirty = this.plan.affected(changed);
            if (dirty != null && dirty.cardinality() < armature.orderedBones.size())
            {
                this.snapshot.restoreExcept(armature, dirty);
                return dirty;
            }
        }
        return null;
    }

    /** A failed or invalidated evaluation must never become the basis of the next update. */
    void remember(BOBJPoseSnapshot completed)
    {
        if (completed == null || this.pendingInputs == null)
        {
            this.snapshot = null;
            this.inputs = null;
            this.values = null;
            this.plan = null;
        }
        else
        {
            this.snapshot = completed;
            this.inputs = this.pendingInputs;
            this.values = this.pendingValues;
            this.frame = this.pendingFrame;
        }
        this.pendingInputs = null;
        this.pendingValues = null;
    }
}
