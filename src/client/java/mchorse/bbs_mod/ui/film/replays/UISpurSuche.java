package mchorse.bbs_mod.ui.film.replays;

import com.mojang.blaze3d.platform.InputConstants;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.DopeSheetAbschnitte;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Find a track of the open replay by typing: Enter opens its section and scrolls to it, Ctrl+Enter
 * also puts a key at the playhead there (or selects the one already there). From BBS FS 0c1708ac7
 * (Wemppy4/bbs-fs, MIT); it searches the tracks the dope sheet holds, laid out like the command
 * palette.
 */
public class UISpurSuche extends UIOverlayPanel
{
    private record Spur(UIKeyframeSheet sheet, String name, String ort) {}

    private final UIKeyframes view;
    private final List<Spur> alle = new ArrayList<>();
    private final List<Spur> treffer = new ArrayList<>();
    private final UITextbox suche;
    private UIStringList liste;

    public static void oeffnen(UIContext context, UIKeyframes view)
    {
        UIOverlay.addOverlay(context, new UISpurSuche(view), 420, 300);
    }

    private UISpurSuche(UIKeyframes view)
    {
        super(L10n.lang("bbs.ui.film.track_search.title"));

        this.view = view;

        for (UIKeyframeSheet sheet : view.getDopeSheet().getSheets())
        {
            this.alle.add(new Spur(sheet, sheet.title.get(), DopeSheetAbschnitte.ort(view.getDopeSheet(), sheet)));
        }

        this.suche = new Eingabe();
        this.suche.placeholder(L10n.lang("bbs.ui.film.track_search.placeholder"));
        this.liste = new UIStringList((auswahl) -> this.waehlen(this.liste.getIndex(), Window.isCtrlPressed()));
        this.suche.relative(this.content).x(6).y(6).w(1F, -12).h(20);
        this.liste.relative(this.content).x(6).y(30).w(1F, -12).h(1F, -36);
        this.content.add(this.suche, this.liste);
        this.filtern("");
    }

    @Override
    protected void onAdd(UIElement parent)
    {
        super.onAdd(parent);
        this.getContext().focus(this.suche);
    }

    /** Whole name before its start before anywhere in it before only in the section path. */
    private static int rang(Spur spur, String suche)
    {
        String name = spur.name.toLowerCase(Locale.ROOT), alles = name + " " + spur.ort.toLowerCase(Locale.ROOT) + " " + spur.sheet.id;

        for (String wort : suche.split(" ")) if (!alles.contains(wort)) return -1;

        return name.equals(suche) ? 0 : name.startsWith(suche) ? 1 : name.contains(suche) ? 2 : 3;
    }

    private void filtern(String text)
    {
        String suche = text.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");

        this.treffer.clear();
        this.liste.clear();
        this.alle.stream().filter((spur) -> rang(spur, suche) >= 0)
            .sorted(Comparator.comparingInt((Spur spur) -> rang(spur, suche))).forEach(this.treffer::add);

        for (Spur spur : this.treffer) this.liste.add(spur.ort.isEmpty() ? spur.name : spur.name + "   ·  " + spur.ort);

        this.liste.setIndex(this.treffer.isEmpty() ? -1 : 0);
        this.liste.update();
    }

    private void waehlen(int index, boolean keyAnlegen)
    {
        if (index < 0 || index >= this.treffer.size()) return;

        UIKeyframeSheet sheet = this.treffer.get(index).sheet;

        this.close();
        DopeSheetAbschnitte.zeigen(this.view.getDopeSheet(), sheet);

        if (!keyAnlegen) return;

        float tick = sheet.channel.getSourceTick(this.view.getTick());

        for (Object entry : sheet.channel.getKeyframes())
        {
            if (((Keyframe) entry).getTick() == tick)
            {
                this.view.getDopeSheet().selectKeyframe((Keyframe) entry);
                return;
            }
        }

        this.view.getDopeSheet().selectKeyframe(this.view.getDopeSheet().addKeyframeManually(sheet, this.view.getTick(), null));
    }

    private class Eingabe extends UITextbox
    {
        Eingabe()
        {
            super(256, UISpurSuche.this::filtern);
        }

        @Override
        public boolean subKeyPressed(UIContext context)
        {
            UISpurSuche suche = UISpurSuche.this;

            if (this.isFocused() && context.isPressed(InputConstants.KEY_RETURN))
            {
                suche.waehlen(Math.max(0, suche.liste.getIndex()), Window.isCtrlPressed());
                return true;
            }

            if (this.isFocused() && (context.isPressed(InputConstants.KEY_UP) || context.isPressed(InputConstants.KEY_DOWN)))
            {
                int schritt = context.isPressed(InputConstants.KEY_UP) ? -1 : 1;

                suche.liste.setIndex(Math.max(0, Math.min(suche.liste.getIndex() + schritt, suche.treffer.size() - 1)));
                return true;
            }

            return super.subKeyPressed(context);
        }
    }
}
