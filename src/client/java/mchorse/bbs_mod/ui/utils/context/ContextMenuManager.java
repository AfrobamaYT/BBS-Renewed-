package mchorse.bbs_mod.ui.utils.context;

import com.mojang.blaze3d.platform.InputConstants;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.context.UISimpleContextMenu;
import mchorse.bbs_mod.ui.framework.elements.events.UIRemovedEvent;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;
import mchorse.bbs_mod.ui.utils.keys.Keybind;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

public class ContextMenuManager
{
    public List<ContextAction> actions = new ArrayList<>();
    public Consumer<UIRemovedEvent> onClose;
    public boolean autoKeys;
    public UISimpleContextMenu menu;

    private IKey category = UIKeys.CONTEXT_MENU_KEY_CATEGORY;

    public ContextMenuManager custom(UISimpleContextMenu menu)
    {
        this.menu = menu;

        return this;
    }

    public ContextMenuManager onClose(Consumer<UIRemovedEvent> onClose)
    {
        this.onClose = onClose;

        return this;
    }

    public ContextMenuManager autoKeys(IKey category)
    {
        this.category = category;

        return this.autoKeys();
    }

    public ContextMenuManager autoKeys()
    {
        this.autoKeys = true;

        return this;
    }

    private boolean trennerFaellig;

    public ContextAction action(IKey label, Runnable runnable)
    {
        return this.action(Icons.NONE, label, runnable);
    }

    public ContextAction action(Icon icon, IKey label, Runnable runnable)
    {
        if (icon == null || label == null)
        {
            throw new IllegalStateException("Icon (" + icon + ") and/or label (" + label + ") is null!");
        }

        return this.action(new ContextAction(icon, label, runnable));
    }

    public ContextAction action(Icon icon, IKey label, boolean hightlight, Runnable runnable)
    {
        return this.action(icon, label, hightlight ? BBSSettings.primaryColor(0) : 0, runnable);
    }

    public ContextAction action(Icon icon, IKey label, int color, Runnable runnable)
    {
        if (color == 0)
        {
            return action(icon, label, runnable);
        }

        if (icon == null || label == null)
        {
            throw new IllegalStateException("Icon (" + icon + ") and/or label (" + label + ") is null!");
        }

        return this.action(new ColorfulContextAction(icon, label, runnable, color));
    }

    public ContextAction action(ContextAction action)
    {
        if (this.trennerFaellig)
        {
            action.trenner();
            this.trennerFaellig = false;
        }

        this.actions.add(action);

        return action;
    }

    /** Gruppentitel wie im Entwurf („CLIP") — mit Trennfuge, wenn schon Eintraege stehen. */
    public ContextAction gruppe(IKey label)
    {
        MenueGruppe g = new MenueGruppe(label);

        if (!this.actions.isEmpty())
        {
            g.trenner();
        }

        return this.action(g);
    }

    /** Trennfuge vor dem NAECHSTEN Eintrag — eine 1-px-Linie, keine leere Zeile. */
    public ContextMenuManager trenner()
    {
        this.trennerFaellig = true;

        return this;
    }

    public UISimpleContextMenu create()
    {
        UISimpleContextMenu contextMenu = this.menu == null ? new UISimpleContextMenu() : this.menu;

        this.actions.sort(Comparator.comparingInt((a) -> a.order));

        contextMenu.actions.add(this.actions);
        contextMenu.getEvents().register(UIRemovedEvent.class, this.onClose);

        for (int i = 0; i < this.actions.size(); i++)
        {
            ContextAction action = this.actions.get(i);

            if (action.keys != null)
            {
                Keybind register = contextMenu.keys().register(new KeyCombo(action.label, action.keys), () ->
                {
                    if (action.runnable != null)
                    {
                        action.runnable.run();
                    }

                    contextMenu.removeFromParent();
                });

                if (action.keyCategory != null)
                {
                    register.category(action.keyCategory);
                }
            }
            else if (this.autoKeys && i < 30)
            {
                IKey label = UIKeys.CONTEXT_MENU_KEY.format(action.label);
                int mod = i % 10;
                int key = i == 9 ? InputConstants.KEY_0 : InputConstants.KEY_1 + mod;

                KeyCombo combo;

                if (i >= 20)
                {
                    combo = new KeyCombo(label, key, InputConstants.KEY_LCONTROL);
                }
                else if (i >= 10)
                {
                    combo = new KeyCombo(label, key, InputConstants.KEY_LSHIFT);
                }
                else
                {
                    combo = new KeyCombo(label, key);
                }

                contextMenu.keys().register(combo, () ->
                {
                    if (action.runnable != null)
                    {
                        action.runnable.run();
                    }

                    contextMenu.removeFromParent();
                }).category(this.category);
            }
        }

        return contextMenu.isEmpty() ? null : contextMenu;
    }
}
