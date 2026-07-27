// Coded by Afrobama/Claude — re-enable vanilla sprint FOV multiplier in BBS editor mode
package com.afrobama.bbstools.client.mixin;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import com.afrobama.bbstools.client.editormode.EditorModeClient;
import com.afrobama.bbstools.editormode.EditorMode;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Re-applies vanilla's sprint FOV multiplier while the user is in BBS
 * editor mode.
 *
 * <p>Vanilla MC pipes the sprint visual boost through
 * {@code GameRenderer.getFov(Camera, float, boolean)} — the third
 * boolean (changingFov) controls whether the smoothed
 * {@code fovMultiplier}/{@code lastFovMultiplier} fields modulate the
 * returned FOV. Upstream BBS replaces the entire return value of
 * {@code getFov} whenever its CameraController is active (see
 * {@code mchorse.bbs_mod.mixin.client.GameRendererMixin#onGetFov}), so
 * the sprint cue is silently dropped during cinematic playback and
 * editor scrubbing.</p>
 *
 * <p>This mixin sits on the same {@code @At("RETURN")} target with a
 * higher Mixin priority (2000 vs the default 1000) so it runs AFTER
 * upstream's injector — when both injectors fire on RETURN, the one
 * with the larger priority value is applied last and therefore sees /
 * wraps the value the earlier injector wrote via
 * {@code info.setReturnValue}.</p>
 *
 * <p>Guard policy:</p>
 * <ul>
 *   <li>Bail when the third-person zoom multiplier is disabled
 *       ({@code changingFov == false}) — vanilla itself skips the
 *       sprint boost in that case (item-zoom, map-render, etc.).</li>
 *   <li>Bail when the local player is null or not sprinting — nothing
 *       to amplify.</li>
 *   <li>Bail unless we're in editor mode (covers the explicit
 *       {@link EditorMode#ACTIVE} toggle and the implicit
 *       "Add-Camera clip active in a BBS dashboard" preview state).
 *       Outside editor mode upstream BBS handles things correctly.</li>
 *   <li>Bail if BBS is rendering a video export — we don't want to
 *       perturb a frame that's being baked to disk.</li>
 * </ul>
 *
 * <p>The smoothing curve mirrors vanilla
 * {@code GameRenderer.updateFovMultiplier}: target = player's
 * {@code getFovMultiplier()} (the same hook vanilla reads), and we
 * exponentially approach it at {@code 0.5} per render call. This stays
 * close to the look of unmodded sprint FOV without us needing to
 * reflectively poke private fields on {@link GameRenderer}.</p>
 */
@Mixin(value = GameRenderer.class, priority = 2000)
public abstract class SprintFOVMixin
{
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true, require = 0)
    private void bbstools$reapplySprintFov(net.minecraft.client.render.Camera camera,
                                            float tickDelta,
                                            boolean changingFov,
                                            CallbackInfoReturnable<Double> info)
    {
        /* BRÜCKEN-MUSTER (2026-07-26): dieser handler wird beim klassenladen in GameRenderer
         * gewoben und liegt damit im KnotClassLoader — vom kernel-swap unerreichbar. Die
         * gesamte smoothing-logik (inkl. mc/player-zugriff) liegt jetzt in
         * `com.afrobama.blendertransform.SprintFovLogik` und wird bei jedem swap mitgetauscht. */
        Object neu = MixinBruecke.rufe("SprintFovLogik", "apply",
            MinecraftClient.getInstance(), changingFov, tickDelta, info.getReturnValue());

        if (neu instanceof Double) info.setReturnValue((Double) neu);
    }

}
