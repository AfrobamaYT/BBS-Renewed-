// Coded by Afrobama/Claude — duck-typed accessor so a client-tick driver can
// drive the chunked entity creation on a BaseFilmController without re-entering
// the UI createEntities path. BaseFilmControllerIncrementalEntitiesMixin implements
// this; BackgroundBaker.tick casts the active controller to it and drains a budget
// of new entities per tick — turning a bulk duplicate (e.g. +3000 replays = 3000
// synchronous FormUtils.copy of a Gecko-animated form = multi-minute freeze) into
// a smooth pop-in over a few seconds with the render thread staying responsive.
package com.afrobama.bbstools.client.bbs.fastpath;

public interface IIncrementalEntities
{
    /** True while enabled replays still lack their IEntity (creation incomplete). */
    boolean bbstools$entitiesPending();

    /** Create up to a time-budget of the still-missing entities (FormUtils.copy +
     *  keyframes.apply). Cheap reuse for the rest. Call once per client tick while
     *  {@link #bbstools$entitiesPending()} is true. */
    void bbstools$drainEntityCreation();

    /** Drive once per client tick. Resets the per-tick coalesce guard, then flushes
     *  any coalesced (burst) createEntities request and/or drains a budget of still-
     *  pending entity creation. Returns true while the crowd is still forming (caller
     *  should skip baking this tick). This is what makes a bulk duplicate — which
     *  calls createEntities N times back-to-back in ONE synchronous frame (O(N^2),
     *  no frame boundary to drain across) — collapse to a single rebuild per tick. */
    boolean bbstools$onClientTick();
}
