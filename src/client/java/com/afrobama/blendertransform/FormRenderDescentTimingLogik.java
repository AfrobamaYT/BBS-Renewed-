package com.afrobama.blendertransform;

/**
 * Logik des descent-timing-mixins — herausgelöst aus {@code FormRenderDescentTimingMixin}
 * (2026-07-26), damit sie hotswapbar ist.
 *
 * <p>PRO-ENTITY-PFAD (3000+/frame bei army-scale). Die rekursions-tiefe selbst bleibt im mixin
 * (rein lokaler int-zähler, keine fachlogik — siehe kommentar dort); nur der nanoTime/accum-teil
 * liegt hier.</p>
 */
public final class FormRenderDescentTimingLogik
{
    private FormRenderDescentTimingLogik() {}

    /** @return start-zeitstempel für das äusserste (per-replay) fenster */
    public static long enter()
    {
        return System.nanoTime();
    }

    public static void exit(long startNs)
    {
        com.afrobama.bbstools.perf.BBSPerfConfig.bbsDescentNsAccum += (System.nanoTime() - startNs);
        com.afrobama.bbstools.perf.BBSPerfConfig.bbsDescentCalls++;
    }
}
