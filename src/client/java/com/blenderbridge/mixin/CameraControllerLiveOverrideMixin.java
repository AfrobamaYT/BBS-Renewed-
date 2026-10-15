// Coded by Afrobama/Claude — Blender<->BBS live-sync bridge (Phase 1 camera bridge).
package com.blenderbridge.mixin;


import com.afrobama.bbstools.client.blender.MixinBruecke;

import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.controller.CameraController;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Re-stamps the live Blender camera pose onto the BBS camera every frame.
 *
 * <p>WHY HERE: {@link CameraController#setup(Camera, float)} is the universal per-frame camera
 * funnel — it delegates to the current {@code ICameraController} (Play/Runner/Orbit), which
 * writes the frame's final position/rotation/fov into the shared {@link Camera}. It is called
 * once per render frame by {@code CameraMixin.onUpdate} immediately before the vanilla MC camera
 * copies those values. Injecting at {@code RETURN} runs AFTER the editor/play controller has
 * written the camera, so overwriting it here is the LAST write before the render path reads it —
 * the editor cannot clobber the Blender pose back.</p>
 *
 * <p>WHILE {@link LiveCameraOverride#isActive()} (armed by an inbound Blender CAMERA frame via
 * {@link CameraBridge#applyCamera}), copy the parked BBS-space pose onto the camera. When not
 * active this is a single volatile read and returns immediately, so the normal editor camera is
 * untouched.</p>
 */
@Mixin(CameraController.class)
public abstract class CameraControllerLiveOverrideMixin
{
    /* BRÜCKEN-MUSTER (Aufgabe #56): logik in
     * com.afrobama.blendertransform.LiveCameraOverrideLogik. Der zustand bleibt in
     * com.blenderbridge.LiveCameraOverride — der kernel-loader ist nur fuer die beiden
     * kernel-pakete kind-zuerst, alles andere kommt vom elternlader und ist damit die
     * eine, geteilte kopie. */
    @Inject(method = "setup", at = @At("RETURN"))
    private void blenderbridge$overrideLiveCamera(Camera camera, float transition, CallbackInfo ci)
    {
        MixinBruecke.rufe("LiveCameraOverrideLogik", "uebernehmen", camera);
    }
}
