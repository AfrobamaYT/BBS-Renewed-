// Coded by Afrobama/Claude — apply sprint FOV multiplier at the
// source: BBS's Position.apply(Camera) sets camera.fov from angle.fov
// every frame. By multiplying angle.fov BEFORE the conversion, the
// resulting camera.fov is boosted — affecting EVERY view path that
// reads camera.fov (main playback, editor preview, alt-cam previews,
// recording export, all of it).
//
// This is the CORRECT hook after 29 iters of trying GameRenderer.getFov
// and pre-pass mixins — user confirmed iter 28's getFov override
// DID write the correct value (verify=91.0 in log) but editor view
// was unchanged. Editor doesn't read MC's GameRenderer FOV; it reads
// BBS's own camera.fov which comes from Position.apply. So this is
// where the multiplier needs to live.
package com.afrobama.bbstools.client.mixin.bbs;

import com.afrobama.bbstools.client.blender.MixinBruecke;

import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.data.Position;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Position.class)
public abstract class PositionFovMixin
{
    private static int bbstools$logCount;

    @Inject(method = "apply", at = @At("HEAD"))
    private void bbstools$applySprintFov(Camera camera, CallbackInfo ci)
    {
        /* BRÜCKEN-MUSTER (2026-07-26): der rumpf dieses handlers wird beim klassenladen in
         * Position gewoben und liegt damit im KnotClassLoader — vom kernel-swap unerreichbar.
         * Feuert einmal pro Position.apply (eine kamera pro frame), nicht pro entity in einer
         * schleife, also unkritisch für den brücken-overhead. Logik liegt jetzt in
         * `com.afrobama.blendertransform.PositionFovLogik` und wird bei jedem swap mitgetauscht. */
        MixinBruecke.rufe("PositionFovLogik", "applySprintFov", this, camera);
    }

}
