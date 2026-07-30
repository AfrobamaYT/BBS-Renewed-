// Coded by Afrobama/Claude — Army System: throttle the editor stencil picker.
//
// THE "mouse-in-3D = 50fps" + "select-all = freeze" fix. BBS's
// UIFilmController.renderStencil runs EVERY frame the cursor is over the 3D
// viewport: it renders forms into an offscreen stencil framebuffer for
// hover/click detection and then does stencil.pick(x,y) = a glReadPixels →
// GPU→CPU sync STALL every frame (that's why fps drops only while the mouse is
// in the scene, and recovers over the UI). Its alt branch renders the ENTIRE
// crowd (3000 forms) to the pick buffer → 0fps freeze on select-all.
//
// The pick RESULT can only change when the cursor MOVES (the readback samples
// one pixel at the cursor). So we re-run renderStencil only when the cursor
// moved, the alt state changed, or a 1s safety window elapsed (covers forms
// drifting under a stationary cursor during playback). A stationary cursor
// reuses the last pick — and a click without moving still resolves to the same
// form, so selection is unaffected. The stencil framebuffer keeps its last
// contents, so any highlight drawn from it is identical while stationary (no
// flicker).
//
// Default ON (ENABLE_STENCIL_PICK_THROTTLE). Pure win: skips only redundant
// per-frame pick work; correctness of picking is preserved.
package com.afrobama.bbstools.client.mixin.bbs;

import com.afrobama.bbstools.client.blender.MixinBruecke;

import mchorse.bbs_mod.ui.film.controller.UIFilmController;
import mchorse.bbs_mod.ui.framework.UIContext;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = UIFilmController.class, remap = false)
public abstract class UIFilmControllerStencilThrottleMixin
{
    /* @Unique-felder bleiben stehen (werden beim weben zu echten instanzfeldern der
     * zielklasse) — die kernel-logik liest/schreibt sie über MixinBruecke.feldLesen/
     * feldSchreiben auf DIESER instanz, nicht in einer kernel-globalen map. */
    @Unique private int bbstools$lastMouseX = Integer.MIN_VALUE;
    @Unique private int bbstools$lastMouseY = Integer.MIN_VALUE;
    @Unique private boolean bbstools$lastAlt = false;
    @Unique private long bbstools$lastRunMs = 0L;

    @Inject(method = "renderStencil", at = @At("HEAD"), cancellable = true, remap = false)
    private void bbstools$throttlePick(LevelRenderContext renderContext, UIContext context, boolean altPressed, CallbackInfo ci)
    {
        /* BRÜCKEN-MUSTER (2026-07-26): logik liegt in
         * com.afrobama.blendertransform.UIFilmControllerStencilThrottleLogik. */
        if (MixinBruecke.rufeUndAbbrechen("UIFilmControllerStencilThrottleLogik", "throttlePick",
                this, context, altPressed))
        {
            ci.cancel();
        }
    }

}
