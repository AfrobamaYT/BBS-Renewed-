package mchorse.bbs_mod.ui.film.utils.undo;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.utils.undo.UndoManager;

import java.util.function.Supplier;

/**
 * The floating window around an {@link UIUndoHistory} — a title bar, a close button, and nothing else.
 *
 * <p>This class used to BE the history; it is now only one of its two hulls. The other hull is the film
 * editor's dock layout, which hosts the same {@link UIUndoHistory} element directly. The model editor has
 * no dock system, so this floating hull stays its only way to show the history.</p>
 */
public class UIUndoHistoryOverlay extends UIOverlayPanel
{
    private final UIUndoHistory history;

    public UIUndoHistoryOverlay(IKey title, UndoManager<ValueGroup> undoManager, Supplier<ValueGroup> context, Runnable onApplied)
    {
        /* The caller hands over one fixed manager; the history re-reads it every frame, which is
         * harmless here and required for the docked case (see UIUndoHistory). */
        this(title, new UIUndoHistory(() -> undoManager, context, onApplied));
    }

    public UIUndoHistoryOverlay(IKey title, UIUndoHistory history)
    {
        super(title);

        this.history = history;

        this.history.full(this.content);

        this.content.add(this.history);
    }

    public UIUndoHistory getHistory()
    {
        return this.history;
    }
}
