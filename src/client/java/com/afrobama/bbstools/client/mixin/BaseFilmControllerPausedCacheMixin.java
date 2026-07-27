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

import com.afrobama.bbstools.perf.PausedReplayBoneCache;
import com.afrobama.bbstools.perf.PausedReplayMatrixCache;

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
        /* Drop every cache entry; bump version counter so any pending
         * reader on another thread sees the stale generation and falls
         * through to the recompute path. */
        PausedReplayBoneCache.INSTANCE.invalidateAll();
        PausedReplayMatrixCache.INSTANCE.reset();
    }

}
