package mchorse.bbs_mod.ui.framework.elements.input.keyframes;

import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes.PastedKeyframes;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pasting and duplicating keyframes for UIKeyframes. Duplicating at the playhead with Ctrl+Alt+click
 * is BBS FS e06a02d36 (Wemppy4/bbs-fs, MIT): the copies land at the playhead on their own tracks.
 */
public final class KeyframeEinfuegen
{
    /** Which of Ctrl and Alt went down first; kept while both are held (BBS FS 27244d938). */
    private static boolean strgZuerst;

    /** Alt, then Ctrl: duplicate the selection at the playhead. */
    public static boolean amAbspielkopf(UIKeyframes keyframes)
    {
        if (!Window.isAltPressed() || !Window.isCtrlPressed()) strgZuerst = Window.isCtrlPressed();

        return !strgZuerst && Window.isAltPressed() && Window.isCtrlPressed() && !Window.isShiftPressed()
            && keyframes.getGraph().getSelected() != null;
    }

    /** Alt+click duplicates only beside a keyframe or at the playhead; on one it selects its column (BBS FS cea01fe06). */
    public static boolean dupliziert(UIKeyframes keyframes, UIContext context)
    {
        return keyframes.getGraph().getSelected() != null && !Window.isShiftPressed()
            && (amAbspielkopf(keyframes) || keyframes.getGraph().findKeyframe(context.mouseX, context.mouseY) == null);
    }

    /** Ctrl, then Alt (or nothing selected): a new key at the playhead on the hovered track. */
    public static boolean anlegenAmAbspielkopf(UIKeyframes keyframes)
    {
        return Window.isCtrlPressed() && Window.isAltPressed() && !Window.isShiftPressed() && !amAbspielkopf(keyframes);
    }

    public static boolean anlegen(UIKeyframes keyframes, UIContext context)
    {
        return anlegenAmAbspielkopf(keyframes) && keyframes.getGraph().addKeyframe(keyframes.toGraphX(keyframes.getTick()), context.mouseY);
    }

    public static float duplikatTick(UIKeyframes keyframes, UIContext context)
    {
        return amAbspielkopf(keyframes) ? keyframes.getTick() : Math.round(keyframes.fromGraphX(context.mouseX));
    }

    /** Only the film's keyboard shortcut pastes at the playhead; menus retain their consumer. */
    public static boolean tastatur(UIKeyframes editor, mchorse.bbs_mod.ui.utils.presets.UICopyPasteController controller, int mouseX, int mouseY)
    {
        if (!(editor instanceof mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes))
        {
            return controller.paste(mouseX, mouseY);
        }

        mchorse.bbs_mod.data.types.MapType data = mchorse.bbs_mod.graphics.window.Window.getClipboardMap(controller.copyPrefix);

        if (data != null)
        {
            einfuegen(editor, UIKeyframes.parseKeyframes(data), Math.round(editor.getTick()), mouseY, false);
        }

        return data != null;
    }

    /** With spurenBehalten a single track's keys stay on it instead of going to the hovered track. */
    public static void einfuegen(UIKeyframes view, Map<String, PastedKeyframes> keyframes, float offset, int mouseY, boolean spurenBehalten)
    {
        List<UIKeyframeSheet> sheets = view.getGraph().getSheets();
        UIKeyframeSheet hovered = view.getGraph().getSheet(mouseY);
        float min = Float.MAX_VALUE;

        view.getGraph().clearSelection();

        if (keyframes.size() == 1 && !spurenBehalten)
        {
            zu(hovered == null ? sheets.get(0) : hovered, keyframes.values().iterator().next(), offset);
            view.getGraph().pickSelected();
            return;
        }

        for (PastedKeyframes pasted : keyframes.values())
        {
            if (pasted.keyframes.isEmpty()) continue;
            pasted.keyframes.sort((a, b) -> Float.compare(a.getTick(), b.getTick()));
            min = Math.min(min, pasted.keyframes.get(0).getTick());
        }

        for (Map.Entry<String, PastedKeyframes> entry : keyframes.entrySet())
        {
            float d = min == Float.MAX_VALUE ? 0F : entry.getValue().keyframes.get(0).getTick() - min;

            for (UIKeyframeSheet sheet : sheets)
            {
                if (sheet.id.equals(entry.getKey())) zu(sheet, entry.getValue(), offset + d);
            }
        }

        view.getGraph().pickSelected();
    }

    private static void zu(UIKeyframeSheet sheet, PastedKeyframes pasted, float offset)
    {
        if (sheet.channel.getFactory() != pasted.factory) return;

        float firstX = pasted.keyframes.get(0).getTick();
        List<Keyframe> toSelect = new ArrayList<>();

        for (Keyframe keyframe : pasted.keyframes)
        {
            keyframe.setTick(sheet.channel.getSourceTick(keyframe.getTick() - firstX + offset));

            Keyframe inserted = sheet.channel.get(sheet.channel.insert(keyframe.getTick(), keyframe.getValue()));

            inserted.copy(keyframe);
            toSelect.add(inserted);
        }

        for (Keyframe select : toSelect) sheet.selection.add(sheet.channel.getKeyframes().indexOf(select));
    }
}
