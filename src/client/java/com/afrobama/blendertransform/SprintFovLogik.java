package com.afrobama.blendertransform;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logik des editor-modus-sprint-FOV-mixins — im kernel-paket, damit sie hotswapbar ist.
 *
 * <p>Herausgelöst aus {@code SprintFOVMixin} (2026-07-26, brücken-umbau). Der handler dort
 * bleibt ein einzeiler, der hierher delegiert; der ganze zustand (statische felder) und die
 * gesamte smoothing-logik leben jetzt hier und können bei jedem kernel-swap getauscht werden,
 * ohne dass {@code GameRenderer} neu gewoben werden muss.</p>
 *
 * <p>⚠ KEINE KERNEL-TYPEN IN DER SIGNATUR — {@link #apply} nimmt nur JDK/Minecraft-typen
 * ({@code MinecraftClient}). Der neue FOV-wert kommt als rückgabe (Double, oder null =
 * "nicht ändern", handler lässt dann den originalwert stehen).</p>
 */
public final class SprintFovLogik
{
    private SprintFovLogik() {}

    private static final Logger LOG = LoggerFactory.getLogger("bbstools/editor-mode/sprint-fov");

    private static float sprintFov = 1F;
    private static float lastSprintFov = 1F;
    private static long lastFrame = Long.MIN_VALUE;
    private static boolean loggedFirstApply;

    /**
     * Berechnet den neuen FOV-wert für {@code GameRenderer#getFov}, oder liefert {@code null}
     * wenn nichts geändert werden soll.
     *
     * @param mc           {@code MinecraftClient.getInstance()} aus dem handler
     * @param changingFov  vanilla's eigenes gate (item-zoom/map-render schalten das aus)
     * @param tickDelta    original-parameter aus getFov
     * @param baseFov      der bisherige rückgabewert (evtl. schon von upstream verändert)
     */
    public static Double apply(Minecraft mc, boolean changingFov, float tickDelta, double baseFov)
    {
        try
        {
            if (!changingFov) return null;
            if (mc == null) return null;

            /* Don't perturb baked frames. */
            try
            {
                if (mchorse.bbs_mod.client.BBSRendering.isCustomSize()) return null;
            }
            catch (Throwable ignored) {}

            AbstractClientPlayer player = mc.player;
            if (player == null) return null;

            boolean isSprinting = player.isSprinting();
            /* 26.2: getFieldOfViewModifier nimmt zwei Werte, die es sich frueher selbst holte.
             * Erster: darf der Fernrohr-Zoom (fester Wert 0.1) durchschlagen. In 1.21.1 gab es
             * den Schalter nicht, der Zweig griff immer — deshalb hier true, das ist die
             * unveraenderte Bedingung. (Vanilla setzt inzwischen "nur in Ich-Perspektive" ein;
             * fuer diesen Aufrufer ist es ohnehin gleich, der Wert wird nur beim SPRINTEN
             * benutzt, und wer durch ein Fernrohr schaut, sprintet nicht.)
             * Zweiter: die Mischstaerke aus den Optionen (fovEffectScale), die frueher in der
             * Methode selbst nachgeschlagen wurde. */
            float fovMultiplier = player.getFieldOfViewModifier();
            long worldTime = mc.level == null ? 0L : mc.level.getGameTime();

            /* Iter-17 fix: do NOT bail when player stops sprinting, bail only when the
             * smoothed multiplier has already decayed back to 1.0. Previously the mixin
             * returned early as soon as isSprinting flipped false → FOV snapped back to 70°
             * in one frame (the "instant" feel the user reported). By staying engaged while
             * smoothed != 1, the multiplier ramps DOWN over ~5 ticks exactly like it ramped up. */
            boolean factorAlreadyAtRest = Math.abs(sprintFov - 1F) < 1E-3F
                && Math.abs(lastSprintFov - 1F) < 1E-3F;
            if (!isSprinting && factorAlreadyAtRest) return null;

            /* Vanilla's smoothed fovMultiplier returns ~1.15 when sprinting, ~1.0 otherwise.
             * We use this as the TARGET; our own per-tick lerp handles the smoothing so the
             * ramp down on sprint-stop is also smooth. */
            float target = isSprinting ? fovMultiplier : 1F;

            /* Per-frame smoothing. Window the update to a single per-frame tick by world-time
             * frame counter — getFov can be called multiple times per frame (Iris shadow pass,
             * hand pass, etc.) and we don't want to double-step. */
            if (worldTime != lastFrame)
            {
                lastFrame = worldTime;
                lastSprintFov = sprintFov;
                sprintFov += (target - sprintFov) * 0.5F;
            }

            float smoothed = lastSprintFov + (sprintFov - lastSprintFov) * tickDelta;

            /* No-op early-out if smoothed is essentially 1.0. */
            if (Math.abs(smoothed - 1F) < 1.0E-4F) return null;

            double next = baseFov * (double) smoothed;

            if (!loggedFirstApply)
            {
                loggedFirstApply = true;
                LOG.info("Sprint FOV multiplier ENGAGED in editor mode (target={}, smoothed={}, prev={}, next={})",
                    target, smoothed, baseFov, next);
            }

            return next;
        }
        catch (Throwable t)
        {
            LOG.debug("sprint-fov mixin failed: {}", t.toString());
            return null;
        }
    }
}
