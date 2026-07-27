// Coded by Afrobama/Claude — Army System: stash the active film controller.
//
// BaseFilmController.update() runs every tick while a film is loaded (editor
// idle OR playing). We stash `this` so BackgroundBaker (which runs on the
// client tick, decoupled from the render-loop bypass) knows which controller's
// entities to bake — the bypass only ran during playback, so it couldn't drive
// AFK baking.
package com.afrobama.bbstools.client.mixin.bbs;

import com.afrobama.bbstools.client.bbs.fastpath.BackgroundBaker;

import mchorse.bbs_mod.film.BaseFilmController;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BaseFilmController.class, remap = false)
public abstract class BaseFilmControllerActiveStashMixin
{
    @Inject(method = "update", at = @At("HEAD"), remap = false)
    private void bbstools$stashActive(CallbackInfo ci)
    {
        BackgroundBaker.setActive((BaseFilmController) (Object) this);
    }

}
