// Coded by Afrobama/Claude — UI Overhaul Phase 9: undo stack data layer
package com.afrobama.bbstools.client.ui.undo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bounded LIFO + redo stack for BBSTools-tracked edits. Independent
 * of BBS-CML's internal undo because that one is per-panel; we want
 * a single mod-wide history surface for the undo-overlay (Phase 9).
 *
 * <p>API:</p>
 * <ul>
 *   <li>{@link #push(BBSToolsUndoEntry)} — record a new edit (clears redo)</li>
 *   <li>{@link #undo()} — replay the last entry's undo callback + move
 *       it to the redo stack</li>
 *   <li>{@link #redo()} — replay the last redone entry's redo callback</li>
 *   <li>{@link #snapshot()} — read-only view of the stack for UI</li>
 * </ul>
 *
 * <p>Currently unused at runtime — the integration with BBS edits is
 * Phase 9b. This class lives now so we can ship the overlay UI shell
 * and have a stable API for the editing hooks later.</p>
 */
public final class BBSToolsUndoStack
{
    public static final int CAPACITY = 64;

    private static final Logger LOG = LoggerFactory.getLogger("bbstools/undo");

    private static final Deque<BBSToolsUndoEntry> UNDO = new ArrayDeque<>();
    private static final Deque<BBSToolsUndoEntry> REDO = new ArrayDeque<>();

    private BBSToolsUndoStack() {}

    public static synchronized void push(BBSToolsUndoEntry e)
    {
        if (e == null) return;
        UNDO.push(e);
        REDO.clear();
        while (UNDO.size() > CAPACITY) UNDO.removeLast();
    }

    public static synchronized boolean undo()
    {
        BBSToolsUndoEntry e = UNDO.pollFirst();
        if (e == null) return false;
        try { e.undo.run(); }
        catch (Throwable t) { LOG.warn("undo callback for '{}' threw", e.label, t); }
        REDO.push(e);
        return true;
    }

    public static synchronized boolean redo()
    {
        BBSToolsUndoEntry e = REDO.pollFirst();
        if (e == null) return false;
        try { e.redo.run(); }
        catch (Throwable t) { LOG.warn("redo callback for '{}' threw", e.label, t); }
        UNDO.push(e);
        return true;
    }

    public static synchronized List<BBSToolsUndoEntry> snapshot()
    {
        return new ArrayList<>(UNDO);
    }

    public static synchronized List<BBSToolsUndoEntry> redoSnapshot()
    {
        return new ArrayList<>(REDO);
    }

    public static synchronized void clear()
    {
        UNDO.clear();
        REDO.clear();
    }

    public static synchronized int size()
    {
        return UNDO.size();
    }

    public static synchronized int redoSize()
    {
        return REDO.size();
    }

}
