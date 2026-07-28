// Coded by Afrobama/Claude — Tier 1d UIReplayList crowdMode.
//
// When BBSPerfConfig.UI_REPLAY_LIST_CROWD_MODE is true:
//   1. At HEAD of UIReplayList.render(UIContext) we wipe the per-frame
//      aggregation map so the strip rebuilds cleanly each frame.
//   2. At HEAD of renderElementPart(UIContext, Replay, int, int, int,
//      boolean, boolean) we bucket the row's actor-type name
//      (Form.getDisplayName) into the shared map and cancel the
//      vanilla per-row render. That skips both the indent/label work
//      AND the expensive per-row FormUtilsClient.renderUI thumbnail
//      bake at the right edge.
//   3. At TAIL of UIReplayList.render(UIContext) we draw a compact strip
//      listing every distinct type with its row count, so the user can
//      still see the population at a glance.
//
// All shared state lives in UIReplayCrowdModeState (per the mixin
// no-public-static rule).
//
// Default OFF. Toggle via /bbstools ui crowdmode on|off or /bbsdebug
// ui-crowdmode on. Reflectively safe — if any optional field/method
// rename in BBS-Renewed, the per-row inject is wrapped in try/catch.
package com.afrobama.bbstools.client.mixin;

import com.afrobama.bbstools.client.blender.MixinBruecke;

import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.ui.film.replays.ReplayListEntry;
import mchorse.bbs_mod.ui.film.replays.UIReplayList;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(UIReplayList.class)
public abstract class UIReplayListCrowdModeMixin extends UIList<Replay>
{
    protected UIReplayListCrowdModeMixin(java.util.function.Consumer<java.util.List<Replay>> callback)
    {
        super(callback);
    }

    /* AUTONOMIE 2026-07-27: die beiden render-injections (HEAD/TAIL) sind ENTFERNT.
     * fs' UIReplayList erbt render() von UIList und deklariert es nicht selbst — ein
     * @Inject(method="render") findet in der zielklasse dann kein ziel und laesst den
     * ganzen mixin (und damit den start) scheitern. Die aggregation laeuft jetzt allein
     * ueber renderElementPart unten: die logik-klasse setzt ihren zaehler beim ersten
     * aufruf eines frames zurueck. Der zusammenfassungs-streifen am ende des frames
     * fehlt damit — er braucht einen eigenen anker (z.B. UIList.render), der in der
     * feature-runde nachgezogen wird. */

    /** HEAD of renderElementPart: in crowd mode, aggregate the row's
     *  actor-type label and CANCEL the vanilla render. Skips the
     *  per-row FormUtilsClient.renderUI bake at the right edge —
     *  the dominant per-row cost for 100+ replay lists. */
    @Inject(method = "renderElementPart",
            at = @At("HEAD"),
            cancellable = true)
    private void bbstools$crowdModeAggregateRow(UIContext context, ReplayListEntry element,
        int i, int x, int y, boolean hover, boolean selected, CallbackInfo ci)
    {
        /* BRÜCKEN-MUSTER (2026-07-26): logik liegt in
         * com.afrobama.blendertransform.UiReplayListCrowdModeLogik — siehe MixinBruecke.
         *
         * Das REPLAY auspacken, nicht die zeile weiterreichen: renderElementPart bekommt seit
         * fs einen ReplayListEntry, die logik erwartet ein Replay. Vorher flog hier jeden frame
         * eine ClassCastException, die die bruecke geschluckt hat — der crowd-modus tat also nie
         * etwas, und jeder fehlschlag baute einen stacktrace auf (gemessen 2026-07-28: >4000
         * fehlschlaege im leerlauf).
         *
         * isReplay() ist dabei nicht nur typ-sicherheit: FOLDER-zeilen sind kategorie-koepfe mit
         * replay == null und sollen gar nicht aggregiert werden. */
        if (element != null && element.isReplay()
            && MixinBruecke.rufeUndAbbrechen("UiReplayListCrowdModeLogik", "crowdModeAggregateRow",
                element.replay)) ci.cancel();
    }


}
