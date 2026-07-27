// Coded by Afrobama/Claude — BBSTools-owned per-Replay crowd-mode flag.
//
// WHY THIS EXISTS
// ---------------
// Crowd Mode used to be a `Replay.crowdMode` ValueBoolean that lived on
// the BBS-CML `Replay` class — a BBSTools source-patch on the old fork.
// The upstream UI/UX revamp rebuilt `Replay` and dropped that field
// (it was never an upstream API). Rather than re-patch the BBS source
// (which would re-break on every rebase), the crowd-mode flag is now
// owned entirely by BBSTools and keyed per-Replay here.
//
// SEMANTICS
// ---------
// crowd-mode is a per-replay boolean. We key it on Replay identity via a
// WeakHashMap so a replay that gets garbage-collected (film unloaded,
// replay deleted) drops out of the set automatically — no leak, no need
// to track film lifecycle. Identity (==) is the correct key: two
// distinct Replay objects with equal data are still distinct replays.
//
// Because WeakHashMap is NOT thread-safe and crowd-mode is read from the
// render thread (BaseFilmControllerVeilMixin) while toggled on the
// client/UI thread, every access is synchronized on the map.
//
// This is a drop-in replacement for the old `replay.crowdMode.get()` /
// `replay.crowdMode.set(b)` calls:
//   replay.crowdMode.get()   ->  CrowdModeRegistry.isCrowdMode(replay)
//   replay.crowdMode.set(b)  ->  CrowdModeRegistry.setCrowdMode(replay, b)
package com.afrobama.bbstools.client.util;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import mchorse.bbs_mod.film.replays.Replay;

public final class CrowdModeRegistry
{
    /** Replay-identity -> crowd-mode flag. Weak keys so GC'd replays
     *  fall out automatically. Synchronized wrapper because reads happen
     *  on the render thread and writes on the UI thread. */
    private static final Map<Replay, Boolean> FLAGS =
        Collections.synchronizedMap(new WeakHashMap<>());

    private CrowdModeRegistry() {}

    /** True if this replay is marked crowd-mode (visual-only, GPU
     *  instanced). Default false for any replay never toggled. */
    public static boolean isCrowdMode(Replay replay)
    {
        if (replay == null) return false;
        Boolean v = FLAGS.get(replay);
        return v != null && v;
    }

    /** Set / clear the crowd-mode flag for a single replay. Storing
     *  false removes the entry (keeps the map small and means "default"
     *  and "explicitly off" are indistinguishable, which matches the old
     *  ValueBoolean default-false behaviour). */
    public static void setCrowdMode(Replay replay, boolean on)
    {
        if (replay == null) return;

        if (on)
        {
            FLAGS.put(replay, Boolean.TRUE);
        }
        else
        {
            FLAGS.remove(replay);
        }
    }

}
