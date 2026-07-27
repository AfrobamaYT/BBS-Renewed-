// Coded by Afrobama/Claude — playback simulation decouple (render-rate, not tick-rate).
//
// BBS drives the heavy per-replay film sim — BaseFilmController.updateEntities ->
// updateEntityAndForm + ReplayKeyframes.apply (~40 KeyframeChannel.interpolate calls per
// replay) — from ClientTickEvents.END_CLIENT_TICK, i.e. ONCE PER CLIENT TICK. When the render
// can't keep 20 TPS (e.g. a 20k-replay crowd at ~1 fps), MC runs many catch-up ticks per
// rendered frame (measured ~11 ticks/frame), so updateEntities runs ~11x per frame — but only
// the LAST tick's entity state is ever rendered. The other ~10 are pure waste, and they
// death-spiral: slow render -> more catch-up ticks -> even slower.
//
// Fix: coalesce updateEntities to run at most ONCE per RENDERED frame. We key on a real-frame
// counter (BBSPerfConfig.bbstoolsRenderFrameId, bumped once per frame from the HUD callback):
// the first per-tick updateEntities of each frame runs; later catch-up ticks sharing that
// frame id are cancelled. This is SELF-GATING — when the render keeps up (editing/idle at 60fps,
// frame id advances faster than ticks) every tick gets a fresh id and nothing is skipped; only
// the catch-up burst during slow playback is collapsed. Keyframe motion/pose stays exact
// (interpolate is a pure function of tick, and the surviving call uses the latest tick). The
// only behavioural change: per-tick side effects in updateEntities (action-clip application,
// step sounds, sprint particles) fire once per frame instead of every tick — coarser action
// timing at heavy-crowd playback. Flag OFF (ENABLE_PLAYBACK_SIM_DECOUPLE) for frame-precise
// action-clip timing.
package com.afrobama.bbstools.client.mixin.bbs;

import com.afrobama.bbstools.client.blender.MixinBruecke;

import mchorse.bbs_mod.film.BaseFilmController;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BaseFilmController.class, remap = false)
public abstract class BaseFilmControllerPlaybackDecoupleMixin
{
    /** Render-frame id of the last updateEntities that was allowed to run, per controller. */
    @Unique private long bbstools$lastUpdateFrameId = -1L;

    @Inject(method = "updateEntities(I)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void bbstools$coalescePerFrame(int ticks, CallbackInfo ci)
    {
        /* BRÜCKEN-MUSTER (2026-07-26): siehe MixinBruecke-javadoc. Logik liegt in
         * com.afrobama.blendertransform.PlaybackDecoupleLogik und ist damit hotswapbar; nur
         * dieser einzeiler wird beim klassenladen gewoben. */
        if (MixinBruecke.rufeUndAbbrechen("PlaybackDecoupleLogik", "coalescePerFrame", this))
        {
            ci.cancel();
        }
    }

}
