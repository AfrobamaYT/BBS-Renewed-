// Coded by Afrobama/Claude — Army System: skip the animator in the editor's
// stencil PICK pass (the multi-select 0-fps fix).
//
// BBS's UIFilmController stencil picker (hover/click detection in the editor)
// renders EVERY selected replay to an offscreen pick framebuffer through the
// full per-form path: ModelFormRenderer.render3D → animator.applyActions. With
// a big crowd selected (e.g. 1500), that's 1500 full animator runs per frame
// (keyframe interp + molang + config resolve = the GeckoAnimationsConfig/Pose
// deep-equals storm in the profiler) → frame-timeline `final` phase = 4.5s →
// 0 fps. The MAIN visible render is unaffected (it's the cached instanced
// bypass; bbs-replays phase = 0ns).
//
// The picker only needs a SILHOUETTE to know which form is under the cursor —
// the animated pose is irrelevant (the form's WORLD position comes from the
// matrix stack, not the bones). So for the stencil pass we skip
// animator.applyActions: the model renders at rest+pose, cheap, invisible to
// the user (pick buffer only), and selection still hits the right form. This is
// independent of the bake cache, so it works for UNIQUE replays too.
//
// Scoped tightly: ONLY when context.stencilMap != null (the pick pass) AND
// ENABLE_ARMY_BAKE — the normal visible render (stencilMap == null) animates
// exactly as before, so nothing the user sees changes.
package com.afrobama.bbstools.client.mixin.bbs;

import com.afrobama.bbstools.perf.BBSPerfConfig;

import mchorse.bbs_mod.cubic.IModelInstance;
import mchorse.bbs_mod.cubic.animation.IAnimator;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = ModelFormRenderer.class, remap = false)
public abstract class ModelFormRendererPickerSkipAnimateMixin
{
    @Redirect(
        method = "render3D",
        at = @At(value = "INVOKE",
            target = "Lmchorse/bbs_mod/cubic/animation/IAnimator;applyActions(Lmchorse/bbs_mod/forms/entities/IEntity;Lmchorse/bbs_mod/cubic/IModelInstance;F)V"),
        remap = false)
    private void bbstools$skipAnimateForPicker(IAnimator animator, IEntity entity, IModelInstance model, float transition,
                                               FormRenderingContext context)
    {
        if (BBSPerfConfig.ENABLE_ARMY_BAKE
            && BBSPerfConfig.ENABLE_PICKER_SKIP_ANIMATE
            && context.stencilMap != null)
        {
            /* Pick pass → rest-pose silhouette is enough; skip the per-form
             * animator (the multi-select 0-fps). Model stays at resetPose;
             * applyPose still runs after this in render3D. */
            BBSPerfConfig.pickerAnimateSkips++;
            return;
        }
        animator.applyActions(entity, model, transition);
    }

}
