// Coded by Afrobama/Claude — bring BBS's sprint FOV smoothing ramp
// in line with vanilla MC. BBS uses 0.2 step per tick toward target;
// vanilla uses 0.5 step per tick. At 0.2, sprint-start takes ~12
// ticks to reach 95% of target FOV — feels mushy. Vanilla's 0.5
// reaches 95% in 5 ticks, the classic snappy sprint zoom.
//
// We Mixin into updateSprintFov at HEAD, recompute the smoothed
// value ourselves with vanilla's 0.5 factor, and cancel the original
// method. Same field writes (lastSprintFovFactor + isSprinting +
// sprintFovFactor) so downstream consumers see consistent state.
package com.afrobama.bbstools.client.mixin.bbs;

import com.afrobama.bbstools.client.blender.MixinBruecke;

import mchorse.bbs_mod.camera.clips.misc.TrackerClientClip;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TrackerClientClip.class)
public abstract class TrackerSprintRampMixin
{
    @Inject(method = "updateSprintFov", at = @At("HEAD"), cancellable = true)
    private static void bbstools$vanillaRamp(boolean entitySprinting, int tick, CallbackInfo ci)
    {
        /* BRÜCKEN-MUSTER (2026-07-26): der rumpf dieses handlers wird beim klassenladen in
         * TrackerClientClip gewoben und liegt damit im KnotClassLoader — vom kernel-swap
         * unerreichbar. Feuert einmal pro tick, nicht pro entity in einer schleife, also
         * unkritisch für den brücken-overhead. Logik liegt jetzt in
         * `com.afrobama.blendertransform.TrackerSprintRampLogik` und wird bei jedem swap
         * mitgetauscht. */
        if (MixinBruecke.rufeUndAbbrechen("TrackerSprintRampLogik", "vanillaRamp",
            entitySprinting, tick))
        {
            ci.cancel();
        }
    }

}
