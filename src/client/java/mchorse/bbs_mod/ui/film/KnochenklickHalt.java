package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

/**
 * Katalog C3: what you see is what you edit. BBS has no unkeyed pose between keys, so when the
 * playhead is not on the one selected key (a bone click keeps the playhead, or you scrubbed away),
 * editing behaves like auto-key: the first change creates the key at the playhead instead of
 * silently changing the selected key elsewhere. Several selected keys keep editing them together.
 * User decisions 2026-10-15.
 */
public final class KnochenklickHalt
{
    private KnochenklickHalt()
    {}

    /** Kept for FilmAutoKey.pickTick; the rule below needs no remembered click any more */
    public static void setzen(int cursor, int closest)
    {}

    public static boolean gilt(UIFilmPanel panel)
    {
        if (panel == null || panel.replayEditor.keyframeEditor == null || panel.replayEditor.keyframeEditor.editor == null)
        {
            return false;
        }

        Keyframe<?> key = panel.replayEditor.keyframeEditor.editor.getKeyframe();
        UIKeyframeSheet sheet = key == null ? null : panel.replayEditor.keyframeEditor.view.getGraph().getSheet(key);

        if (sheet == null || sheet.selection.getSelected().size() > 1)
        {
            return false;
        }

        return sheet.channel.getSourceTick(panel.getCursor(0F)) != key.getTick();
    }
}
