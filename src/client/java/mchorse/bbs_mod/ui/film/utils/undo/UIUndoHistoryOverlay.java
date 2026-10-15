package mchorse.bbs_mod.ui.film.utils.undo;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.ui.framework.UIContext;
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

    /** null im Modell-Editor: dort gibt es keine Docks, das Fenster schwebt nur. */
    private AndockRegler andockRegler;

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

    public UIUndoHistoryOverlay andockRegler(AndockRegler regler)
    {
        this.andockRegler = regler;

        return this;
    }

    @Override
    public boolean subMouseReleased(UIContext context)
    {
        /* VOR super lesen: super setzt das Zieh-Flag um. Der Regler merkt nur vor
         * (nie sofort umbauen — CME-Klasse dieser Codebasis); close() ist der animierte,
         * dokumentiert sichere Weg (UIOverlay.abschlussFaellig). */
        boolean zogAmTitel = this.istAmTitelZiehen();
        boolean ergebnis = super.subMouseReleased(context);

        if (zogAmTitel && this.andockRegler != null
            && this.andockRegler.zugEnde(context.mouseX, context.mouseY))
        {
            this.close();
        }

        return ergebnis;
    }

    @Override
    public void render(UIContext context)
    {
        super.render(context);

        /* Nach super: ueber der Abdunklung UND ausserhalb des Aufblend-push/pop —
         * die Miniaturschlange haengt an genau dem Paar (Commits 979f29c23/b56ab20a7). */
        if (this.andockRegler != null && this.istAmTitelZiehen())
        {
            this.andockRegler.vorschauZeichnen(context, context.mouseX, context.mouseY);
        }
    }
}
