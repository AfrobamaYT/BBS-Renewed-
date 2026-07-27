package com.afrobama.blendertransform;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import com.afrobama.bbstools.perf.BBSPerfConfig;

/**
 * Logik des diagnose-timers für {@code FilmEditorController.updateEntities} — im kernel-paket,
 * damit sie hotswapbar ist.
 *
 * <p>Herausgelöst aus {@code FilmEditorControllerUpdateTimerMixin} (2026-07-26). Reine messung,
 * kein verhaltens-einfluss — siehe die begründung im original-mixin (RETURN feuert nicht, wenn
 * der throttle-mixin bereits abbricht; ein &gt;50ms-delta wird deshalb als leck verworfen).</p>
 *
 * <p>Der {@code @Unique}-zustand ({@code bbstools$updTimerT0}) hängt an der ZIELINSTANZ (mehrere
 * film-editor-controller dürfen sich nicht denselben start-zeitstempel teilen) — deshalb über
 * {@link MixinBruecke#feldLesen}/{@link MixinBruecke#feldSchreiben} auf {@code ziel}.</p>
 */
public final class UpdateTimerLogik
{
    private UpdateTimerLogik() {}

    private static final String FELD_T0 = "bbstools$updTimerT0";

    public static void head(Object ziel)
    {
        MixinBruecke.feldSchreiben(ziel, FELD_T0, System.nanoTime());
    }

    public static void rueckkehr(Object ziel)
    {
        Object gespeichert = MixinBruecke.feldLesen(ziel, FELD_T0);
        long t0 = gespeichert instanceof Long ? (Long) gespeichert : 0L;

        MixinBruecke.feldSchreiben(ziel, FELD_T0, 0L);

        if (t0 <= 0L) return;

        long delta = System.nanoTime() - t0;
        if (delta < 0L || delta > 50_000_000L) return; /* leck-schutz (parked cancel) */

        BBSPerfConfig.updateEntitiesNsAccum += delta;
        BBSPerfConfig.updateEntitiesFrames++;
    }
}
