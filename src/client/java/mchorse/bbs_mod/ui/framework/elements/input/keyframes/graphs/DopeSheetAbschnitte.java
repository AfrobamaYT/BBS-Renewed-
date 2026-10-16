package mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeElement;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeGroup;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.util.List;

/**
 * Collapse or expand every track section of the dope sheet at once, from its context menu. The
 * replay's own header row stays open, it holds all the rest. BBS FS 95e751a06 (Wemppy4/bbs-fs, MIT)
 * put these on two buttons; here they are menu entries.
 */
public final class DopeSheetAbschnitte
{
    public static void menue(ContextMenuManager menu, UIKeyframeDopeSheet sheet)
    {
        if (!hat(sheet.elements)) return;

        menu.action(Icons.ARROW_UP, L10n.lang("bbs.ui.film.replays.collapse_all"), () -> alle(sheet, false));
        menu.action(Icons.ARROW_DOWN, L10n.lang("bbs.ui.film.replays.expand_all"), () -> alle(sheet, true));
    }

    /** Opens the sections around a track and scrolls it into the middle (track search, BBS FS 0c1708ac7). */
    public static void zeigen(UIKeyframeDopeSheet dope, UIKeyframeSheet sheet)
    {
        oeffnen(dope.elements, sheet);
        dope.updateScrollSize();
        dope.lastSheet = sheet;
        dope.zeigen(sheet);
    }

    /** The titles of the sections a track sits in, outermost first. */
    public static String ort(UIKeyframeDopeSheet dope, UIKeyframeSheet sheet)
    {
        StringBuilder ort = new StringBuilder();

        pfad(dope.elements, sheet, ort);

        return ort.toString();
    }

    private static boolean oeffnen(List<UIKeyframeElement> elements, UIKeyframeSheet sheet)
    {
        for (UIKeyframeElement element : elements)
        {
            if (element == sheet) return true;
            if (!(element instanceof UIKeyframeGroup group) || !oeffnen(group.children, sheet)) continue;

            if (group.collapsed)
            {
                group.collapsed = false;
                if (group.onToggle != null) group.onToggle.run();
            }

            return true;
        }

        return false;
    }

    private static boolean pfad(List<UIKeyframeElement> elements, UIKeyframeSheet sheet, StringBuilder ort)
    {
        for (UIKeyframeElement element : elements)
        {
            if (element == sheet) return true;
            if (!(element instanceof UIKeyframeGroup group)) continue;

            int laenge = ort.length();

            ort.append(laenge == 0 ? "" : " / ").append(group.title.get());
            if (pfad(group.children, sheet, ort)) return true;
            ort.setLength(laenge);
        }

        return false;
    }

    private static void alle(UIKeyframeDopeSheet sheet, boolean offen)
    {
        setzen(sheet.elements, offen);
        sheet.updateScrollSize();
    }

    private static boolean hat(List<UIKeyframeElement> elements)
    {
        for (UIKeyframeElement element : elements)
        {
            if (element instanceof UIKeyframeGroup group && (!group.kopf || hat(group.children))) return true;
        }

        return false;
    }

    private static void setzen(List<UIKeyframeElement> elements, boolean offen)
    {
        for (UIKeyframeElement element : elements)
        {
            if (!(element instanceof UIKeyframeGroup group)) continue;

            if (!group.kopf && group.collapsed == offen)
            {
                group.collapsed = !offen;
                if (group.onToggle != null) group.onToggle.run();
            }

            setzen(group.children, offen);
        }
    }
}
