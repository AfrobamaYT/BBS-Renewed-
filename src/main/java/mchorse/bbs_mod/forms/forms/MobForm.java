package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.settings.values.core.ValuePose;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.core.ValueLink;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.utils.pose.Pose;

public class MobForm extends Form implements IPosedForm
{
    public final ValueString mobID = new ValueString("mobId", "minecraft:chicken");
    public final ValueString mobNBT = new ValueString("mobNbt", "");

    public final ValueLink texture = new ValueLink("texture", null);
    public final ValueBoolean slim = new ValueBoolean("slim", false);

    public final ValuePose pose = new ValuePose("pose", new Pose());
    /** Each bone as a track of its own in the timeline (BBS FS e341fa733) */
    public final mchorse.bbs_mod.settings.values.numeric.ValueBoolean boneTracks = new mchorse.bbs_mod.settings.values.numeric.ValueBoolean("bone_tracks", true);
    public final ValuePose poseOverlay = new ValuePose("pose_overlay", new Pose());

    public MobForm()
    {
        this.slim.invisible();

        this.add(this.mobID);
        this.add(this.mobNBT);
        this.add(this.pose);
        this.add(this.poseOverlay);
        this.boneTracks.invisible();
        this.add(this.boneTracks);
        this.syncOverlayTracks();
        this.add(this.texture);
        this.add(this.slim);
    }

    @Override
    protected String getDefaultDisplayName()
    {
        return this.mobID.get().isEmpty() ? super.getDefaultDisplayName() : this.mobID.get();
    }

    public boolean isPlayer()
    {
        return this.mobID.get().equals("minecraft:player");
    }

    @Override
    public ValuePose getPose()
    {
        return this.pose;
    }

    @Override
    public mchorse.bbs_mod.settings.values.numeric.ValueBoolean getBoneTracks()
    {
        return this.boneTracks;
    }
}
