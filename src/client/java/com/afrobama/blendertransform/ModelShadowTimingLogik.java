package com.afrobama.blendertransform;

/**
 * Logik des shadow-timing-mixins — herausgelöst aus {@code ModelShadowTimingMixin}
 * (2026-07-26), damit sie hotswapbar ist.
 *
 * <p>PRO-ENTITY-PFAD (3000+/frame bei army-scale) — siehe klassen-javadoc von
 * {@link com.afrobama.bbstools.client.blender.MixinBruecke#griff}. Der handler holt sich EINMAL
 * pro frame einen {@link java.lang.invoke.MethodHandle} auf {@link #enter} bzw. {@link #exit}
 * und ruft ihn direkt — kein map-lookup pro entity.</p>
 */
public final class ModelShadowTimingLogik
{
    private ModelShadowTimingLogik() {}

    /* Ein renderShadow-aufruf nested nicht (siehe original-mixin-kommentar), deshalb reicht
     * ein einzelnes statisches feld für start-zeitstempel — kein per-instanz-zustand nötig. */
    private static long shadowStartNs = 0L;

    /**
     * HEAD von {@code renderShadow}.
     *
     * @return {@code true}, wenn der aufrufer {@code ci.cancel()} rufen soll
     *         ({@code SKIP_BLOB_SHADOW} ist an)
     */
    public static boolean enter()
    {
        if (com.afrobama.bbstools.perf.BBSPerfConfig.SKIP_BLOB_SHADOW)
        {
            com.afrobama.bbstools.perf.BBSPerfConfig.bbsShadowSkipped++;
            return true;
        }

        shadowStartNs = System.nanoTime();
        return false;
    }

    /** RETURN von {@code renderShadow}. */
    public static void exit()
    {
        com.afrobama.bbstools.perf.BBSPerfConfig.bbsShadowNsAccum += (System.nanoTime() - shadowStartNs);
        com.afrobama.bbstools.perf.BBSPerfConfig.bbsShadowCalls++;
    }
}
