package com.afrobama.blendertransform;

import com.afrobama.bbstools.client.blender.MixinBruecke;

/**
 * Logik des sprint-FOV-mixins — im kernel-paket, damit sie hotswapbar ist.
 *
 * <p>Herausgelöst aus {@code PositionFovMixin} (2026-07-26, brücken-umbau). Der handler dort
 * ist jetzt ein einzeiler, der hierher delegiert; dieser code kann bei jedem kernel-swap
 * getauscht werden, ohne dass {@code Position} neu gewoben werden muss.</p>
 *
 * <p>Multipliziert {@code angle.fov} VOR der umrechnung in {@code camera.fov} — trifft dadurch
 * jeden view-pfad, der camera.fov liest (haupt-playback, editor-preview, alt-cam, export).
 * Feuert einmal pro {@code Position.apply}-aufruf (eine kamera pro frame), NICHT pro entity in
 * einer schleife — der brücken-overhead (map-lookup + invoke) ist hier unkritisch.</p>
 *
 * <p>⚠ KEINE KERNEL-TYPEN IN DER SIGNATUR. {@code ziel} ist die {@code Position}-instanz (BBS-
 * typ, stabiler loader) als {@code Object} durchgereicht, weil {@code bbstools$logCount} ein
 * @Unique-feld ist, das beim weben zu einem echten instanzfeld von {@code Position} wird — der
 * zugriff läuft über {@link MixinBruecke#feldLesen}/{@link MixinBruecke#feldSchreiben} auf der
 * ZIEL-instanz, nicht über eine kernel-globale map (sonst hinge der zähler am falschen objekt).</p>
 */
public final class PositionFovLogik
{
    private PositionFovLogik() {}

    /**
     * Wird vom gewobenen {@code Position.apply}-handler pro kamera-frame gerufen.
     *
     * @param ziel   die {@code Position}-instanz (für {@code @Unique bbstools$logCount})
     * @param camera die {@code mchorse.bbs_mod.camera.Camera}-instanz (unbenutzt hier, wie im
     *               original — bleibt in der signatur, weil der handler sie durchreicht)
     */
    /**
     * Der aktuelle sprint-multiplikator, mit dem {@link #applySprintFov} {@code angle.fov} boostet.
     * 1.0 = kein boost.
     *
     * <p>Herausgezogen 2026-07-28: {@code SprintFovLogik} muss denselben faktor kennen, um ihn im
     * hand-pfad wieder herauszurechnen (vanilla rendert die hand sprint-frei). EINE quelle, damit
     * boost und gegenrechnung nicht auseinanderlaufen koennen.</p>
     */
    public static float sprintFaktor()
    {
        if (com.afrobama.bbstools.client.tracker.SprintFovState.MANUAL_SPRINT)
        {
            /* Vanilla MC sprint FOV multiplier — matches the
             *  PlayerEntity.getSpeedFOVMultiplier() value when
             *  isSprinting() returns true. Was 1.3 for visibility
             *  during 16-iter debug session; back to vanilla now
             *  that the hook works. */
            return 1.15F;
        }

        /* Read upstream TrackerClientClip's smoothed sprint
         *  factor. When entity sprints during playback,
         *  CameraWorkCameraController calls updateSprintFov
         *  which sets this. */
        try
        {
            Class<?> cls = Class.forName("mchorse.bbs_mod.camera.clips.misc.TrackerClientClip");
            float factor = cls.getDeclaredField("sprintFovFactor").getFloat(null);
            float lastFactor = cls.getDeclaredField("lastSprintFovFactor").getFloat(null);
            float td = cls.getDeclaredField("currentTickDelta").getFloat(null);

            return lastFactor + (factor - lastFactor) * td;
        }
        catch (Throwable ignored) {}

        return 1F;
    }

    public static void applySprintFov(Object ziel, Object camera)
    {
        try
        {
            float mul = sprintFaktor();

            if (Math.abs(mul - 1F) < 0.001F) return;

            /* Access angle.fov reflectively to avoid hard coupling
             *  to Position internal field name remapping. */
            mchorse.bbs_mod.camera.data.Position self = (mchorse.bbs_mod.camera.data.Position) ziel;
            float origFov = self.angle.fov;
            float boosted = origFov * mul;
            self.angle.fov = boosted;

            int logCount = zaehlerLesen(ziel);
            if (logCount % 60 == 0)
            {
                org.slf4j.LoggerFactory.getLogger("bbstools/sprint-fov-position")
                    .info("[BBSDIAG/sprint-fov-position] event=boost orig={} mul={} boosted={}",
                        origFov, mul, boosted);
            }
            zaehlerSchreiben(ziel, logCount + 1);
        }
        catch (Throwable t)
        {
            int logCount = zaehlerLesen(ziel);
            if (logCount % 600 == 0)
            {
                org.slf4j.LoggerFactory.getLogger("bbstools/sprint-fov-position")
                    .warn("[BBSDIAG/sprint-fov-position] event=error msg={}", t.toString());
            }
            zaehlerSchreiben(ziel, logCount + 1);
        }
    }

    /** Liest {@code bbstools$logCount} von der ziel-{@code Position}-instanz (default 0). */
    private static int zaehlerLesen(Object ziel)
    {
        Object wert = MixinBruecke.feldLesen(ziel, "bbstools$logCount");
        return wert instanceof Integer ? (Integer) wert : 0;
    }

    /** Schreibt {@code bbstools$logCount} auf der ziel-{@code Position}-instanz zurück. */
    private static void zaehlerSchreiben(Object ziel, int wert)
    {
        MixinBruecke.feldSchreiben(ziel, "bbstools$logCount", wert);
    }
}
