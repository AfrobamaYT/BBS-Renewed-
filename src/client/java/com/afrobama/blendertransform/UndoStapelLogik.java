package com.afrobama.blendertransform;

import com.afrobama.bbstools.client.bbs.fastpath.UndoSnapshotMemo;

/**
 * Logik der Undo-Stapel-Klammer — im Kernel-Paket, damit sie hotswapbar ist.
 *
 * <p>Herausgelöst aus {@code UIFormUndoHandlerSubmitUndoMixin} (2026-07-29,
 * Brücken-Nachrüstung). Der Handler klammert {@code submitUndo()}, damit pro Stapel genau
 * ein Schnappschuss-Memo geöffnet und wieder geschlossen wird.</p>
 *
 * <p>⚠ Keine Argumente, keine Rückgabe — es gibt hier nichts, das einen Typ über die Brücke
 * tragen müsste, und das ist die einfachste Form, in der ein Handler swapbar wird.</p>
 */
public final class UndoStapelLogik
{
    private UndoStapelLogik()
    {
    }

    public static void beginne()
    {
        UndoSnapshotMemo.beginBatch();
    }

    public static void beende()
    {
        UndoSnapshotMemo.endBatch();
    }
}
