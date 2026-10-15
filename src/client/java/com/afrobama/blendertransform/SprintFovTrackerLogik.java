package com.afrobama.blendertransform;

import net.minecraft.client.Minecraft;

/**
 * Logik des tracker-sprint-FOV-mixins — im kernel-paket, damit sie hotswapbar ist.
 *
 * <p>Herausgelöst aus {@code GameRendererTrackerSprintFovMixin} (2026-07-26, brücken-umbau).
 * Der handler dort bleibt ein einzeiler, der hierher delegiert; die reflection-probe, der
 * geglättete faktor und alle statischen felder leben jetzt hier und können bei jedem
 * kernel-swap getauscht werden, ohne dass {@code GameRenderer} neu gewoben werden muss.</p>
 *
 * <p>Bypass von upstreams {@code TrackerClientClip.updateSprintFov}-pipeline (die wird im
 * user-setup nicht gefüttert — log bestätigte isSprinting bleibt false am statischen feld).
 * Stattdessen: {@code TrackerClientClip.currentEntity.isSprinting()} direkt lesen und selbst
 * glätten. Funktioniert solange BBS irgendwo {@code currentEntity} setzt (macht es —
 * applyClip zeile 1036 von upstream).</p>
 *
 * <p>⚠ KEINE KERNEL-TYPEN IN DER SIGNATUR — {@link #apply} nimmt nur JDK-typen
 * (long/float/double/boolean). Alle reflection-ziele (TrackerClientClip, IEntity, ...) sind
 * BBS-typen und werden per {@code Class.forName} aufgelöst wie im original.</p>
 */
public final class SprintFovTrackerLogik
{
    private SprintFovTrackerLogik() {}

    /** Reflective handles — resolved on first successful probe. We probe BOTH classes because
     *  user's setup uses our fork BBSToolsTrackerClientClip which has its OWN currentEntity
     *  field (upstream's stays null when our subclass is the active tracker). Whichever
     *  returns non-null wins. */
    private static java.lang.reflect.Field upstreamCurrentEntity;
    private static java.lang.reflect.Field forkCurrentEntity;
    private static java.lang.reflect.Field forkLevelRendering;
    private static java.lang.reflect.Method isSprintingMethod;
    private static boolean probed;
    private static int logCount;

    /** Our own smoothed sprint factor. Approach 1.15 when entity is sprinting, decay to 1.0
     *  when it isn't. Per-tick step at 0.15 (~83% to target in 5 ticks ≈ 0.25 s) for a natural
     *  feel. */
    private static float factor = 1F;
    private static float lastFactor = 1F;
    private static long lastFrame = Long.MIN_VALUE;

    /**
     * Berechnet den neuen FOV-wert, oder liefert {@code null} wenn nichts geändert werden soll.
     *
     * @param manualSprint  {@code SprintFovState.MANUAL_SPRINT} — instant 1.3x override, ohne glättung
     * @param mc            {@code MinecraftClient.getInstance()} aus dem handler
     * @param tickDelta     original-parameter aus getFov
     * @param baseFov       der bisherige rückgabewert
     */
    public static Double apply(boolean manualSprint, Minecraft mc, float tickDelta, double baseFov)
    {
        try
        {
            long worldTime = (mc == null || mc.level == null) ? 0L : mc.level.getGameTime();

            if (!probed)
            {
                Class<?> upstream = Class.forName("mchorse.bbs_mod.camera.clips.misc.TrackerClientClip");
                upstreamCurrentEntity = upstream.getDeclaredField("currentEntity");
                try
                {
                    Class<?> fork = Class.forName("com.afrobama.bbstools.client.tracker.BBSToolsTrackerClientClip");
                    forkCurrentEntity = fork.getDeclaredField("currentEntity");
                    /* Gate signal: true only while the tracker is rendering its FIRST-PERSON
                     * view (beginLevelRendering sets it + forces Perspective.FIRST_PERSON).
                     * In real gameplay (controller didn't run) and editor free-flight (hand
                     * not needed) it stays false → the synthesized FOV override below bails,
                     * killing the "doubles in real play" + "cumulative in free-flight" leaks. */
                    forkLevelRendering = fork.getDeclaredField("levelRenderingActive");
                    forkLevelRendering.setAccessible(true);
                }
                catch (Throwable t) { /* fork class might not exist on stripped builds */ }
                Class<?> ient = Class.forName("mchorse.bbs_mod.forms.entities.IEntity");
                isSprintingMethod = ient.getMethod("isSprinting");
                probed = true;
                org.slf4j.LoggerFactory.getLogger("bbstools/sprint-fov-tracker")
                    .info("[BBSDIAG/sprint-fov] event=probe-ok upstream-field={} fork-field={}",
                        upstreamCurrentEntity != null, forkCurrentEntity != null);
            }

            /* MANUAL OVERRIDE — applies 1.3x INSTANTLY (no smoothing, no bail) so user can
             *  verify the FOV pipeline works at all. If even this doesn't visibly change FOV,
             *  then another mixin is overriding us further downstream. */
            if (manualSprint)
            {
                double newFovManual = baseFov * 1.3D;
                if (logCount++ % 60 == 0)
                {
                    org.slf4j.LoggerFactory.getLogger("bbstools/sprint-fov-tracker")
                        .info("[BBSDIAG/sprint-fov] event=manual-write base={} newFov={} verify={}",
                            baseFov, newFovManual, newFovManual);
                }
                return newFovManual;
            }

            /* AUTO-PATH GATE — apply the synthesized sprint FOV ONLY while the tracker is
             * actively rendering its first-person view (levelRenderingActive). Without this
             * the override fired on every getFov call whenever a (possibly stale) currentEntity
             * was sprinting → doubled the FOV in real gameplay and stacked on each editor
             * free-flight enter. levelRenderingActive is false in both those contexts. */
            if (forkLevelRendering != null)
            {
                Object lvl = forkLevelRendering.get(null);
                if (!(lvl instanceof Boolean) || !((Boolean) lvl)) return null;
            }

            /* Read whichever class actually has a non-null entity. The user's setup uses our
             *  fork, which populates its own currentEntity; upstream's stays null. Prefer fork;
             *  fall through to upstream. */
            Object currentEntity = null;
            if (forkCurrentEntity != null)
            {
                currentEntity = forkCurrentEntity.get(null);
            }
            if (currentEntity == null && upstreamCurrentEntity != null)
            {
                currentEntity = upstreamCurrentEntity.get(null);
            }
            boolean entitySprinting = false;
            if (currentEntity != null)
            {
                entitySprinting = (Boolean) isSprintingMethod.invoke(currentEntity);
            }

            /* Once-per-tick step so multiple getFov calls per frame (shadow pass, hand pass,
             *  main pass) don't double-step the smoothing. */
            if (worldTime != lastFrame)
            {
                lastFrame = worldTime;
                lastFactor = factor;
                float target = entitySprinting ? 1.15F : 1.0F;
                factor += (target - factor) * 0.15F;
            }

            float smoothed = lastFactor + (factor - lastFactor) * tickDelta;

            /* Throttled diag — every 60 calls. Now logs entity class so we can see what we're
             *  reading from when isSprinting=false despite the keyframe value=1 (entity might
             *  be a different StubEntity instance than the keyframe-receiving one). */
            if (logCount++ % 60 == 0)
            {
                String entityCls = currentEntity == null ? "null" : currentEntity.getClass().getName();
                org.slf4j.LoggerFactory.getLogger("bbstools/sprint-fov-tracker")
                    .info("[BBSDIAG/sprint-fov] event=probe entitySprinting={} entityCls={} factor={} smoothed={} baseFov={}",
                        entitySprinting, entityCls, factor, smoothed, baseFov);
            }

            /* Bail when smoothed ≈ 1.0 to avoid perturbing every frame. */
            if (Math.abs(smoothed - 1F) < 0.001F) return null;

            double newFov = baseFov * (double) smoothed;

            /* Verify-after-write per log-first policy. */
            if (logCount % 60 == 1)
            {
                org.slf4j.LoggerFactory.getLogger("bbstools/sprint-fov-tracker")
                    .info("[BBSDIAG/sprint-fov] event=write base={} smoothed={} newFov={} verify={}",
                        baseFov, smoothed, newFov, newFov);
            }

            return newFov;
        }
        catch (Throwable t)
        {
            if (logCount++ % 600 == 0)
            {
                org.slf4j.LoggerFactory.getLogger("bbstools/sprint-fov-tracker")
                    .warn("[BBSDIAG/sprint-fov] event=error msg={}", t.toString());
            }
            return null;
        }
    }
}
