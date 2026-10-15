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
//
// ⚠ MERGE fsgh/2.5 (2026-07-28): upstream hat applyActions aus render3D in die neue
// private methode evaluateChannels gezogen (rest → actions → pose). Der alte redirect
// zeigte auf einen aufruf, den es in render3D nicht mehr gibt: 0 ziele, und die klasse
// riss beim ERSTEN modell-render den client mit — der user hatte nur das fenster
// vergroessert. Jetzt haengt der redirect am aufruf von evaluateChannels, denn nur dort
// ist der FormRenderingContext greifbar, den die pick-abfrage braucht.
//
// ⚠ WENN UPSTREAM evaluateChannels ERWEITERT: der ueberspringende zweig unten baut die
// methode ohne applyActions nach (resetPose + applyPose). Kommt dort ein dritter schritt
// dazu, fehlt er hier — dann muss diese stelle mitgezogen werden. tools/mixin-ziele.py
// meldet ein VERSCHWUNDENES ziel, nicht ein erweitertes.
package com.afrobama.bbstools.client.mixin.bbs;

import com.afrobama.bbstools.client.blender.MixinBruecke;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = ModelFormRenderer.class, remap = false)
public abstract class ModelFormRendererPickerSkipAnimateMixin
{
    @Shadow
    private void evaluateChannels(IEntity entity, ModelInstance model, float transition)
    {
        throw new AssertionError("mixin shadow");
    }

    @Redirect(
        method = "render3D",
        at = @At(value = "INVOKE",
            target = "Lmchorse/bbs_mod/forms/renderers/ModelFormRenderer;evaluateChannels(Lmchorse/bbs_mod/forms/entities/IEntity;Lmchorse/bbs_mod/cubic/ModelInstance;F)V"),
        remap = false)
    private void bbstools$skipAnimateForPicker(ModelFormRenderer self, IEntity entity, ModelInstance model, float transition,
                                               FormRenderingContext context)
    {
        /* BRÜCKEN-MUSTER (Aufgabe #56): die logik liegt in
         * com.afrobama.blendertransform.ModelFormRendererPickerSkipAnimateLogik und ist
         * damit hotswapbar. Hier steht nur noch die weiche. */
        if (MixinBruecke.rufeUndAbbrechen("ModelFormRendererPickerSkipAnimateLogik", "ueberspringen",
                self, model, context))
        {
            return;
        }

        this.evaluateChannels(entity, model, transition);
    }

}
