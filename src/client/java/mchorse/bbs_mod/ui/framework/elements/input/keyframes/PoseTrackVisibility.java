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
        /* The replay's own overlay layers (pose_overlay0..N, transform_overlay0..N, set by
         * recording.pose_overlays and transform_overlays) are working layers: hidden while empty, a model
         * showed one pose overlay and several transform overlays, another the reverse,
         * depending on which happened to hold a keyframe (owner, 2026-10-14). */
        if (sheet.id != null && sheet.id.matches("(pose|transform)_overlay\\d*")) return true;
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
