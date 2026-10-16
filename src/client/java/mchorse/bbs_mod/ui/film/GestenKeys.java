package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditorUtils;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

import java.util.ArrayList;
import java.util.List;

/**
 * Keys an R/G/S gesture created at the playhead (auto-key, C3 off-key edit). Cancelling the gesture
 * (Escape, RMB) removes them again: in Blender a cancelled transform never inserts a key. Before,
 * the values went back but the new key stayed at the cursor (measured 2026-10-16).
 */
public final class GestenKeys
{
    private static final List<Neu> NEU = new ArrayList<>();

    private record Neu(UIKeyframeSheet sheet, Keyframe key, List<Keyframe> vorher)
    {}

    private GestenKeys()
    {}

    /** channel.insert; during a film gesture it remembers the key and the selection before it */
    public static int einfuegen(UIKeyframeSheet sheet, float tick, Object value)
    {
        List<Keyframe> vorher = geste() ? new ArrayList<>(sheet.selection.getSelected()) : null;
        int index = sheet.channel.insert(tick, value);

        if (vorher == null)
        {
            NEU.clear(); /* outside a gesture nothing older may be taken back later */
        }
        else
        {
            NEU.add(new Neu(sheet, sheet.channel.get(index), vorher));
        }

        return index;
    }

    /** From UIPropTransform.rejectChanges, after the values went back */
    public static void verwerfen()
    {
        for (int i = NEU.size() - 1; i >= 0; i--)
        {
            Neu neu = NEU.get(i);
            int index = index(neu.sheet, neu.key);

            if (index < 0)
            {
                continue;
            }

            neu.sheet.channel.remove(index);
            neu.sheet.selection.clear();

            for (Keyframe key : neu.vorher)
            {
                int alt = index(neu.sheet, key);

                if (alt >= 0)
                {
                    neu.sheet.selection.add(alt);
                }
            }
        }

        NEU.clear();
    }

    /** From UIPropTransform.acceptChanges: the gesture keeps its keys */
    public static void behalten()
    {
        NEU.clear();
    }

    /* Identity, not equals: Keyframe.equals compares every field (whole poses), needlessly slow here */
    private static int index(UIKeyframeSheet sheet, Keyframe key)
    {
        List<?> keys = sheet.channel.getKeyframes();

        for (int i = 0; i < keys.size(); i++)
        {
            if (keys.get(i) == key)
            {
                return i;
            }
        }

        return -1;
    }

    private static boolean geste()
    {
        UIFilmPanel panel = BBSModClient.getDashboard().getPanels().getPanel(UIFilmPanel.class);
        UIPropTransform transform = panel == null || panel.replayEditor == null || panel.replayEditor.keyframeEditor == null ? null
            : mchorse.bbs_mod.ui.film.replays.UIReplayWurzelTransform.filmTransform(panel);

        return transform != null && transform.isEditing();
    }
}
