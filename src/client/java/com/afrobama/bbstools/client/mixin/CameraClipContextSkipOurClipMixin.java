// Coded by Afrobama/Claude — Phase 15 v9: keep BBSToolsAddCameraClip out of the main camera
package com.afrobama.bbstools.client.mixin;

import com.afrobama.bbstools.client.blender.MixinBruecke;

import mchorse.bbs_mod.camera.clips.CameraClipContext;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.utils.clips.Clip;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * BBS's camera-work pipeline iterates active camera clips and calls
 * {@link CameraClipContext#apply(Clip, Position)} on each to mutate the
 * MAIN camera position. That's the right behavior for vanilla
 * TrackerClip — its job is to drive the main film camera. But our
 * {@link BBSToolsAddCameraClip} extends TrackerClip purely to inherit
 * the property layout (selector/offset/angle/fov/lookAt/…); its actual
 * job is to feed a SECONDARY viewport via the alt-cam pre-pass mixin.
 *
 * <p>Without this mixin, when the user adjusts our clip's angle slider
 * BBS also rotates the main film preview, which makes the slider feel
 * like it rotates "everything" instead of just the alt-cam tile.
 * Skipping {@code apply} for our subclass type keeps the main camera
 * untouched while leaving the alt-cam pre-pass (which reads the clip's
 * fields directly via the registry) fully operational.</p>
 */
@Mixin(CameraClipContext.class)
public abstract class CameraClipContextSkipOurClipMixin
{
    /* BRÜCKEN-MUSTER (Aufgabe #56): logik samt einmal-meldung in
     * com.afrobama.blendertransform.CameraClipContextSkipLogik. */
    @Inject(
        method = "apply(Lmchorse/bbs_mod/utils/clips/Clip;Lmchorse/bbs_mod/camera/data/Position;)Z",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void bbstools$skipOurClip(Clip clip, Position position, CallbackInfoReturnable<Boolean> cir)
    {
        if (MixinBruecke.rufeUndAbbrechen("CameraClipContextSkipLogik", "ueberspringen", clip))
        {
            cir.setReturnValue(false);
        }
    }

}
