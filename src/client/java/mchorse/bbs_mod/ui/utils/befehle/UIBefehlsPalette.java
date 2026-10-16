package mchorse.bbs_mod.ui.utils.befehle;

import com.mojang.blaze3d.platform.InputConstants;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.utils.context.ContextAction;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.keys.Keybind;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

/**
 * Befehls-Palette (P8a): erntet beim Oeffnen alle AKTIVEN Keybinds und alle
 * Kontextmenue-Actions des laufenden UI-Baums — der Baum selbst ist die
 * Registry, es gibt keine tote Kopie daneben. Kontextsensitiv wie Blenders
 * Menue-Suche: was gerade nicht gilt, erscheint nicht.
 *
 * <p>Kontext-Ernte: je sichtbarem Element werden seine context()-Consumer
 * gegen einen frischen {@link ContextMenuManager} gefahren und die Actions
 * eingesammelt, ohne ein Menue zu zeigen. Elemente, die stattdessen
 * createContextMenu() ueberschreiben oder einen Supplier setzen, sieht die
 * Ernte nicht (v1).</p>
 *
 * <p>v1-Bedienung: Tippen + Enter filtert; Enter bei unveraendertem Text
 * (oder Doppel-Enter) fuehrt den ersten Treffer aus, Klick den gewaehlten.</p>
 */
public class UIBefehlsPalette extends UIOverlayPanel
{
    /* ⚠ FALLE — UIElement.contextOptions ist privat; der Monolith (1089 Z.)
     * bekommt fuer die Ernte keinen Getter. Schlaegt die Reflection fehl
     * (Umbenennung!), wird das als Zeile IN der Palette sichtbar — nie still. */
    private static Field CONTEXT_OPTIONS;
    private static String REFLECTION_FEHLER;

    private final UITextbox suche;
    private final UIStringList liste;
    private final List<PalettenBefehl> alle = new ArrayList<>();
    private final List<PalettenBefehl> gefiltert = new ArrayList<>();

    public static void oeffnen(UIContext context)
    {
        UIOverlay.addOverlay(context, new UIBefehlsPalette(context), 360, 280);
    }

    public UIBefehlsPalette(UIContext context)
    {
        super(mchorse.bbs_mod.l10n.L10n.lang("bbs.ui.command_palette.title"));

        this.sammle(context.menu.main);

        this.suche = new SuchEingabe(500, (text) -> this.filter(text));
        this.liste = new UIStringList((auswahl) -> this.ausfuehrenGewaehlt());

        this.suche.relative(this.content).x(6).y(6).w(1F, -12).h(20);
        this.liste.relative(this.content).x(6).y(30).w(1F, -12).h(1F, -36);

        this.content.add(this.suche, this.liste);
        this.filter("");
    }

    /** Der UI-Baum ist die Quelle: aktive Keybinds + Kontext-Actions unterhalb der Wurzel. */
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
                    this.alle.add(new PalettenBefehl(zeileKeybind(keybind), keybind.callback));
                }
            }

            if (element.isVisible())
            {
                this.sammleKontext(element);
            }

            for (IUIElement kind : element.getChildren())
            {
                if (kind instanceof UIElement e)
                {
                    stapel.push(e);
                }
            }
        }

        if (REFLECTION_FEHLER != null)
        {
            this.alle.add(new PalettenBefehl("⚠ " + REFLECTION_FEHLER, null));
        }
    }

    /** Faehrt die context()-Consumer des Elements gegen einen frischen Manager und erntet die Actions. */
    private void sammleKontext(UIElement element)
    {
        List<Consumer<ContextMenuManager>> optionen = contextOptionen(element);

        if (optionen == null)
        {
            return;
        }

        ContextMenuManager manager = new ContextMenuManager();

        try
        {
            for (Consumer<ContextMenuManager> consumer : optionen)
            {
                consumer.accept(manager);
            }
        }
        catch (Throwable t)
        {
            /* Builder duerfen vom Maus-/Auswahlzustand abhaengen — ohne echten
             * Rechtsklick darf so einer scheitern; geerntet wird, was er bis
             * dahin eingetragen hat. */
        }

        String quelle = kurzname(element);

        for (ContextAction action : manager.actions)
        {
            if (action.runnable != null && action.label != null)
            {
                /* " · " wie bei den Keybind-Zeilen — "▸" hat im Bitmap-Font kein Glyph. */
                this.alle.add(new PalettenBefehl(quelle + " · " + action.label.get(), action.runnable));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Consumer<ContextMenuManager>> contextOptionen(UIElement element)
    {
        try
        {
            if (CONTEXT_OPTIONS == null)
            {
                CONTEXT_OPTIONS = UIElement.class.getDeclaredField("contextOptions");
                CONTEXT_OPTIONS.setAccessible(true);
            }

            return (List<Consumer<ContextMenuManager>>) CONTEXT_OPTIONS.get(element);
        }
        catch (Throwable t)
        {
            REFLECTION_FEHLER = "context harvest broken: " + t;

            return null;
        }
    }

    /** Anonyme Klassen haben keinen SimpleName — dann traegt die naechste benannte Oberklasse. */
    private static String kurzname(UIElement element)
    {
        Class<?> klasse = element.getClass();

        while (klasse.getSimpleName().isEmpty() && klasse.getSuperclass() != null)
        {
            klasse = klasse.getSuperclass();
        }

        return klasse.getSimpleName().replaceFirst("^UI", "");
    }

    private static String zeileKeybind(Keybind keybind)
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

        for (PalettenBefehl befehl : this.alle)
        {
            if (klein.isEmpty() || befehl.zeile.toLowerCase().contains(klein))
            {
                this.gefiltert.add(befehl);
                this.liste.add(befehl.zeile);
            }
        }

        this.liste.update();
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

        PalettenBefehl befehl = this.gefiltert.get(index);

        if (befehl.lauf == null)
        {
            return;
        }

        this.close();
        befehl.lauf.run();
    }

    /** Blender-Menue-Suche-Bedienung: Tippen filtert live (Callback je Zeichen),
     *  Pfeile waehlen in der Liste, Enter fuehrt Auswahl (sonst ersten Treffer) aus. */
    private class SuchEingabe extends UITextbox
    {
        public SuchEingabe(int length, java.util.function.Consumer<String> callback)
        {
            super(length, callback);
        }

        @Override
        public boolean subKeyPressed(mchorse.bbs_mod.ui.framework.UIContext context)
        {
            if (this.isFocused())
            {
                UIBefehlsPalette palette = UIBefehlsPalette.this;

                if (context.isPressed(InputConstants.KEY_RETURN))
                {
                    palette.ausfuehren(Math.max(0, palette.liste.getIndex()));

                    return true;
                }

                if (context.isPressed(InputConstants.KEY_UP) || context.isPressed(InputConstants.KEY_DOWN))
                {
                    int schritt = context.isPressed(InputConstants.KEY_UP) ? -1 : 1;
                    int max = palette.gefiltert.size() - 1;

                    palette.liste.setIndex(Math.max(0, Math.min(palette.liste.getIndex() + schritt, max)));

                    return true;
                }
            }

            return super.subKeyPressed(context);
        }
    }
}
