package mchorse.bbs_mod.compat.mixin;

import mchorse.bbs_mod.compat.CameraRenderStateAlt;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.LevelRenderState;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stores the frame's view rotation, projection and camera angles on the camera state at the start
 * of renderLevel (GameRenderer.renderLevel passes view rotation, projection, culling projection),
 * where 26.2 extracts them into its CameraRenderState.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererAltMixin
{
    @Shadow
    @Final
    private LevelRenderState levelRenderState;

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void bbs$kameraMatrizen(com.mojang.blaze3d.resource.GraphicsResourceAllocator zuteiler,
        net.minecraft.client.DeltaTracker takt, boolean umriss, Camera kamera, Matrix4f blick, Matrix4f projektion,
        Matrix4f culling, com.mojang.blaze3d.buffers.GpuBufferSlice nebel, org.joml.Vector4f nebelFarbe, boolean himmel, CallbackInfo info)
    {
        ((CameraRenderStateAlt) this.levelRenderState.cameraRenderState).bbs$setMatrizen(projektion, blick, kamera.xRot(), kamera.yRot());
    }
}
