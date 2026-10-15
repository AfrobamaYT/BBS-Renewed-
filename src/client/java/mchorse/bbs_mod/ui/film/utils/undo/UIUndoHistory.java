package mchorse.bbs_mod.ui.film.utils.undo;

import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.utils.undo.UndoManager;

import java.util.ArrayList;
import java.util.function.Supplier;

/**
 * The undo history itself — a list of an {@link UndoManager}'s stack where clicking an entry drives the
 * manager until its position reaches that entry.
 *
 * <p>This is the CONTENT, not the window: it knows nothing about where it is shown. That separation is
 * what makes the history dockable at all — {@link UIUndoHistoryOverlay} wraps it in a floating panel
 * (used by the model editor, which has no docks), while the film editor hands the very same element to
 * its dock layout as a regular panel. Established practice: Qt ADS wraps a "content widget" in a
 * CDockWidget, Godot wraps a Control in either a dock slot or a WindowWrapper.</p>
 *
 * <p>⚠ FALLE — the manager is a {@link Supplier}, never a stored reference. A docked history outlives
 * many films, and {@code UIFilmPanel.fillData} builds a BRAND NEW UIFilmUndoHandler (and therefore a new
 * UndoManager) on every film switch. A stored reference would keep showing the previous film's stack and
 * silently drive the wrong manager.</p>
 *
 * <p>⚠ FALLE — {@link mchorse.bbs_mod.ui.framework.elements.input.list.UIList#setList} keeps a LIVE
 * reference to the manager's list, so new entries appear on their own, but the scroll size and the
 * highlighted row do not. Re-sync passiert im render-Pfad, angestossen vom "Stack geaendert"-Abo
 * des Managers (P8d) — nur der Manager-WECHSEL (neuer Film) wird weiter je Frame per Referenz
 * verglichen, den meldet niemand.</p>
 */
public class UIUndoHistory extends UIElement
{
    private final Supplier<UndoManager<ValueGroup>> undoManager;
    private final UIUndoList<ValueGroup> list;

    /** Identity of the manager the list is currently bound to — a change means "different film". */
    private UndoManager<ValueGroup> boundManager;
    private int lastSize = -1;
    private int lastIndex = Integer.MIN_VALUE;

    /* P8d: statt je Frame size/index zu vergleichen, abonniert die Historie das
     * "Stack geaendert"-Ereignis des Managers. Der Listener setzt NUR das Flag —
     * UI-Arbeit bleibt im render-Pfad. Der Identitaets-Check je Frame bleibt:
     * einen Manager-WECHSEL (neuer Film) meldet niemand. */
    private boolean dirty = true;
    private final Runnable stackAbo = () -> this.dirty = true;

    /**
     * @param undoManager where the stack comes from; may return null while no data is loaded.
     * @param context     the value tree undo/redo is applied to (the film, the model config, ...).
     * @param onApplied   run after driving the manager to the picked entry. Editors whose widgets
     *                    live-track the data (the film timeline) pass null; ones with static widgets
     *                    (the model editor) pass their rebuild.
     */
    public UIUndoHistory(Supplier<UndoManager<ValueGroup>> undoManager, Supplier<ValueGroup> context, Runnable onApplied)
    {
        this.undoManager = undoManager;

        /* Callback NACH der Zuweisung setzen: er liest this.list.getIndex(), und ein Lambda
         * im Konstruktor darf ein noch nicht zugewiesenes final-Feld nicht anfassen —
         * "Variable list wurde moeglicherweise nicht initialisiert". Auf dem 1.21.1-Stand
         * des Werkbank-Zweigs war das von den Classpath-Luecken der javac-Gegenprobe
         * verdeckt; der volle Bau hier findet es. */
        this.list = new UIUndoList<ValueGroup>(null);
        this.list.callback = (l) ->
        {
            UndoManager<ValueGroup> manager = this.getUndoManager();

            if (manager == null)
            {
                return;
            }

            int index = this.list.getIndex();

            while (manager.getCurrentUndoIndex() != index)
            {
                if (manager.getCurrentUndoIndex() > index)
                {
                    manager.undo(context.get());
                }
                else
                {
                    manager.redo(context.get());
                }
            }

            if (onApplied != null)
            {
                onApplied.run();
            }

            UIUtils.playClick();
        };

        this.list.full(this);

        this.add(this.list);
    }

    public UndoManager<ValueGroup> getUndoManager()
    {
        return this.undoManager.get();
    }

    @Override
    public void render(UIContext context)
    {
        this.syncFromManager();

        super.render(context);
    }

    /**
     * Cheap per-frame reconciliation: only manager identity, stack length and position are compared, so
     * the common case (nothing changed) costs three integer reads.
     */
    private void syncFromManager()
    {
        UndoManager<ValueGroup> manager = this.getUndoManager();

        if (manager == null)
        {
            if (this.boundManager != null)
            {
                this.boundManager.removeStackListener(this.stackAbo);
                this.boundManager = null;
                this.lastSize = -1;
                this.lastIndex = Integer.MIN_VALUE;

                /* ⚠ FALLE — NOT list.clear(): UIList.clear() empties its BACKING list, and that
                 * backing list IS the manager's live undo stack. Swapping in a fresh empty list is
                 * the only way to blank the view without destroying the history. */
                this.list.setList(new ArrayList<>());
                this.list.deselect();
            }

            return;
        }

        if (manager != this.boundManager)
        {
            if (this.boundManager != null)
            {
                this.boundManager.removeStackListener(this.stackAbo);
            }

            manager.addStackListener(this.stackAbo);

            this.boundManager = manager;
            this.lastSize = -1;
            this.lastIndex = Integer.MIN_VALUE;
            this.dirty = true;

            this.list.setList(manager.getUndos());
        }

        if (!this.dirty)
        {
            return;
        }

        this.dirty = false;

        int size = manager.getTotalUndos();
        int index = manager.getCurrentUndoIndex();

        if (size != this.lastSize)
        {
            /* #66: die Liste klebt am neuesten Eintrag (Log-Verhalten) — ausser der Nutzer
             * hat bewusst hochgescrollt, dann wird nicht gerissen. "Unten" wird VOR dem
             * update gemessen, danach ist scrollSize schon der neue. Erstes Sync (-1)
             * springt immer ans Ende: wer die Historie oeffnet, will das Neueste sehen. */
            boolean warUnten = this.lastSize < 0
                || this.list.scroll.getScroll() >= this.list.scroll.scrollSize - this.list.scroll.area.h - this.list.scroll.scrollItemSize;

            this.lastSize = size;

            /* Resyncs the scroll bar to the new row count — the list itself shares the manager's
             * List instance, so the rows are already there. */
            this.list.update();

            if (warUnten)
            {
                this.list.scroll.setScroll(this.list.scroll.scrollSize);
            }
        }

        if (index != this.lastIndex)
        {
            this.lastIndex = index;

            this.list.setIndex(index);
        }
    }
}
