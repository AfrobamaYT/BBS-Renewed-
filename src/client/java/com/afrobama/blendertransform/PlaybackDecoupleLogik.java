package com.afrobama.blendertransform;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import com.afrobama.bbstools.perf.BBSPerfConfig;

/**
 * Logik des playback-decouple-mixins — im kernel-paket, damit sie hotswapbar ist.
 *
 * <p>Herausgelöst aus {@code BaseFilmControllerPlaybackDecoupleMixin} (2026-07-26). Der handler
 * dort ist jetzt ein einzeiler, der hierher delegiert.</p>
 *
 * <p>Der {@code @Unique}-zustand ({@code bbstools$lastUpdateFrameId}) bleibt PRO ZIELINSTANZ
 * bestehen — er wird beim weben zu einem echten instanzfeld von {@code BaseFilmController}, also
 * über {@link MixinBruecke#feldLesen}/{@link MixinBruecke#feldSchreiben} auf {@code ziel}
 * gelesen/geschrieben, NICHT in einer kernel-globalen variable (sonst würden mehrere
 * film-controller sich den zustand teilen).</p>
 *
 * <p>⚠ KEINE KERNEL-TYPEN IN DER SIGNATUR — {@code ziel} bleibt {@code Object}.</p>
 */
public final class PlaybackDecoupleLogik
{
    private PlaybackDecoupleLogik() {}

    private static final String FELD_LAST_FRAME_ID = "bbstools$lastUpdateFrameId";

    /**
     * Coalesce-gate: läuft {@code updateEntities} schon für dieses render-frame, wird der
     * catch-up-tick verworfen (dessen state wird eh vor dem nächsten render überschrieben).
     *
     * @return {@code true}, wenn der handler den original-rumpf abbrechen soll
     */
    public static boolean coalescePerFrame(Object ziel)
    {
        if (!BBSPerfConfig.ENABLE_PLAYBACK_SIM_DECOUPLE)
        {
            return false;
        }

        long fid = BBSPerfConfig.bbstoolsRenderFrameId;

        Object gespeichert = MixinBruecke.feldLesen(ziel, FELD_LAST_FRAME_ID);
        long letzte = gespeichert instanceof Long ? (Long) gespeichert : -1L;

        /* Schon für dieses gerenderte frame simuliert → catch-up-tick, dessen zustand vor dem
         * nächsten render ohnehin überschrieben würde. Überspringen. */
        if (fid == letzte)
        {
            BBSPerfConfig.playbackTickSkips++;
            return true;
        }

        MixinBruecke.feldSchreiben(ziel, FELD_LAST_FRAME_ID, fid);

        return false;
    }
}
