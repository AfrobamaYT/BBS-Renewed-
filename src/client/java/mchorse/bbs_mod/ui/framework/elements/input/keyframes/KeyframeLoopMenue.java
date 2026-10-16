package mchorse.bbs_mod.ui.framework.elements.input.keyframes;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeLoop;
import mchorse.bbs_mod.utils.keyframes.KeyframeLoops;
import java.util.ArrayList;
import java.util.List;

/** The keyframe loop entries of the keyframe editor's context menu (BBS FS 494fe5d19, Wemppy4/bbs-fs, MIT). */
final class KeyframeLoopMenue
{
    private KeyframeLoopMenue()
    {}

    static void fuellen(UIKeyframeLoops loops, ContextMenuManager menu, UIContext context)
    {
        List<KeyframeChannel<?>> channels = new ArrayList<>();
        float start = Float.POSITIVE_INFINITY, end = Float.NEGATIVE_INFINITY;
        for (UIKeyframeSheet sheet : loops.view.getGraph().getSheets())
        {
            if (!sheet.selection.hasAny()) continue;
            channels.add(sheet.channel);
            for (Keyframe key : sheet.selection.getSelected())
            {
                start = Math.min(start, key.getTick());
                end = Math.max(end, key.getTick());
            }
        }

        /* A block owns a continuous source interval; don't silently include unselected keys. */
        boolean complete = true;
        for (UIKeyframeSheet sheet : loops.view.getGraph().getSheets())
        {
            if (!sheet.selection.hasAny()) continue;
            for (Object entry : sheet.channel.getKeyframes())
            {
                Keyframe key = (Keyframe) entry;
                if (key.getTick() >= start && key.getTick() <= end && !sheet.selection.has(key)) complete = false;
            }
        }
        boolean canCreate = complete && KeyframeLoops.canCreate(channels, start, end);
        String problem = end <= start ? "selection_duration" : !complete ? "selection_range" : "selection_overlap";
        if (!channels.isEmpty())
        {
            float sourceStart = start, sourceEnd = end;
            menu.action(Icons.REFRESH, L10n.lang("bbs.ui.keyframes.loop.create"), () ->
            {
                if (!canCreate)
                {
                    context.notify(L10n.lang("bbs.ui.keyframes.loop." + problem), Colors.BLUE);
                    return;
                }
                loops.edit(channels, () ->
                {
                    KeyframeLoop loop = KeyframeLoops.create(channels, sourceStart, sourceEnd);
                    loops.selectedChannel = channels.get(0);
                    loops.selectedId = loop.id();
                });
                loops.view.getGraph().clearSelection();
            });
        }

        UIKeyframeLoops.Hit hit = loops.hit(context);
        if (hit != null)
        {
            menu.action(Icons.EDIT, L10n.lang("bbs.ui.keyframes.loop.edit"), () -> loops.open(hit.sheet().channel, hit.loop().id()));
            menu.action(Icons.REMOVE, L10n.lang("bbs.ui.keyframes.loop.remove"), () -> loops.remove(hit.sheet().channel, hit.loop().id()));
        }
    }
}
