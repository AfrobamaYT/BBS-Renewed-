package com.afrobama.bbstools.client.mixin;

import mchorse.bbs_mod.camera.clips.misc.TrackerClientClip;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.ui.film.controller.UIFilmController;
import mchorse.bbs_mod.utils.clips.Clip;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Auto-snaps the editor camera to FIRST_PERSON when the user enters
 * Actor Control AND a {@link TrackerClientClip} is active at the
 * current cursor AND the controlled replay is player-shaped (model
 * path contains "player"). Restores the prior pov on exit.
 *
 * <p>Why the player-shape gate: tracker clips record from the player
 * camera. For player-replays this lets the recorded view match what
 * the user sees while driving the actor. For non-humanoid replays
 * (villager, hay block) first-person on the actor's bone tree
 * produces a wrong eye position; we leave the pov alone instead.</p>
 *
 * <p>This is the Mixin form of the upstream-fork patch in
 * UIFilmController.toggleControl.</p>
 */
@Mixin(UIFilmController.class)
public abstract class UIFilmControllerTrackerPovMixin
{
    @Unique
    private int bbstools$povBeforeTrackerSnap = -1;

    @Unique
    private boolean bbstools$wasControlling;

    @Inject(method = "toggleControl", at = @At("HEAD"), remap = false)
    private void bbstools$captureBefore(CallbackInfo ci)
    {
        /* Logik → com.afrobama.blendertransform.UiFilmControllerTrackerPovLogik. Die felder
         * oben bleiben deklariert (gewoben als echte instanz-felder), damit der kernel sie
         * ueber MixinBruecke.feldLesen/feldSchreiben instanz-treu lesen/schreiben kann. */
        com.afrobama.bbstools.client.blender.MixinBruecke.rufe(
            "UiFilmControllerTrackerPovLogik", "captureBefore", (UIFilmController) (Object) this);
    }

    @Inject(method = "toggleControl", at = @At("TAIL"), remap = false)
    private void bbstools$autoSnapPov(CallbackInfo ci)
    {
        com.afrobama.bbstools.client.blender.MixinBruecke.rufe(
            "UiFilmControllerTrackerPovLogik", "autoSnapPov", (UIFilmController) (Object) this);
    }

}
