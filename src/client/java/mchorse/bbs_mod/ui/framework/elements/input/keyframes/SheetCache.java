package mchorse.bbs_mod.ui.framework.elements.input.keyframes;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.utils.Pair;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Der Zustand der Spuren mit Auswahl, festgehalten vor einer Änderung — damit sich der
 * Rückgängig-Stapel den Vorher-Stand merken kann.
 *
 * <p>Gesichert werden nur Spuren, in denen etwas ausgewählt ist: nur die können sich durch
 * die anstehende Bearbeitung ändern, und bei Filmen mit hunderten Spuren wäre alles andere
 * Verschwendung.</p>
 */
class SheetCache
{
    public List<Pair<BaseType, UIKeyframeSheet>> data = new ArrayList<>();

    public SheetCache(Collection<UIKeyframeSheet> sheets)
    {
        for (UIKeyframeSheet sheet : sheets)
        {
            if (sheet.selection.hasAny())
            {
                this.data.add(new Pair<>(sheet.channel.toData(), sheet));
            }
        }
    }
}
