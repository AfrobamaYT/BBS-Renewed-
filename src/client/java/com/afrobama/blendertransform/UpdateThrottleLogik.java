package com.afrobama.blendertransform;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import com.afrobama.bbstools.perf.BBSPerfConfig;

/**
 * Logik des Army-System editor-update-throttles — im kernel-paket, damit sie hotswapbar ist.
 *
 * <p>Herausgelöst aus {@code FilmEditorControllerUpdateThrottleMixin} (2026-07-26). Der handler
 * dort ist jetzt ein einzeiler, der hierher delegiert.</p>
 *
 * <p>ZUSTAND: {@code bbstools$lastAppliedTick} und {@code bbstools$lastApplyMs} sind
 * {@code @Unique}-felder der ZIELKLASSE ({@code FilmEditorController}) und werden pro
 * ZIELINSTANZ über {@link MixinBruecke#feldLesen}/{@link MixinBruecke#feldSchreiben} auf
 * {@code ziel} gehalten — nicht kernel-global, sonst würden mehrere editor-controller sich den
 * throttle-zustand teilen.</p>
 *
 * <p>@Shadow-ZUGRIFF: das original las {@code this.controller} (feld) und
 * {@code this.getTick()} (methode) direkt. Beides läuft hier über die brücke: das feld über
 * {@link MixinBruecke#feldLesen}, die methode reflektiv über den zielobjekt-typ (kein
 * kernel-typ in der signatur nötig, da wir nur {@code Object} durchreichen und die methode per
 * name aufrufen). {@code controller.isPlaying()} ist eine methode auf einem BBS-typ
 * ({@code UIFilmController}), also ebenfalls reflektiv aufgerufen statt gecastet.</p>
 */
public final class UpdateThrottleLogik
{
    private UpdateThrottleLogik() {}

    private static final String FELD_LAST_TICK = "bbstools$lastAppliedTick";
    private static final String FELD_LAST_APPLY_MS = "bbstools$lastApplyMs";

    /**
     * @return {@code true}, wenn der handler {@code updateEntities} abbrechen soll
     */
    public static boolean throttleUpdate(Object ziel)
    {
        if (!BBSPerfConfig.ENABLE_EDITOR_UPDATE_THROTTLE) return false;

        Object controller = MixinBruecke.feldLesen(ziel, "controller");
        if (controller == null) return false;

        boolean playing;
        try
        {
            playing = (boolean) controller.getClass().getMethod("isPlaying").invoke(controller);
        }
        catch (Throwable t)
        {
            return false;
        }
        if (playing) return false; /* playback → volles per-tick-update (glatt) */

        int t;
        try
        {
            t = (int) ziel.getClass().getMethod("getTick").invoke(ziel);
        }
        catch (Throwable t2)
        {
            return false;
        }
        long now = System.currentTimeMillis();

        Object gespeicherterTick = MixinBruecke.feldLesen(ziel, FELD_LAST_TICK);
        int lastAppliedTick = gespeicherterTick instanceof Integer ? (Integer) gespeicherterTick : Integer.MIN_VALUE;

        /* HINWEIS: NICHT das volle 3000-entity updateEntities erzwingen, während editiert wird —
         * das würde das 75k-interpolation/tick-freeze (= der edit-lag) wieder einführen. Die
         * live-vorschau der ausgewählten replay läuft über einen EINZEL-entity re-apply in
         * BackgroundBaker; der throttle hier bleibt billig. */

        if (t == lastAppliedTick)
        {
            /* Geparkt an unverändertem cursor. Die crowd ist STATISCH: sie zeichnet ihre
             * gebackene pose aus dem VAT-atlas, ihre positionen ändern sich nicht während der
             * cursor eingefroren ist — das alte "alle 500ms ein volles 3000-entity re-apply
             * erzwingen" tat 9.9ms arbeit (≈4×/s → die 200→120-einbrüche) für NULL sichtbare
             * änderung. Re-apply NUR die ausgewählte replay (billig — hält deren
             * positions-/ausrüstungs-edits in der vorschau; deren pose läuft ohnehin über
             * startRenderFrame) und breche das volle ab. Ein cursor-wechsel (scrub/playback)
             * überspringt diesen zweig → das volle läuft → crowd bewegt sich. */
            if (BBSPerfConfig.ENABLE_PARK_SELECTED_ONLY_REAPPLY)
            {
                reapplySelected(ziel, t, controller);
                BBSPerfConfig.editorUpdateSkips++;
                return true;
            }

            /* Legacy-pfad (toggle aus): original 500ms idempotenz-skip-fenster. */
            Object gespeicherteMs = MixinBruecke.feldLesen(ziel, FELD_LAST_APPLY_MS);
            long lastApplyMs = gespeicherteMs instanceof Long ? (Long) gespeicherteMs : 0L;
            if ((now - lastApplyMs) < 500L)
            {
                BBSPerfConfig.editorUpdateSkips++;
                return true;
            }
        }

        MixinBruecke.feldSchreiben(ziel, FELD_LAST_TICK, t);
        MixinBruecke.feldSchreiben(ziel, FELD_LAST_APPLY_MS, now);

        return false;
    }

    /** Re-apply NUR die aktuell ausgewählte replay's keyframes (1 entity, ~µs), damit deren
     *  positions-/ausrüstungs-edits in der vorschau erscheinen, während geparkt — ohne das volle
     *  3000-replay re-apply zu bezahlen. Pose läuft über startRenderFrame; das hier deckt die
     *  transform-/ausrüstungs-kanäle ab. Null-sicher: keine auswahl → nichts zu tun. */
    private static void reapplySelected(Object ziel, int tick, Object controller)
    {
        Object er = BBSPerfConfig.currentEntityRef;
        Object rp = BBSPerfConfig.currentReplay;

        if (!(er instanceof mchorse.bbs_mod.forms.entities.IEntity ent)) return;
        if (!(rp instanceof mchorse.bbs_mod.film.replays.Replay rep)) return;

        /* BBS-GUARD (FilmEditorController.applyReplay): NIE keyframes auf die entity unter
         * live ACTOR-KONTROLLE re-applyen. BBS wendet keyframes nur an, wenn
         * `entity != controller.getControlled()` — der kontrollierte actor wird vom
         * user-input gesteuert, nicht von seiner aufgezeichneten spur. Unser throttle ruft
         * keyframes.apply DIREKT (umgeht diesen guard), also musste dieselbe prüfung hier
         * nachgebildet werden: sonst schlägt ein parked-reapply auf den kontrollierten actor
         * jeden tick die aufgezeichnete pose/position zurück → der spieler kann sich nicht
         * bewegen, bleibt morphed. */
        try
        {
            if (controller != null)
            {
                Object controlled = controller.getClass().getMethod("getControlled").invoke(controller);
                if (ent == controlled) return;
            }
        }
        catch (Throwable ignored) {}

        try
        {
            rep.keyframes.apply(tick, ent);
            BBSPerfConfig.parkSelectedReapplies++;
        }
        catch (Throwable ignored) {}
    }
}
