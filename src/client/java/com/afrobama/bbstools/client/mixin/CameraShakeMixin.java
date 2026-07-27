package com.afrobama.bbstools.client.mixin;

import com.afrobama.bbstools.client.fracture.CameraShakeManager;

import net.minecraft.client.render.Camera;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Cinematic camera-shake hook. Perturbs the rotation argument of
 * {@code Camera.setRotation(float yaw, float pitch)} when the
 * {@link CameraShakeManager} has any active shake amplitude. Layered
 * sin-noise perturbation gives organic feel; amplitude decays per
 * frame so a one-off impact rattles briefly while a sustained crash
 * holds shake longer.
 *
 * <p>We perturb on the method argument level (via
 * {@link ModifyVariable}) rather than on the camera's stored fields
 * — so the shake is applied to the rendering rotation only, not to
 * the player's actual look-rotation. Player's mouse aim is unaffected.</p>
 *
 * <p>Tick-and-noise advance happens inside the YAW callback (called
 * first), so by the time PITCH callback runs the noise time and
 * amplitude have already been refreshed for this frame.</p>
 */
@Mixin(Camera.class)
public class CameraShakeMixin
{
    @ModifyVariable(method = "setRotation(FF)V", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private float bbstools$shakeYaw(float yaw)
    {
        if (!CameraShakeManager.isActive()) return yaw;
        /* Advance the noise time + decay amplitude for this frame.
         *  Done in the yaw hook because it's reliably called before
         *  pitch. */
        CameraShakeManager.tickFrame();
        return yaw + CameraShakeManager.currentYawDelta();
    }

    @ModifyVariable(method = "setRotation(FF)V", at = @At("HEAD"), ordinal = 1, argsOnly = true)
    private float bbstools$shakePitch(float pitch)
    {
        if (!CameraShakeManager.isActive()) return pitch;
        return pitch + CameraShakeManager.currentPitchDelta();
    }

}
