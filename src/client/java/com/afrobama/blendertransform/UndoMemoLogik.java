package com.afrobama.blendertransform;

import com.afrobama.bbstools.client.bbs.fastpath.UndoSnapshotMemo;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.ui.film.utils.undo.ValueChangeUndo;
import mchorse.bbs_mod.ui.framework.elements.UIElement;

/**
 * Logik hinter {@code ValueChangeUndoMemoMixin}, herausgeloest damit sie hotswapbar ist.
 *
 * <p>Ersetzt BBS' {@code cacheAfter} durch eine gemerkte Aufnahme: statt den ganzen
 * Oberflaechenbaum erneut abzuschreiten, liefert {@link UndoSnapshotMemo} den Stand aus
 * seinem Zwischenspeicher.</p>
 *
 * <p>⚠️ DER HANDLER CANCELT DEN ORIGINALRUMPF. Er ersetzt ihn also, statt ihn zu ergaenzen —
 * wer hier etwas weglaesst, verliert die Undo-Aufnahme ganz. Deshalb setzt diese Methode
 * {@code uiAfter} IMMER, auch wenn kein Wurzelelement da ist (dann auf eine leere
 * {@link MapType}); ein {@code null} dort waere ein Undo-Schritt, der beim Zurueckgehen
 * nichts wiederherstellt.</p>
 */
public final class UndoMemoLogik
{
    private UndoMemoLogik() {}

    /**
     * @param undo   das {@code ValueChangeUndo}; kommt als Argument, weil der Cast den
     *               gewobenen {@code this} braucht
     * @param editor das Bedienelement, aus dessen Wurzel die Aufnahme entsteht
     */
    public static void aufnehmen(Object undo, UIElement editor)
    {
        if (!(undo instanceof ValueChangeUndo selbst) || editor == null)
        {
            return;
        }

        selbst.uiAfter = (editor.getRoot() == null)
            ? new MapType()
            : UndoSnapshotMemo.collect(editor.getRoot());
    }
}
