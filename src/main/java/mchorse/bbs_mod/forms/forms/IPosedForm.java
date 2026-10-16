package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.settings.values.core.ValuePose;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;

/**
 * A form whose skeleton is posed by a {@link mchorse.bbs_mod.utils.pose.Pose}: the model form and
 * the mob form. What drives a pose from outside (the per-bone tracks, the whole pose track's copy
 * on write reset) used to ask for a model form by name and so did nothing for a mob; what it needs
 * is this. From BBS FS e341fa733 (Wemppy4/bbs-fs, MIT).
 */
public interface IPosedForm
{
    public ValuePose getPose();

    /** Whether the timeline offers this form's bones as tracks of their own */
    public ValueBoolean getBoneTracks();
}
