package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIPoseKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIPoseTransformKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

import java.util.ArrayList;
import java.util.List;

/** Insert into the active pose context, even when the all-tracks view retains PLAYER category. */
public final class FilmPoseKey
{
    private FilmPoseKey()
    {}

    /** True when the pose context owns this command; false leaves ordinary player keying intact. */
    public static boolean insert(UIFilmPanel panel, float tick)
    {
        if (panel.replayEditor == null || panel.replayEditor.getReplay() == null)
        {
            return false;
        }

        UIKeyframeEditor editor = panel.replayEditor.keyframeEditor;
        List<UIKeyframeSheet> targets = new ArrayList<>();
        boolean activePose = editor != null && (editor.editor instanceof UIPoseKeyframeFactory
            || editor.editor instanceof UIPoseTransformKeyframeFactory);

        if (activePose)
        {
            UIKeyframeSheet sheet = editor.getSheet(editor.editor.getKeyframe());

            if (sheet != null)
            {
                targets.add(sheet);
            }
        }
        else if (editor != null && panel.replayEditor.getCategory() == UIReplaysEditor.ReplayCategory.POSE)
        {
            for (UIKeyframeSheet sheet : editor.view.getGraph().getSheets())
            {
                /* Key base poses and already animated bone tracks; do not seed every empty overlay. */
                if (sheet.channel.getFactory() == KeyframeFactories.POSE
                    && (sheet.id.equals("pose") || sheet.id.endsWith("/pose"))
                    || sheet.isBoneTrack && !sheet.channel.getKeyframes().isEmpty()
                        && sheet.channel.getFactory() == KeyframeFactories.POSE_TRANSFORM)
                {
                    targets.add(sheet);
                }
            }
        }
        else
        {
            return false;
        }

        if (targets.isEmpty())
        {
            panel.getContext().notifyInfo(mchorse.bbs_mod.l10n.keys.IKey.raw("Select a pose track to insert a key"));
            return true;
        }

        panel.getUndoHandler().submitUndo();
        panel.getUndoHandler().getUndoManager().markLastUndoNoMerging();
        BaseValue.edit(panel.replayEditor.getReplay().properties, properties ->
        {
            for (UIKeyframeSheet sheet : targets)
            {
                UIReplaysEditorUtils.ensureKeyframe(sheet, tick);
            }
        });
        panel.getUndoHandler().submitUndo();
        panel.getUndoHandler().getUndoManager().markLastUndoNoMerging();
        editor.view.getGraph().clearSelection();

        for (UIKeyframeSheet sheet : targets)
        {
            for (Object value : sheet.channel.getKeyframes())
            {
                Keyframe<?> key = (Keyframe<?>) value;

                if (key.getTick() == sheet.channel.getSourceTick(tick))
                {
                    sheet.selection.add(sheet.channel.getKeyframes().indexOf(key));
                }
            }
        }

        /* A collapsed bone row is omitted by getSelected(); keep its active editor anyway. */
        editor.view.getGraph().pickKeyframe(targets.getFirst().selection.getFirst());

        return true;
    }
}
