// Coded by Afrobama/Claude
package com.afrobama.bbstools.client.mixin;

import com.afrobama.bbstools.client.blender.MixinBruecke;

import mchorse.bbs_mod.forms.FormCategories;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Inject a "Camera" entry into BBS's form picker so the user can pick
 * a camera-style replay directly from the picker — instead of the
 * multi-step manual workflow (add replay → uncheck actor → close form
 * picker without picking).
 *
 * <p>Implementation: at the TAIL of {@link FormCategories#setup()} we
 * grab the "extra" category from {@link ExtraFormSection} and append
 * an {@link AnchorForm} with name="Camera". AnchorForm has no visible
 * mesh, so a replay using it renders nothing — exactly what a camera
 * replay should be. The companion change in
 * {@code EditorCamHandler.isActorReplay} treats any AnchorForm-attached
 * replay as a camera replay so F8 picks it up.</p>
 *
 * <p>BBS clones the picked form via {@code Form.copy()} into the
 * replay's own form field; the original instance we add stays as a
 * template in the category list. So this lives once per JVM.</p>
 */
@Mixin(value = FormCategories.class, remap = false)
public abstract class FormCategoriesAddCameraMixin
{
    @Inject(method = "setup", at = @At("TAIL"), remap = false)
    private void bbstools$injectCameraIntoExtraCategory(CallbackInfo ci)
    {
        /* BRÜCKEN-MUSTER (2026-07-26): logik liegt in
         * com.afrobama.blendertransform.FormCategoriesAddCameraLogik und ist damit
         * hotswapbar — siehe MixinBruecke-javadoc. */
        MixinBruecke.rufe("FormCategoriesAddCameraLogik", "injectCameraIntoExtraCategory",
            (FormCategories)(Object) this);
    }

}
