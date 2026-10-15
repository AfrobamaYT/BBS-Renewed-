/* Coded by Afrobama/Claude */
package com.afrobama.bbstools.client.bbs.fastpath;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;

/**
 * One replay that qualifies for the render-loop bypass, with everything the bypass needs resolved
 * up front (index, replay, entity, renderer, model instance, form).
 *
 * WHY IT LIVES HERE AND NOT IN THE MIXIN: this was a nested record inside
 * BaseFilmControllerRenderLoopBypassMixin, i.e. inside {@code com.afrobama.bbstools.client.mixin.*}.
 * Mixin packages are off-limits to the class loader for direct references — anything in them is
 * meant to be woven away, never loaded as an ordinary class. The mixin's woven code lives in
 * BaseFilmController, and the moment that code touched the record the loader threw:
 *
 *   IllegalClassLoadError: ...RenderLoopBypassMixin$Eligible is in a defined mixin package
 *   com.afrobama.bbstools.client.mixin.* owned by bbstools.client.mixins.json and cannot be
 *   referenced directly
 *
 * That crashed the game on the very first createEntities() — i.e. as soon as a film was created
 * (crash-2026-07-24_20.03.51). The gate flag could not prevent it either: the field type
 * {@code List<Eligible>} forces the load during verification, before any gate is read.
 *
 * A plain class in a normal package has no such restriction, so the record moved out and the mixin
 * only references it by import.
 */
public record EligibleReplay(int i, Replay replay, IEntity entity,
    ModelFormRenderer renderer, ModelInstance instance, ModelForm form)
{}
