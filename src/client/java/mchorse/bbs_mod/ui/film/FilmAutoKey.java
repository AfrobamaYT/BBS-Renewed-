package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.settings.EditorZustaende;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframePropTransform;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import mchorse.bbs_mod.utils.pose.Transform;

/** Edit-triggered keying, separate from continuous replay recording. Defaults off; the switch survives a restart (EditorZustaende). */
public final class FilmAutoKey
{
    private FilmAutoKey() {}

    public static boolean isEnabled()
    {
        return EditorZustaende.autoKey.get();
    }

    private static UIFilmPanel panel()
    {
        return BBSModClient.getDashboard().panels.panel instanceof UIFilmPanel panel ? panel : null;
    }

    public static boolean isEnabled(UIKeyframes view)
    {
        UIFilmPanel panel = panel();
        return EditorZustaende.autoKey.get() && panel != null && panel.replayEditor.keyframeEditor != null
            && panel.replayEditor.keyframeEditor.view == view;
    }

    private static UIFilmPanel owner(UIElement element)
    {
        UIFilmPanel panel = panel();
        if (panel == null || panel.replayEditor.keyframeEditor == null)
        {
            return null;
        }
        for (UIElement parent = element; parent != null; parent = parent.getParent())
        {
            if (parent == panel.replayEditor.keyframeEditor.editor)
            {
                return panel;
            }
        }
        return null;
    }

    public static boolean isEditing(UIElement element)
    {
        return EditorZustaende.autoKey.get() && owner(element) != null;
    }

    public static int pickTick(UIKeyframes view, int closest, int current)
    {
        return isEnabled(view) ? current : closest;
    }

    public static void toggle()
    {
        EditorZustaende.autoKey.set(!EditorZustaende.autoKey.get());
        UIFilmPanel panel = panel();
        if (panel != null && panel.replayEditor.keyframeEditor != null && panel.replayEditor.keyframeEditor.editor != null)
        {
            for (UIKeyframePropTransform transform : panel.replayEditor.keyframeEditor.editor.getChildren(UIKeyframePropTransform.class))
            {
                transform.refillTransform();
            }
        }
    }

    public static Transform transform(UIPropTransform element, Transform fallback)
    {
        if (!(element instanceof UIKeyframePropTransform))
        {
            return fallback;
        }
        UIFilmPanel panel = owner(element);
        if (panel == null || panel.getController().isTransformRecording())
        {
            return fallback;
        }
        Keyframe<?> template = panel.replayEditor.keyframeEditor.editor.getKeyframe();
        UIKeyframeSheet sheet = panel.replayEditor.keyframeEditor.view.getGraph().getSheet(template);
        if (sheet == null)
        {
            return fallback;
        }
        Object value = template.getValue();
        String bone = null;
        if (value instanceof Pose)
        {
            mchorse.bbs_mod.utils.Pair<String, Boolean> selected = panel.replayEditor.keyframeEditor.getBone();
            if (selected == null)
            {
                return fallback;
            }
            int prefix = sheet.id.lastIndexOf('/') + 1;
            bone = selected.a.substring(Math.min(prefix, selected.a.length()));
        }
        if (EditorZustaende.autoKey.get())
        {
            int tick = panel.getCursor();
            value = null;
            for (Object entry : sheet.channel.getKeyframes())
            {
                if (entry instanceof Keyframe<?> frame && frame.getTick() == tick)
                {
                    value = frame.getValue();
                }
            }
            if (value == null)
            {
                if (bone != null)
                {
                    /* Evaluate only the edited bone. Native whole-pose interpolation can
                     * add missing bones to its inputs; reads must not mutate those keys. */
                    KeyframeSegment<?> segment = sheet.channel.find(tick);
                    if (segment == null)
                    {
                        return fallback;
                    }
                    return (PoseTransform) KeyframeFactories.POSE_TRANSFORM.interpolate(
                        boneFrame(segment.preA, bone), boneFrame(segment.a, bone), boneFrame(segment.b, bone),
                        boneFrame(segment.postB, bone), segment.a.getInterpolation(), segment.x).copy();
                }
                value = sheet.channel.interpolate(tick);
            }
        }
        if (value instanceof Transform transform)
        {
            return transform;
        }
        if (value instanceof Pose pose)
        {
            return pose.transforms.getOrDefault(bone, new PoseTransform());
        }
        return fallback;
    }

    private static Keyframe<PoseTransform> boneFrame(Keyframe<?> source, String bone)
    {
        Pose pose = (Pose) source.getValue();
        Keyframe<PoseTransform> frame = new Keyframe<>("", KeyframeFactories.POSE_TRANSFORM, source.getTick(),
            pose.transforms.getOrDefault(bone, new PoseTransform()));
        frame.copyOverExtra(source);
        return frame;
    }
}
