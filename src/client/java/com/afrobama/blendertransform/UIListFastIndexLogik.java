package com.afrobama.blendertransform;

import java.util.List;

/**
 * Logik des identitäts-index-mixins für {@code UIList.setCurrent} — im kernel-paket, damit sie
 * hotswapbar ist.
 *
 * <p>Herausgelöst aus {@code UIListFastIndexMixin} (2026-07-26). BBS' {@code UIList.setCurrent}
 * ruft {@code this.list.indexOf(element)} — bei repliken-listen ist das ein tiefer
 * {@code ValueGroup.equals}-vergleich, O(N) je aufruf und der grund für den freeze bei
 * massen-operationen (siehe mixin-javadoc). Der ersatz sucht erst per referenz (billig),
 * fällt nur bei einem miss auf den echten {@code indexOf} zurück — verhalten bleibt exakt
 * gleich, nur der referenz-treffer wird beschleunigt.</p>
 *
 * <p>⚠ KEINE KERNEL-TYPEN IN DER SIGNATUR — {@code List}/{@code Object} sind JDK-typen.</p>
 */
public final class UIListFastIndexLogik
{
    private UIListFastIndexLogik() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static int fastReferenceIndexOf(List list, Object element)
    {
        for (int i = 0, n = list.size(); i < n; i++)
        {
            if (list.get(i) == element)
            {
                return i;
            }
        }

        return list.indexOf(element);
    }
}
