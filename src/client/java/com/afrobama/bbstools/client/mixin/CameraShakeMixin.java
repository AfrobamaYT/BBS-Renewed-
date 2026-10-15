package com.afrobama.bbstools.client.mixin;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import com.afrobama.bbstools.client.fracture.CameraShakeManager;
import net.minecraft.client.Camera;
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
    /* BRÜCKEN-MUSTER (Aufgabe #56): logik in
     * com.afrobama.blendertransform.CameraShakeLogik, damit sie hotswapbar ist.
     *
     * ⛔ RÜCKFALL AUF DEN UNVERÄNDERTEN WERT, nicht auf 0: liefert die brücke null (klasse
     * oder methode nicht auflösbar), muss die kamera stehen wie ohne feature. Ein
     * `(Float) ergebnis` würde dort eine NullPointerException pro frame werfen. */
    @ModifyVariable(method = "setRotation(FF)V", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private float bbstools$shakeYaw(float yaw)
    {
        Object ergebnis = MixinBruecke.rufe("CameraShakeLogik", "yaw", yaw);

        return ergebnis instanceof Float ? (Float) ergebnis : yaw;
    }

    @ModifyVariable(method = "setRotation(FF)V", at = @At("HEAD"), ordinal = 1, argsOnly = true)
    private float bbstools$shakePitch(float pitch)
    {
        Object ergebnis = MixinBruecke.rufe("CameraShakeLogik", "pitch", pitch);

        return ergebnis instanceof Float ? (Float) ergebnis : pitch;
    }

}
