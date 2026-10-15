// Coded by Afrobama/Claude — diagnostic timer for FilmEditorController.updateEntities.
//
// Times the per-tick keyframe re-apply (15 equipment slots + pos/rot × every
// replay). Suspected playback cost (3-phase test 2026-05-24: in-world playback
// 112fps vs parked editor 158fps, with startRenderFrame only 0.30ms). Confirm
// the delta lands HERE before optimising it.
//
// RETURN inject won't fire when the throttle mixin ci.cancel()s the parked path
// (synthetic return), so t0 would leak across frames — a >50ms delta is treated
// as a leak and dropped, and HEAD always re-arms t0, so it self-heals. During
// PLAYBACK (the case we measure) the throttle does NOT cancel → RETURN fires →
// clean timing. Zero behaviour change; pure measurement.
package com.afrobama.bbstools.client.mixin.bbs;

import com.afrobama.bbstools.client.blender.MixinBruecke;

import mchorse.bbs_mod.ui.film.controller.FilmEditorController;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FilmEditorController.class, remap = false)
public abstract class FilmEditorControllerUpdateTimerMixin
{
    @Unique private long bbstools$updTimerT0 = 0L;

    /* BRÜCKEN-MUSTER (2026-07-26): siehe MixinBruecke-javadoc. Logik liegt in
     * com.afrobama.blendertransform.UpdateTimerLogik und ist damit hotswapbar; nur dieser
     * einzeiler wird beim klassenladen gewoben. */
    @Inject(method = "updateEntities", at = @At("HEAD"), remap = false)
    private void bbstools$updTimerHead(int ticks, CallbackInfo ci)
    {
        MixinBruecke.rufe("UpdateTimerLogik", "head", this);
    }

    @Inject(method = "updateEntities", at = @At("RETURN"), remap = false)
    private void bbstools$updTimerReturn(int ticks, CallbackInfo ci)
    {
        MixinBruecke.rufe("UpdateTimerLogik", "rueckkehr", this);
    }

}
