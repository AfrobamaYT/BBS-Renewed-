package com.afrobama.blendertransform;

/**
 * Logik des animator-cache-mixins — herausgelöst aus {@code ModelFormRendererAnimatorCacheMixin}
 * (2026-07-26), damit sie hotswapbar ist.
 *
 * <p>PRO-ENTITY-PFAD (3000+/frame bei army-scale). Das {@code @Unique}-instanz-feld
 * ({@code lastResolveMs}, gebunden an EINE {@code ModelFormRenderer}-instanz) bleibt im mixin —
 * {@link com.afrobama.bbstools.client.blender.MixinBruecke#feldLesen} findet nur echte
 * {@code @Shadow}-felder der ZIELKLASSE, keine {@code @Unique}-felder des mixins selbst. Diese
 * klasse bekommt die relevanten werte als PRIMITIVE argumente und liefert die reine
 * throttle-entscheidung zurück — keine kernel-typen, keine identität über die brücke.</p>
 */
public final class ModelFormRendererAnimatorCacheLogik
{
    private ModelFormRendererAnimatorCacheLogik() {}

    /**
     * @param nowMs aktuelle zeit in ms ({@code System.currentTimeMillis()})
     * @param lastResolveMs letzter resolve-zeitpunkt (mixin-lokaler @Unique-wert)
     * @return {@code true}, wenn der aufrufer {@code ci.cancel()} rufen soll (throttle aktiv)
     */
    public static boolean throttled(long nowMs, long lastResolveMs)
    {
        if (!com.afrobama.bbstools.perf.BBSPerfConfig.ENABLE_ANIMATOR_CONFIG_CACHE) return false;

        if (nowMs - lastResolveMs < com.afrobama.bbstools.perf.BBSPerfConfig.ANIMATOR_CACHE_THROTTLE_MS)
        {
            com.afrobama.bbstools.perf.BBSPerfConfig.animatorResolveSkips++;
            return true;
        }

        return false;
    }
}
