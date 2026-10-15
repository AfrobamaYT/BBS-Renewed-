// Coded by Afrobama/Claude — one-shot deferred UI flush for batched bulk duplicate.
//
// The duplicate-count overlay runs `for (i<n) dupeReplay()`. Vanilla dupeReplay does,
// PER iteration, buildVisualList() (O(N)) + setCurrentDirect() + setReplay() +
// updateFilmEditor() — O(N) UI rebuilds looped N times = O(N^2) = a 1-4 minute freeze
// at n=8999. UIReplayListBulkDupeCoalesceMixin cancels that per-iter UI and does only
// the data creation (addReplay + Replay.copy), then registers ONE pending flush here.
// The next client tick rebuilds the visible list + selection + entities exactly ONCE.
//
// (The per-iter buildVisualList is NOT skippable on its own — it's what makes the new
// replay selectable for vanilla's chain; skipping it broke the chain so only ~2-3
// replays were created. Batching the whole tail is the correct fix: dupe the original
// selection n times (fan), then refresh UI once.)
package com.afrobama.bbstools.client.bbs.fastpath;

import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.ui.film.replays.UIReplayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BulkDupeFlush
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/bulk-dupe");

    private static volatile UIReplayList pendingList = null;
    private static volatile Replay pendingLast = null;

    private BulkDupeFlush() {}

    /** Called by the mixin after a (data-only) dupe — schedules the single UI refresh. */
    public static void request(UIReplayList list, Replay last)
    {
        pendingList = list;
        pendingLast = last;
    }

    /** Run once per client tick (from BackgroundBaker). If a bulk dupe is pending,
     *  rebuild the visible list + selection + entities ONCE — the same tail vanilla
     *  dupeReplay runs per iteration, but collapsed to a single O(N) pass. */
    public static void flush()
    {
        UIReplayList list = pendingList;
        Replay last = pendingLast;
        if (list == null) return;

        pendingList = null;
        pendingLast = null;

        try
        {
            list.refreshReplayList();
            if (last != null)
            {
                /* AUTONOMIE 2026-07-27: fs' liste haelt ReplayListEntry (replay ODER ordner) und
                     * verwaltet die auswahl selbst — setCurrentDirect(Replay) gibt es nicht mehr.
                     * scrollToReplay waehlt den eintrag aus und scrollt ihn sichtbar. */
                    list.scrollToReplay(last);
                if (list.panel != null)
                {
                    if (list.panel.replayEditor != null) list.panel.replayEditor.setReplay(last);
                    if (list.panel.getController() != null) list.panel.getController().createEntities();
                    if (list.panel.replayEditor != null) list.panel.replayEditor.updateChannelsList();
                }
            }
        }
        catch (Throwable t)
        {
            LOG.warn("[bulk-dupe] flush threw: {}", t.toString(), t);
        }
    }

}
