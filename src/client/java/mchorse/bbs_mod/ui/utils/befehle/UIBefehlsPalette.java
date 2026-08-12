package mchorse.bbs_mod.ui.utils.befehle;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.utils.keys.Keybind;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Befehls-Palette (P8a): erntet beim Oeffnen alle AKTIVEN Keybinds des laufenden
 * UI-Baums — der Baum selbst ist die Registry, es gibt keine tote Kopie daneben.
 * Kontextsensitiv wie Blenders Menue-Suche: was gerade nicht gilt, erscheint nicht.
 *
 * <p>v1-Bedienung: Tippen + Enter filtert; Enter bei unveraendertem Text (oder
 * Doppel-Enter) fuehrt den ersten Treffer aus, Klick in der Liste den gewaehlten.</p>
 */
public class UIBefehlsPalette extends UIOverlayPanel
{
    private final UITextbox suche;
    private final UIStringList liste;
    private final List<Keybind> alle = new ArrayList<>();
    private final List<Keybind> gefiltert = new ArrayList<>();
    private String letzteSuche = "";

    public static void oeffnen(UIContext context)
    {
        UIOverlay.addOverlay(context, new UIBefehlsPalette(context), 360, 280);
    }

    public UIBefehlsPalette(UIContext context)
    {
        super(mchorse.bbs_mod.l10n.L10n.lang("bbs.ui.command_palette.title"));

        this.sammle(context.menu.main);

        this.suche = new UITextbox(500, (text) -> this.enter(text));
        this.liste = new UIStringList((auswahl) -> this.ausfuehrenGewaehlt());

        this.suche.relative(this.content).x(6).y(6).w(1F, -12).h(20);
        this.liste.relative(this.content).x(6).y(30).w(1F, -12).h(1F, -36);

        this.content.add(this.suche, this.liste);
        this.filter("");
    }

    /** Der UI-Baum ist die Quelle: alle aktiven Keybinds unterhalb der Wurzel. */
    private void sammle(UIElement wurzel)
    {
        Deque<UIElement> stapel = new ArrayDeque<>();

        stapel.push(wurzel);

        while (!stapel.isEmpty())
        {
            UIElement element = stapel.pop();

            for (Keybind keybind : element.keys().keybinds)
            {
                if (keybind.isActive())
                {
                    this.alle.add(keybind);
                }
            }

            for (IUIElement kind : element.getChildren())
            {
                if (kind instanceof UIElement e)
                {
                    stapel.push(e);
                }
            }
        }
    }

    private String zeile(Keybind keybind)
    {
        String kategorie = keybind.getCombo().category.get();
        String label = keybind.getCombo().label.get();
        String combo = keybind.getCombo().getKeyCombo();

        return (kategorie.isEmpty() ? "" : kategorie + " · ") + label
            + (combo.isEmpty() ? "" : "  [" + combo + "]");
    }

    private void filter(String text)
    {
        String klein = text.toLowerCase();

        this.gefiltert.clear();
        this.liste.clear();

        for (Keybind keybind : this.alle)
        {
            if (klein.isEmpty() || this.zeile(keybind).toLowerCase().contains(klein))
            {
                this.gefiltert.add(keybind);
                this.liste.add(this.zeile(keybind));
            }
        }

        this.liste.update();
    }

    /** Enter: erst filtern; unveraenderter Text = ersten Treffer ausfuehren. */
    private void enter(String text)
    {
        if (text.equals(this.letzteSuche) && !this.gefiltert.isEmpty())
        {
            this.ausfuehren(0);

            return;
        }

        this.letzteSuche = text;
        this.filter(text);
    }

    private void ausfuehrenGewaehlt()
    {
        this.ausfuehren(this.liste.getIndex());
    }

    private void ausfuehren(int index)
    {
        if (index < 0 || index >= this.gefiltert.size())
        {
            return;
        }

        Keybind keybind = this.gefiltert.get(index);

        this.close();
        keybind.callback.run();
    }
}
