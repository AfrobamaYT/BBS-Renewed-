// Coded by Afrobama/Claude — UI Overhaul Phase 9: undo entry record
package com.afrobama.bbstools.client.ui.undo;

/**
 * One reversible operation tracked by {@link BBSToolsUndoStack}.
 *
 * <p>Hand-rolled instead of using a record because we still target
 * Java 17 source level in the Fabric loom config and we want public
 * field access for the fast paths in the stack.</p>
 */
public final class BBSToolsUndoEntry
{
    public final String label;
    public final Runnable undo;
    public final Runnable redo;
    public final long timestampMs;

    public BBSToolsUndoEntry(String label, Runnable undo, Runnable redo)
    {
        this.label = label == null ? "(unnamed edit)" : label;
        this.undo = undo == null ? () -> {} : undo;
        this.redo = redo == null ? () -> {} : redo;
        this.timestampMs = System.currentTimeMillis();
    }

}
