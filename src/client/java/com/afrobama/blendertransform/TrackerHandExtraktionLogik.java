package com.afrobama.blendertransform;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.clips.misc.TrackerClientClip;
import mchorse.bbs_mod.camera.controller.CameraWorkCameraController;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.state.level.PlayerRenderState;

/** Scopes tracked inventory/cache state around vanilla's 26.3 player-state extraction. */
public final class TrackerHandExtraktionLogik
{
    public static void extrahieren(Camera camera, DeltaTracker delta, float partialTick,
        PlayerRenderState playerState, Operation<Void> original)
    {
        boolean tracked = BBSModClient.getCameraController().getCurrent() instanceof CameraWorkCameraController
            && TrackerClientClip.beginPlayerStateExtraction();

        try
        {
            original.call(camera, delta, partialTick, playerState);

            if (tracked)
            {
                TrackerClientClip.applyExtractedHandState(playerState);
            }
        }
        finally
        {
            if (tracked)
            {
                TrackerClientClip.endPlayerStateExtraction();
            }
        }
    }

    private TrackerHandExtraktionLogik()
    {}
}
