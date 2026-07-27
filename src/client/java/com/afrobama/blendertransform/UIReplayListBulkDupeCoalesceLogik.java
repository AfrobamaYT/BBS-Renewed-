package com.afrobama.blendertransform;

import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.ui.film.replays.UIReplayList;

import java.util.UUID;

/**
 * Logik des bulk-dupe-coalesce-mixins — im kernel-paket, damit sie hotswapbar ist.
 *
 * <p>Herausgelöst aus {@code UIReplayListBulkDupeCoalesceMixin} (2026-07-26). Ersetzt BBS'
 * {@code dupeReplay()} (das PRO aufruf einen O(N) UI-rebuild macht) durch reine
 * daten-duplikation der aktuellen auswahl plus EINEN aufgeschobenen UI-flush — siehe
 * mixin-javadoc für die vollständige begründung (fan == chain).</p>
 *
 * <p>{@code UIReplayList} und {@code BulkDupeFlush} sind BBS-/mod-typen im stabilen loader,
 * keine kernel-typen — als parameter/rückgabewert erlaubt.</p>
 *
 * @return {@code true}, wenn der aufrufende handler {@code ci.cancel()} rufen soll (immer,
 *         außer im vanilla-fallback-fall ohne panel/film).
 */
public final class UIReplayListBulkDupeCoalesceLogik
{
    private UIReplayListBulkDupeCoalesceLogik() {}

    public static boolean batchDupe(UIReplayList self)
    {
        if (self.panel == null)
        {
            return false; /* vanilla den seltenen no-panel-fall selbst behandeln lassen */
        }

        Film film = self.panel.getData();
        if (film == null)
        {
            return false;
        }

        /* Reine daten-duplikation der aktuellen auswahl — identisch zu vanilla dupeReplay's
         * erzeugungs-schleife, aber OHNE den UI-rebuild pro aufruf. */
        Replay last = null;
        for (Replay replay : self.getSelectedReplays())
        {
            Replay newReplay = film.replays.addReplay();
            newReplay.copy(replay);
            newReplay.uuid.set(UUID.randomUUID().toString());
            last = newReplay;
        }

        if (last != null)
        {
            /* Ein aufgeschobener UI-refresh (nächster client-tick) statt einer pro aufruf. */
            com.afrobama.bbstools.client.bbs.fastpath.BulkDupeFlush.request(self, last);
        }

        return true;
    }
}
