package com.afrobama.blendertransform;

import mchorse.bbs_mod.film.replays.Replay;

import java.util.List;

/**
 * Logik des current-entity-fast-mixins — herausgelöst aus
 * {@code UIFilmControllerCurrentEntityFastMixin} (2026-07-26), damit sie hotswapbar ist.
 *
 * <p>PRO-ENTITY-PFAD (3000+/frame bei army-scale, siehe klassen-header des mixins). Das
 * {@code @Unique}-instanz-cache ({@code lastReplay/lastIndex/lastSize}, an EINE
 * {@code UIFilmController}-instanz gebunden) bleibt im mixin — {@code feldLesen} findet nur
 * echte {@code @Shadow}-felder der zielklasse. Diese methode bekommt den cache-stand als
 * primitive/Object-argumente und liefert den aufgelösten index zurück; der aufrufer schreibt
 * ihn danach IMMER in seine uniques zurück (bei einem cache-hit ist das ein no-op-overwrite
 * derselben werte — siehe mixin-kommentar).</p>
 *
 * <p>{@code Replay}/{@code List} sind BBS/JDK-typen, keine kernel-typen — laufzeit-identität
 * bleibt loader-stabil (siehe {@link com.afrobama.bbstools.client.blender.MixinBruecke}).</p>
 */
public final class UIFilmControllerCurrentEntityFastLogik
{
    private UIFilmControllerCurrentEntityFastLogik() {}

    /**
     * @param list          die replay-liste (aufrufer: {@code List<Replay>})
     * @param replay        der gesuchte replay
     * @param cachedReplay  {@code this.bbstools$lastReplay} des aufrufers
     * @param cachedIdx     {@code this.bbstools$lastIndex}
     * @param cachedSize    {@code this.bbstools$lastSize}
     * @return aufgelöster index (identisch mit {@code list.indexOf(replay)})
     */
    @SuppressWarnings("unchecked")
    public static int resolveIndex(List<?> list, Object replay,
                                    Object cachedReplay, int cachedIdx, int cachedSize)
    {
        if (replay instanceof Replay r)
        {
            com.afrobama.bbstools.perf.BBSPerfConfig.currentReplay = r;
            try { com.afrobama.bbstools.perf.BBSPerfConfig.currentReplayUuid = r.uuid.get(); }
            catch (Throwable ignored) {}
        }

        if (!com.afrobama.bbstools.perf.BBSPerfConfig.ENABLE_CURRENT_ENTITY_CACHE)
        {
            return list.indexOf(replay);
        }

        int size = list.size();

        /* Cache hit: same current replay, list unchanged, still at the cached index. */
        if (replay == cachedReplay
            && size == cachedSize
            && cachedIdx >= 0 && cachedIdx < size
            && list.get(cachedIdx) == replay)
        {
            com.afrobama.bbstools.perf.BBSPerfConfig.currentEntityCacheHits++;
            return cachedIdx;
        }

        /* Miss: identity scan (== — skips the deep ValueGroup.equals storm). */
        int idx = -1;
        for (int i = 0; i < size; i++)
        {
            if (list.get(i) == replay) { idx = i; break; }
        }
        if (idx < 0)
        {
            /* Not present by identity (shouldn't happen) → fall back to deep-equals indexOf. */
            idx = list.indexOf(replay);
        }

        com.afrobama.bbstools.perf.BBSPerfConfig.currentEntityCacheMisses++;
        return idx;
    }

    /** RETURN-inject: publiziert die aufgelöste entity. */
    public static void publishCurrentEntity(Object resolvedEntity)
    {
        com.afrobama.bbstools.perf.BBSPerfConfig.currentEntityRef = resolvedEntity;
    }
}
