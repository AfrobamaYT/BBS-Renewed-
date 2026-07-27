// Coded by Afrobama/Claude — memoizes the undo "after" snapshot within one submitUndo
// batch, killing the O(changes × tree) freeze on bulk replay edits (e.g. process-replay
// scatter over 20k replays = a multi-minute freeze + OutOfMemoryError).
//
// UIFormUndoHandler.submitUndo() creates ONE ValueChangeUndo per changed BaseValue, and
// each calls undo.cacheAfter(editor) → editor.getRoot().collectAllUndoData(): a FULL walk
// of the UI element tree, serializing everything (incl. the current selection — up to N
// ints — and the keyframe editor data) into a fresh MapType. A scatter of 20k replays
// changes ~40k keyframe values → ~40k full-tree serializations, each allocating a huge
// MapType. That is O(changes × treeSize) CPU + a flood of garbage = the render-thread
// freeze (jstack: ListType.addInt ← intListToData ← collectUndoData ← collectAllUndoData
// ← ValueChangeUndo.cacheAfter ← submitUndo ← UIFilmPanel.render) and the heap-exhaustion
// OOM that preceded it.
//
// Every undo in ONE submitUndo batch captures the SAME after-state (the tree state once
// all edits are applied), so the N serializations are identical. We compute it ONCE per
// batch and hand the same MapType to every ValueChangeUndo → O(treeSize). The snapshot is
// read-only on undo/redo apply, so sharing one instance across the batch's undos is safe.
//
// Data-agnostic: works for unique replays exactly as for duplicates (it's about the tree
// serialization, not the data). Fixes every bulk editor op (scatter/grid/circle/line).
package com.afrobama.bbstools.client.bbs.fastpath;

import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.ui.framework.elements.UIElement;

public final class UndoSnapshotMemo
{
    private static boolean inBatch = false;
    private static UIElement batchRoot = null;
    private static MapType cached = null;

    private UndoSnapshotMemo() {}

    /** submitUndo HEAD — open a memo window and clear any stale snapshot. */
    public static void beginBatch()
    {
        inBatch = true;
        batchRoot = null;
        cached = null;
    }

    /** submitUndo RETURN — close the window (next batch recomputes). */
    public static void endBatch()
    {
        inBatch = false;
        batchRoot = null;
        cached = null;
    }

    /** Redirected from ValueChangeUndo.cacheAfter's collectAllUndoData() call. First call
     *  in a batch computes the real snapshot; the rest reuse it. Outside a batch (defensive)
     *  it just delegates, so behaviour is unchanged for any non-batched caller. */
    public static MapType collect(UIElement root)
    {
        if (root == null)
        {
            return new MapType();
        }

        if (inBatch && cached != null && batchRoot == root)
        {
            return cached;
        }

        MapType data = root.collectAllUndoData();

        if (inBatch)
        {
            cached = data;
            batchRoot = root;
        }

        return data;
    }

}
