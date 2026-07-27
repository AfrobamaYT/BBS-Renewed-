// Coded by Afrobama/Claude — Army System: throttle the editor per-tick entity re-apply.
//
// THE shift-select-all / parked-editor freeze fix. BBS's
// FilmEditorController.updateEntities (called every client tick from
// UIFilmController.update → onEndTick) re-applies EVERY replay's keyframes via
// applyReplay → ReplayKeyframes.apply, which interpolates + sets 15 equipment
// slots (mainHand/offHand/4 armor/9 hotbar) PLUS position/rotation per entity.
// At 3000 replays that's ~75k keyframe interpolations EVERY tick → the editor
// freezes (confirmed via render-thread dump: updateEntities → applyReplay →
// ReplayKeyframes.apply → StubEntity.setEquipmentStack). canUpdate only short-
// circuits when this.paused is true; parked-but-not-"paused" still re-applies.
//
// Re-applying the SAME cursor tick is IDEMPOTENT (keyframes at tick T always
// produce the same entity state), so while the film tick isn't advancing and
// playback isn't running we skip the whole 3000-entity re-apply. getTick()
// returns cursor + (running ? 1 : 0): it advances during playback (→ never
// skip → smooth), and is stable when parked (→ skip). A 500ms safety window
// forces a periodic re-apply so a keyframe edited while parked still previews.
// Rendering is independent (cached bones), so a parked editor stays full-fps.
//
// Default ON (ENABLE_EDITOR_UPDATE_THROTTLE). Touches only the parked/idle
// path; playback and scrubbing (cursor moving) run the full update unchanged.
package com.afrobama.bbstools.client.mixin.bbs;

import com.afrobama.bbstools.client.blender.MixinBruecke;

import mchorse.bbs_mod.ui.film.controller.FilmEditorController;
import mchorse.bbs_mod.ui.film.controller.UIFilmController;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FilmEditorController.class, remap = false)
public abstract class FilmEditorControllerUpdateThrottleMixin
{
    /* @Shadow bleibt stehen: die kernel-logik liest/schreibt "controller" trotzdem über
     * MixinBruecke.feldLesen (loader-unabhängig), aber die deklaration hier hält den
     * mixin-annotation-prozessor zufrieden und dokumentiert das feld. */
    @Shadow public UIFilmController controller;

    @Shadow public abstract int getTick();

    @Unique private int bbstools$lastAppliedTick = Integer.MIN_VALUE;
    @Unique private long bbstools$lastApplyMs = 0L;

    /* BRÜCKEN-MUSTER (2026-07-26): siehe MixinBruecke-javadoc. Logik liegt in
     * com.afrobama.blendertransform.UpdateThrottleLogik und ist damit hotswapbar; nur dieser
     * einzeiler wird beim klassenladen gewoben. */
    @Inject(method = "updateEntities", at = @At("HEAD"), cancellable = true, remap = false)
    private void bbstools$throttleUpdate(int ticks, CallbackInfo ci)
    {
        if (MixinBruecke.rufeUndAbbrechen("UpdateThrottleLogik", "throttleUpdate", this))
        {
            ci.cancel();
        }
    }

}
