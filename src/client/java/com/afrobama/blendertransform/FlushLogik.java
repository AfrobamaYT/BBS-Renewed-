package com.afrobama.blendertransform;

/**
 * Logik des flush-mixins — im kernel-paket, damit sie hotswapbar ist.
 *
 * <p>Herausgelöst aus {@code BaseFilmControllerFlushMixin} (2026-07-26). Der handler dort ist
 * jetzt ein einzeiler, der hierher delegiert; dieser code kann bei jedem kernel-swap getauscht
 * werden, ohne dass die zielklasse neu gewoben werden muss.</p>
 *
 * <p>ERSTER PRODUKTIVFALL des brücken-musters — vorher war es nur an einer eigens gebauten
 * probe gezeigt. Perf-relevant: {@code flush} läuft einmal pro frame, der brücken-aufruf
 * kostet einen map-lookup plus invoke.</p>
 *
 * <p>⚠ KEINE KERNEL-TYPEN IN DER SIGNATUR. Der aufrufer ist der gewobene handler im stabilen
 * loader; ein kernel-typ wäre für ihn eine andere klasse. Hier sind alle parameter primitiv
 * und die aufgerufenen BBS-klassen liegen im stabilen loader.</p>
 */
public final class FlushLogik
{
    private FlushLogik() {}

    /**
     * Schliesst das render-loop-fenster und leert den fast-path-batch.
     *
     * <p>Das fenster ist {@code render()}-HEAD bis jetzt: BBS' serielle vorarbeit pro replay
     * (matrizen, licht-lookups, FormRenderingContext-allokation, FormUtilsClient-abstieg).</p>
     *
     * <p>Die {@code Throwable}-klammer um den flush ist absichtlich: eine ausnahme dort darf
     * BBS' normalen render-frame-lebenszyklus nicht abreissen. Der HEAD-inject sammelt weiter,
     * und die zähler zeigen die regression.</p>
     */
    public static void flush()
    {
        long flushStart = System.nanoTime();
        long head = com.afrobama.bbstools.perf.BBSPerfConfig.bbsRenderHeadNs;

        if (head != 0L)
        {
            com.afrobama.bbstools.perf.BBSPerfConfig.bbsRenderLoopNsAccum += (flushStart - head);
            com.afrobama.bbstools.perf.BBSPerfConfig.bbsRenderLoopFrames++;
        }

        try
        {
            com.afrobama.bbstools.client.bbs.fastpath.FastPathBatch.flush();
        }
        catch (Throwable ig)
        {
            /* Siehe javadoc — bewusst geschluckt. */
        }

        com.afrobama.bbstools.perf.BBSPerfConfig.bbsFlushNsAccum += (System.nanoTime() - flushStart);
    }
}
