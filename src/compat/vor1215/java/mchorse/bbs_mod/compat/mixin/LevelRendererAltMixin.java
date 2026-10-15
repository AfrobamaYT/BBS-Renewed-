package mchorse.bbs_mod.compat.mixin;

import mchorse.bbs_mod.compat.CameraRenderStateAlt;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.state.LevelRenderState;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** vor1215: fuellt den Kamerazustand des Bildes zu Beginn von renderLevel (1.21.4-Signatur). */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererAltMixin
{
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void bbs$kameraMatrizen(com.mojang.blaze3d.resource.GraphicsResourceAllocator zuteiler, DeltaTracker takt, boolean umriss,
        Camera kamera, GameRenderer spiel, Matrix4f blick, Matrix4f projektion, CallbackInfo info)
    {
        LevelRenderState welt = LevelRenderState.INSTANZ;
        CameraRenderState zustand = welt.cameraRenderState;

        zustand.pos = kamera.getPosition();
        zustand.blockPos = kamera.getBlockPosition();
        zustand.initialized = kamera.isInitialized();
        zustand.entityPos = kamera.getEntity() == null ? zustand.pos : kamera.getEntity().position();
        zustand.orientation = new org.joml.Quaternionf(kamera.rotation());
        ((CameraRenderStateAlt) zustand).bbs$setMatrizen(projektion, blick, kamera.getXRot(), kamera.getYRot());
        welt.gameTime = spiel.getMinecraft().level == null ? 0L : spiel.getMinecraft().level.getGameTime();
    }
}
