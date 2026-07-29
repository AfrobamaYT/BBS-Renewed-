// Coded by Afrobama/Claude — Tier 3g static-batch paused replays.
//
// Sibling mixin to {@link BaseFilmControllerMixin}: keeps the
// {@link PausedReplayBoneCache} and {@link PausedReplayMatrixCache} in
// sync with the controller's play/pause/scrub state.
//
// Lifecycle hooks:
//   togglePause() RETURN
//     - Caches are invalidated unconditionally (covers both paused→playing
//       and playing→paused). On playing→paused the next render captures
//       fresh entries; on paused→playing the cache is no-longer-useful
//       (every tick advances) so we drop it.
//     - The version-counter cache also gets bumped, which makes any
//       in-flight entry stale even if invalidateAll() races a reader.
//
// Per-frame observeTick hook is performed inside the BOBJ VAO mixin's
// HEAD inject — that's the path where the tick is actually known at
// upload time.
package com.afrobama.bbstools.client.mixin;

import com.afrobama.bbstools.client.blender.MixinBruecke;

import mchorse.bbs_mod.film.BaseFilmController;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BaseFilmController.class, remap = false)
public abstract class BaseFilmControllerPausedCacheMixin
{
    @Inject(method = "togglePause", at = @At("RETURN"), remap = false)
    private void bbs$invalidatePausedCacheOnToggle(CallbackInfo ci)
    {
        /* BRÜCKEN-MUSTER: logik liegt in
         * com.afrobama.blendertransform.BaseFilmControllerPausedCacheLogik — dort ist sie
         * hotswapbar, der gewobene teil bleibt ein einzeiler. */
        MixinBruecke.rufe("BaseFilmControllerPausedCacheLogik", "invalidateOnToggle");
    }

}
