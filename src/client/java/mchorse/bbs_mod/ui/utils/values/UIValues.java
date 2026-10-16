package mchorse.bbs_mod.ui.utils.values;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.util.function.Supplier;

/**
 * "Reset value" on a right click, for an element bound to a value: offered only while the value
 * differs from its declared default, and checked afresh on every click, since the menu is built on
 * each one. The value comes from a supplier, as a form panel outlives the form it edits. From BBS FS
 * c79e8231c, 2cc3e2d65 (Wemppy4/bbs-fs, MIT).
 */
public final class UIValues
{
    public static final IKey RESET = L10n.lang("bbs.ui.value.reset");

    private UIValues()
    {}

    public static <T extends UIElement> T resettable(T element, Supplier<? extends BaseValue> value, Runnable refresh)
    {
        element.context((menu) ->
        {
            BaseValue current = value.get();

            if (current == null || current.isDefault())
            {
                return;
            }

            menu.action(Icons.UNDO, RESET, () ->
            {
                current.reset();

                if (refresh != null) refresh.run();
            });
        });

        return element;
    }
}
