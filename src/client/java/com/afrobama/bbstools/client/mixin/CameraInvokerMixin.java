// Coded by Afrobama/Claude — BBS Renewed Camera Preview Phase 2
package com.afrobama.bbstools.client.mixin;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes Camera's package-private mutators so {@code CameraPreviewRenderHook}
 * can re-aim the camera mid-frame to render a second world pass from the
 * preview camera's pose, then restore.
 */
@Mixin(Camera.class)
public interface CameraInvokerMixin
{
    @Invoker("setPosition")
    void bbsRenewed$setPos(double x, double y, double z);

    @Invoker("setRotation")
    void bbsRenewed$setRotation(float yaw, float pitch);
}
