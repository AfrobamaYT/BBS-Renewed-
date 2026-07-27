package com.afrobama.blendertransform;

import java.util.List;

/**
 * Logik des identitäts-index-mixins für {@code UIReplaysEditor.collectUndoData} — im
 * kernel-paket, damit sie hotswapbar ist.
 *
 * <p>Herausgelöst aus {@code UIReplaysEditorUndoFastIndexMixin} (2026-07-26). Der gesuchte
 * replay ist dasselbe objekt, das schon in der liste steckt — ein referenz-scan findet ihn
 * ohne den teuren tiefen {@code ValueGroup.equals}-vergleich, den {@code List.indexOf} sonst
 * macht (siehe mixin-javadoc: O(N²) bei massen-scatter). Verhalten bleibt identisch zu
 * {@code indexOf}: gleicher index, {@code -1} wenn nicht gefunden.</p>
 *
 * <p>⚠ KEINE KERNEL-TYPEN IN DER SIGNATUR — {@code List}/{@code Object} sind JDK-typen.</p>
 */
public final class UIReplaysEditorUndoFastIndexLogik
{
    private UIReplaysEditorUndoFastIndexLogik() {}

    @SuppressWarnings("rawtypes")
    public static int fastReferenceIndexOf(List list, Object self)
    {
        for (int i = 0, n = list.size(); i < n; i++)
        {
            if (list.get(i) == self)
            {
                return i;
            }
        }

        return -1;
    }
}
