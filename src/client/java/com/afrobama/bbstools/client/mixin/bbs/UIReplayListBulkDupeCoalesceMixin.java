// Coded by Afrobama/Claude — kills the O(N^2) freeze on bulk duplicate (dupe one
// replay 8999×, observed 1-4 minute freeze).
//
// The duplicate-count overlay runs `for (int i = 0; i < n; i++) this.dupeReplay();`.
// Vanilla dupeReplay() does, PER call: create one copy, then buildVisualList() (O(N))
// + setCurrentDirect() + replayEditor.setReplay() + updateFilmEditor() (createEntities +
// updateChannelsList). Those UI rebuilds are O(N), looped N times = O(N^2) string/scan
// work on the render thread = the multi-minute freeze.
//
// FIX: cancel vanilla dupeReplay and do ONLY the data creation here (addReplay +
// Replay.copy + fresh uuid) for the CURRENT selection, then register a single deferred
// UI flush (BulkDupeFlush, next client tick). The overlay's n calls each duplicate the
// (unchanged) original selection → n fan copies — data-identical to vanilla's chain
// (vanilla re-selects the new copy each iter so iter k+1 copies iter k's copy; every
// copy is a copy of the same original, so fan == chain in the result). One O(N) UI pass
// at the end instead of N → O(N) total, no freeze.
//
// Why not just skip buildVisualList (the earlier attempt): the per-iter buildVisualList
// is load-bearing for vanilla's chain (it makes the new replay selectable so the next
// iter can copy it). Skipping it broke the chain → only ~2-3 replays created. Batching
// the WHOLE tail (so the chain is never needed) is the correct fix.
package com.afrobama.bbstools.client.mixin.bbs;

import com.afrobama.bbstools.client.blender.MixinBruecke;

import mchorse.bbs_mod.ui.film.replays.UIReplayList;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = UIReplayList.class, remap = false)
public class UIReplayListBulkDupeCoalesceMixin
{
    /* BRÜCKEN-MUSTER (2026-07-26): logik liegt in
     * com.afrobama.blendertransform.UIReplayListBulkDupeCoalesceLogik. */
    @Inject(method = "dupeReplay", at = @At("HEAD"), cancellable = true)
    private void bbstools$batchDupe(CallbackInfo ci)
    {
        UIReplayList self = (UIReplayList) (Object) this;

        if (MixinBruecke.rufeUndAbbrechen("UIReplayListBulkDupeCoalesceLogik", "batchDupe", self))
        {
            ci.cancel();
        }
    }

}
