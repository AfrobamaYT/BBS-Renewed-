package mchorse.bbs_mod.ui.framework.elements.input.keyframes;

import mchorse.bbs_mod.utils.keyframes.Keyframe;

import java.util.List;

/** Flip and spread from the keyframe editor's context menu, moved out of UIKeyframes (over 800 lines). */
final class KeyframeAnordnen
{
    private KeyframeAnordnen()
    {}

    /** Mirrors the selected keyframes across the middle of their tick range. */
    static void spiegeln(UIKeyframes keyframes)
    {
        /* Find the global tick range of the selection across all tracks */
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;

        for (UIKeyframeSheet sheet : keyframes.getGraph().getSheets())
        {
            for (Keyframe keyframe : sheet.selection.getSelected())
            {
                min = Math.min(min, keyframe.getTick());
                max = Math.max(max, keyframe.getTick());
            }
        }

        if (min > max)
        {
            return;
        }

        float pivot = min + max;

        for (UIKeyframeSheet sheet : keyframes.getGraph().getSheets())
        {
            if (!sheet.selection.hasAny())
            {
                continue;
            }

            sheet.channel.preNotify();

            for (Keyframe keyframe : sheet.selection.getSelected())
            {
                keyframe.setTick(sheet.channel.constrainKeyframeTick(keyframe, pivot - keyframe.getTick()), false);
            }

            sheet.channel.postNotify();

            /* Re-sort the channel (the flip reverses the order) and re-sync the selection indices */
            sheet.sort();
        }

        keyframes.getGraph().pickSelected();
    }

    /** Spreads the selected keyframes evenly between the first and the last one. */
    static void verteilen(UIKeyframes keyframes)
    {
        for (UIKeyframeSheet sheet : keyframes.getGraph().getSheets())
        {
            List<Keyframe> selected = sheet.selection.getSelected();

            if (selected.isEmpty())
            {
                continue;
            }

            int min = Integer.MAX_VALUE;
            int max = Integer.MIN_VALUE;

            for (Keyframe keyframe : selected)
            {
                int index = sheet.channel.getKeyframes().indexOf(keyframe);

                min = Math.min(min, index);
                max = Math.max(max, index);
            }

            Keyframe minKf = sheet.channel.get(min);
            Keyframe maxKf = sheet.channel.get(max);
            int count = max - min;
            float distance = (maxKf.getTick() - minKf.getTick()) / count;

            sheet.channel.preNotify();

            for (int i = 1; i < count; i++)
            {
                int index = i + min;
                Keyframe kf = sheet.channel.get(index);

                kf.setTick(sheet.channel.constrainKeyframeTick(kf, minKf.getTick() + i * distance));
            }

            sheet.channel.postNotify();

            sheet.selection.clear();

            for (int i = min; i <= max; i++)
            {
                sheet.selection.add(i);
            }
        }

        keyframes.getGraph().pickSelected();
    }
}
