// Coded by Afrobama/Claude — Army System P5 (editor-side): animator config cache.
//
// THE editor-freeze fix at scale. BBS's ModelFormRenderer.ensureAnimator runs
// resolveActionsConfig EVERY call — building a throwaway ActionsConfig and
// deep-copying gecko-animation HashMaps — purely to detect a (rare) config
// change vs lastConfigs. At 3000 replays this fires 3000×/tick inside
// BaseFilmController.updateEntities (+ again per render) and froze the editor
// (confirmed: render-thread stack RUNNABLE in GeckoAnimationsConfig.copy →
// ActionsConfig.copy → resolveActionsConfig → ensureAnimator).
//
// Fix: when the animator already exists and the model is unchanged, skip the
// re-resolve unless ANIMATOR_CACHE_THROTTLE_MS has elapsed. The animator stays
// fully valid between checks — we only skip the redundant "did the config
// change?" rebuild. A real actions-config edit is picked up within the throttle
// window (default 100ms = imperceptible). Model change or a null animator both
// fall through to BBS's normal setup, so first-time setup + model swaps are
// unaffected.
//
// Default OFF (ENABLE_ANIMATOR_CONFIG_CACHE) — opt-in for army-scale editing.
package com.afrobama.bbstools.client.mixin.bbs;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import com.afrobama.bbstools.perf.BBSPerfConfig;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.animation.IAnimator;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.invoke.MethodHandle;

@Mixin(value = ModelFormRenderer.class, remap = false)
public abstract class ModelFormRendererAnimatorCacheMixin
{
    @Shadow private IAnimator animator;
    @Shadow public abstract ModelInstance getModel();

    @Unique private long bbstools$lastResolveMs = 0L;
    @Unique private ModelInstance bbstools$lastSeenModel = null;

    /* BRÜCKEN-MUSTER, PRO-ENTITY-VARIANTE (2026-07-26) — siehe ModelShadowTimingMixin für die
     * volle begründung. Statisch (klassen-weit), weil der griff selbst nichts instanz-
     * spezifisches trägt — nur die throttle-ENTSCHEIDUNG bekommt instanz-primitive als arg. */
    @Unique private static long bbstools$throttledGriffStamp = Long.MIN_VALUE;
    @Unique private static MethodHandle bbstools$throttledGriff;

    @Inject(method = "ensureAnimator", at = @At("HEAD"), cancellable = true, remap = false)
    private void bbstools$throttleEnsureAnimator(float transition, CallbackInfo ci)
    {
        /* No animator yet → let BBS build it. */
        if (this.animator == null) return;

        ModelInstance model = this.getModel();
        if (model == null) return;

        /* Model changed → let BBS rebuild + re-resolve. Mixin-lokaler @Unique-zustand, bleibt
         * hier (siehe ModelFormRendererAnimatorCacheLogik-javadoc). */
        if (model != this.bbstools$lastSeenModel)
        {
            this.bbstools$lastSeenModel = model;
            this.bbstools$lastResolveMs = System.currentTimeMillis();
            return;
        }

        bbstools$refreshThrottledGriff();

        long now = System.currentTimeMillis();

        if (bbstools$callThrottled(now, this.bbstools$lastResolveMs))
        {
            ci.cancel();
            return;
        }

        /* Throttle elapsed — let BBS re-resolve once + refresh lastConfigs. */
        this.bbstools$lastResolveMs = now;
    }

    private static boolean bbstools$callThrottled(long nowMs, long lastResolveMs)
    {
        try
        {
            return bbstools$throttledGriff != null
                && (boolean) bbstools$throttledGriff.invoke(nowMs, lastResolveMs);
        }
        catch (Throwable ignored)
        {
            /* Fehler = nicht throttlen, BBS läuft normal weiter (sichere richtung). */
            return false;
        }
    }

    /**
     * Holt den griff neu, sobald ein neuer render-frame begonnen hat.
     *
     * <p>⛔ DER NAME MUSS UEBER ALLE MIXINS AUF {@code ModelFormRenderer} EINDEUTIG SEIN.
     * Bis 2026-08-01 hiess diese methode {@code bbstools$refreshGriff} — genau wie die
     * gleichnamige in {@link ModelFormRendererFastPathMixin}, das auf DIESELBE zielklasse
     * geht. Mixin haelt dann eine und verwirft die andere:
     *
     * <pre>Method overwrite conflict for bbstools$refreshGriff ... Skipping method.</pre>
     *
     * <p>Die verworfene war diese hier. Folge: der aufruf oben landete im FastPath-rumpf,
     * {@code bbstools$throttledGriff} blieb null, {@code bbstools$callThrottled} gab immer
     * false zurueck — die ganze drossel lief leer. Kein crash, keine ausnahme, nur EINE
     * WARN-zeile im log. Feld-namen schuetzt {@code @Unique}, methoden-namen NICHT.
     */
    private static void bbstools$refreshThrottledGriff()
    {
        long stamp = BBSPerfConfig.bbsRenderHeadNs;

        if (stamp != bbstools$throttledGriffStamp)
        {
            bbstools$throttledGriffStamp = stamp;
            bbstools$throttledGriff = MixinBruecke.griff("ModelFormRendererAnimatorCacheLogik", "throttled", 2);
        }
    }

}
