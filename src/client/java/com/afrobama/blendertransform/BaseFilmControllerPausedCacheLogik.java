package com.afrobama.blendertransform;

/**
 * Logik des Pausen-Cache-Handlers — im Kernel-Paket, damit sie hotswapbar ist.
 *
 * <p>Herausgelöst aus {@code BaseFilmControllerPausedCacheMixin} (2026-07-29, stehende
 * User-Order #56).</p>
 *
 * <p>Was der Handler tut: hält {@code PausedReplayBoneCache} und
 * {@code PausedReplayMatrixCache} mit dem Play/Pause-Zustand des Controllers im Takt.
 * Beim Umschalten werden beide bedingungslos verworfen — das deckt beide Richtungen ab:
 * beim Anhalten füllt der nächste Frame frische Einträge, beim Weiterspielen ist der
 * Inhalt ohnehin wertlos, weil jeder Tick ihn überholt.</p>
 *
 * <p>Der Versions-Zähler wird mitgezogen, damit ein Leser auf einem anderen Thread, der
 * gerade unterwegs ist, seine Generation als veraltet erkennt und neu rechnet, statt einen
 * halb verworfenen Eintrag zu benutzen.</p>
 *
 * <p>⚠ KEINE KERNEL-TYPEN IN DER SIGNATUR — der Handler übergibt nichts, das er nicht
 * müsste; hier reicht der reine Aufruf.</p>
 */
public final class BaseFilmControllerPausedCacheLogik
{
    private BaseFilmControllerPausedCacheLogik() {}

    public static void invalidateOnToggle()
    {
        com.afrobama.bbstools.perf.PausedReplayBoneCache.INSTANCE.invalidateAll();
        com.afrobama.bbstools.perf.PausedReplayMatrixCache.INSTANCE.reset();
    }
}
