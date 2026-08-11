package mchorse.bbs_mod.ui.film.utils.undo;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.undo.CompoundUndo;
import mchorse.bbs_mod.utils.undo.IUndo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class UIUndoList <T> extends UIList<IUndo<T>>
{
    public UIUndoList(Consumer<List<IUndo<T>>> callback)
    {
        super(callback);

        this.background();
        this.tooltip(IKey.EMPTY);
    }

    @Override
    protected String elementToString(UIContext context, int i, IUndo<T> element)
    {
        /* Menschenlesbar statt Rohpfad (Akte 05) — der Uebersetzer faellt bei unbekannten
         * Mustern auf die letzten Pfadglieder zurueck. Die Liste traegt IUndo<T>, gefuellt
         * wird sie in beiden Huellen ausschliesslich mit IUndo<ValueGroup>. */
        if (element instanceof ValueChangeUndo || element instanceof CompoundUndo)
        {
            return UndoBeschriftung.beschrifte((IUndo) element);
        }

        return super.elementToString(context, i, element);
    }

    @Override
    public void renderTooltip(UIContext context, Area area)
    {
        int index = this.getHoveredIndex(context);
        IUndo<T> safe = CollectionUtils.getSafe(this.getList(), index);

        if (safe != null)
        {
            String label = this.elementToString(context, index, safe);

            context.batcher.textCard(label, context.mouseX + 5, context.mouseY + 5);
        }
    }
}