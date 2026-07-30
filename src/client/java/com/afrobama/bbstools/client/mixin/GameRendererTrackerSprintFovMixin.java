// Coded by Afrobama/Claude — apply sprint FOV multiplier during BBS
// replay playback in editor mode. ITER 24 REWRITE: bypass upstream's
// TrackerClientClip.updateSprintFov pipeline entirely (it's not being
// fed in user's BBS-CML setup — log confirmed isSprinting stays
// false on the static state field). Instead read
// TrackerClientClip.currentEntity.isSprinting() directly each frame
// and synthesize our own smoothed multiplier. This works as long as
// some part of BBS sets currentEntity to the replay's StubEntity
// (which it does — applyClip line 1036 of upstream).
package com.afrobama.bbstools.client.mixin;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GameRenderer.class, priority = 3000)
public abstract class GameRendererTrackerSprintFovMixin
{
    /* Manual override lives in tracker.SprintFovState — Mixins can't
     *  have non-private static fields. */

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void bbstools$applyTrackerSprintFov(net.minecraft.client.Camera camera,
        float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir)
    {
        /* BRÜCKEN-MUSTER (2026-07-26): dieser handler wird beim klassenladen in GameRenderer
         * gewoben und liegt damit im KnotClassLoader — vom kernel-swap unerreichbar. Die
         * reflection-probe + die gesamte smoothing-logik liegen jetzt in
         * `com.afrobama.blendertransform.SprintFovTrackerLogik` und werden bei jedem swap
         * mitgetauscht. */
        Object neu = MixinBruecke.rufe("SprintFovTrackerLogik", "apply",
            com.afrobama.bbstools.client.tracker.SprintFovState.MANUAL_SPRINT,
            net.minecraft.client.Minecraft.getInstance(), tickDelta, cir.getReturnValue());

        if (neu instanceof Double) cir.setReturnValue((Double) neu);
    }

}
