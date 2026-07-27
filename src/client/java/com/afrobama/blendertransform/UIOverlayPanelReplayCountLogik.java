package com.afrobama.blendertransform;

import com.afrobama.bbstools.client.blender.MixinBruecke;

/**
 * Logik des replay-zählers im overlay-panel-titel — im kernel-paket, damit sie hotswapbar ist.
 *
 * <p>Herausgelöst aus {@code UIOverlayPanelReplayCountMixin} (2026-07-26). Zählt die
 * nicht-gruppierten replays im film und schreibt "Replay (N)" in den titel — aber nur wenn
 * sich die zahl geändert hat (kein alloc pro frame bei stillstand).</p>
 *
 * <p>⚠ ZUSTAND: {@code bbstools$lastCount} ist ein {@code @Unique}-feld der ZIELKLASSE
 * ({@code UIOverlayPanel}), wird beim weben ein echtes instanzfeld. Es bleibt darum über
 * {@link MixinBruecke#feldLesen}/{@link MixinBruecke#feldSchreiben} auf dem ziel-objekt
 * gelesen/geschrieben — NICHT in einer kernel-globalen map, sonst hinge der zustand am
 * falschen objekt (mehrere overlay-panel-instanzen möglich).</p>
 *
 * <p>⚠ KEINE KERNEL-TYPEN IN DER SIGNATUR. {@code zielObjekt} kommt als {@code this} vom
 * handler (Object), {@code panel} als das gecastete {@code UIReplaysOverlayPanel} (BBS-typ) —
 * beide stabil-loader-typen.</p>
 */
public final class UIOverlayPanelReplayCountLogik
{
    private UIOverlayPanelReplayCountLogik() {}

    public static void replayCount(Object zielObjekt, mchorse.bbs_mod.ui.film.replays.overlays.UIReplaysOverlayPanel rp)
    {
        try
        {
            int n = 0;
            java.util.List<mchorse.bbs_mod.film.replays.Replay> list =
                rp.replays.panel.getData().replays.getList();

            if (list != null)
            {
                for (mchorse.bbs_mod.film.replays.Replay r : list)
                {
                    if (r != null && !r.isGroup.get()) n++;
                }
            }

            Object bisher = MixinBruecke.feldLesen(zielObjekt, "bbstools$lastCount");
            int letzte = bisher instanceof Integer ? (Integer) bisher : -1;

            Object titelFeld = MixinBruecke.feldLesen(zielObjekt, "title");

            if (n != letzte && titelFeld instanceof mchorse.bbs_mod.ui.framework.elements.utils.UILabel titel)
            {
                MixinBruecke.feldSchreiben(zielObjekt, "bbstools$lastCount", n);
                titel.label = mchorse.bbs_mod.l10n.keys.IKey.constant("Replay (" + n + ")");
            }
        }
        catch (Throwable ignored)
        {
            /* Siehe original: fehler beim zählen dürfen render nicht abreissen. */
        }
    }
}
