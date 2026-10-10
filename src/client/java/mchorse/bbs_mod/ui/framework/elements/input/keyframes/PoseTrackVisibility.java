package mchorse.bbs_mod.ui.framework.elements.input.keyframes;

import mchorse.bbs_mod.film.replays.PerLimbService;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.pose.Pose;

/** Animation ownership for the legacy combined-pose and separate-bone track views. */
public final class PoseTrackVisibility
{
    private PoseTrackVisibility() {}

    public static boolean isAnimated(UIKeyframeSheet sheet, UIKeyframeSheet parent)
    {
        if (sheet.channel == null || !sheet.channel.isEmpty()) return true;
        PerLimbService.PoseBonePath path = PerLimbService.parsePoseBonePath(sheet.id);
        if (parent == null || path == null) return false;

        for (Object entry : parent.channel.getKeyframes())
        {
            if (entry instanceof Keyframe<?> keyframe && keyframe.getValue() instanceof Pose pose
                && pose.transforms.containsKey(path.bone()))
            {
                return true;
            }
        }

        return false;
    }
}
