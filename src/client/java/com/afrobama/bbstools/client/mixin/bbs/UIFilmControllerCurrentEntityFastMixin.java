// Coded by Afrobama/Claude — Army System: O(N²)→O(N) current-entity resolve.
//
// THE select-in-replay-menu freeze (third incarnation of the deep-equals
// indexOf bug-class, after the cache-bar and buildVisualList). Render-thread
// dump during the freeze (fps=0):
//   parallelStartRenderFrame (our per-replay parallel mixin)
//     → FilmEditorController.getTransition → isCurrent
//       → UIFilmController.getCurrentEntity
//         → List.indexOf → ValueGroup.equals (DEEP: keyframes + GeckoAnimations
//            Config + ActionsConfig) → ... O(N) deep-compare per call.
// getCurrentEntity is invoked ONCE PER REPLAY every render frame (getTransition
// runs for every replay), so at 3000 replays that's 3000 × indexOf-over-3000
// deep-equals = 9,000,000 deep ValueGroup compares per frame. Our parallel
// startRenderFrame mixin fans this across every core, so the WHOLE machine
// locks (render thread WAITING on the ForkJoin task) — the freeze.
//
// getCurrentEntity resolves the SAME single "current" replay's index regardless
// of which replay is being processed, and that index only changes when the user
// selects a different replay. Fix: redirect the indexOf to (1) an identity
// scan (== instead of the heavy ValueGroup.equals — the editor's current Replay
// IS one of the list's own objects), and (2) a one-entry cache keyed by the
// current Replay's identity, so once resolved it returns in O(1) until the
// selection or list changes. Steady state = 0 scans; a selection change = one
// O(N) identity scan (pointer compares, microseconds). The cache fields are
// volatile because the parallel startRenderFrame calls this from ForkJoin
// worker threads — concurrent misses just recompute the same value (benign).
//
// Default ON (ENABLE_CURRENT_ENTITY_CACHE). Scoped to getCurrentEntity only.
package com.afrobama.bbstools.client.mixin.bbs;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import com.afrobama.bbstools.perf.BBSPerfConfig;

import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.ui.film.controller.UIFilmController;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.invoke.MethodHandle;
import java.util.List;

@Mixin(value = UIFilmController.class, remap = false)
public abstract class UIFilmControllerCurrentEntityFastMixin
{
    @Unique private volatile Object bbstools$lastReplay = null;
    @Unique private volatile int    bbstools$lastIndex  = -1;
    @Unique private volatile int    bbstools$lastSize   = -1;

    /* BRÜCKEN-MUSTER, PRO-ENTITY-VARIANTE (2026-07-26) — siehe ModelShadowTimingMixin für die
     * volle begründung. Statisch (klassen-weit); die instanz-uniques oben werden als primitive
     * argumente an den kernel gereicht, nicht über feldLesen (das findet nur @Shadow). */
    @Unique private static long bbstools$griffFrameStamp = Long.MIN_VALUE;
    @Unique private static MethodHandle bbstools$resolveGriff;
    @Unique private static MethodHandle bbstools$publishGriff;

    /* AUTONOMIE 2026-07-27: ziel von getCurrentEntity -> getCurrentReplayIndex.
     * fs hat die index-suche in eine eigene methode ausgelagert (UIFilmController:341);
     * getCurrentEntity ruft sie nur noch auf und enthaelt selbst kein indexOf mehr.
     * Der redirect fand dort kein ziel und riss beim OEFFNEN DES DASHBOARDS den ganzen
     * client mit (UIFilmPanel:142 laedt die klasse lazy — deshalb erst beim klick,
     * nicht beim boot). Semantik unveraendert: dieselbe liste, derselbe replay. */
    @Redirect(
        method = "getCurrentReplayIndex",
        at = @At(value = "INVOKE", target = "Ljava/util/List;indexOf(Ljava/lang/Object;)I"),
        remap = false)
    private int bbstools$fastCurrentIndex(List list, Object replay)
    {
        bbstools$refreshGriffe();

        int idx = bbstools$callResolve(list, replay);

        /* Schreibt den cache immer zurück: bei einem hit sind es dieselben werte (no-op),
         * bei einem miss die frisch aufgelösten — identisch zum verhalten vor dem umbau. */
        this.bbstools$lastReplay = replay;
        this.bbstools$lastIndex  = idx;
        this.bbstools$lastSize   = list.size();

        return idx;
    }

    /** Publish the resolved selected entity so the per-entity render3D fast-path
     *  can let it render LIVE (skip the bake). RETURN value is the selected
     *  IEntity (or null when nothing is selected). */
    @Inject(method = "getCurrentEntity", at = @At("RETURN"), remap = false)
    private void bbstools$publishCurrentEntity(CallbackInfoReturnable<IEntity> cir)
    {
        bbstools$refreshGriffe();
        bbstools$callPublish(cir.getReturnValue());
    }

    private int bbstools$callResolve(List list, Object replay)
    {
        try
        {
            if (bbstools$resolveGriff != null)
            {
                return (int) bbstools$resolveGriff.invoke(
                    list, replay, this.bbstools$lastReplay, this.bbstools$lastIndex, this.bbstools$lastSize);
            }
        }
        catch (Throwable ignored) { /* fällt auf den echten indexOf zurück */ }

        return list.indexOf(replay);
    }

    private static void bbstools$callPublish(Object resolvedEntity)
    {
        try
        {
            if (bbstools$publishGriff != null) bbstools$publishGriff.invoke(resolvedEntity);
        }
        catch (Throwable ignored) { /* siehe MixinBruecke-javadoc: fehler = frame auslassen */ }
    }

    /** Holt beide griffe neu, sobald ein neuer render-frame begonnen hat. */
    private static void bbstools$refreshGriffe()
    {
        long stamp = BBSPerfConfig.bbsRenderHeadNs;

        if (stamp != bbstools$griffFrameStamp)
        {
            bbstools$griffFrameStamp = stamp;
            bbstools$resolveGriff = MixinBruecke.griff("UIFilmControllerCurrentEntityFastLogik", "resolveIndex", 5);
            bbstools$publishGriff = MixinBruecke.griff("UIFilmControllerCurrentEntityFastLogik", "publishCurrentEntity", 1);
        }
    }

}
