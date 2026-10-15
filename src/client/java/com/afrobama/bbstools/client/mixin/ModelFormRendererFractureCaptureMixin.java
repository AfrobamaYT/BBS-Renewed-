package com.afrobama.bbstools.client.mixin;

import com.afrobama.bbstools.client.blender.MixinBruecke;

import com.afrobama.bbstools.client.fracture.FractureRenderContext;

import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pushes the {@code IEntity} currently being rendered onto
 * {@link FractureRenderContext} at the start of
 * {@link ModelFormRenderer#render3D(FormRenderingContext)} and pops it
 * at the end. Lets the deeper-down BOBJ triangle-filter mixin (which
 * doesn't have direct access to the entity) look up which actor's
 * fractured-bone set to apply.
 *
 * <p>HEAD/TAIL pairing means even if {@code render3D} throws partway
 * through, the TAIL fires (Mixin's @Inject TAIL hooks at every return
 * point including exceptional ones via finally-equivalent). If for
 * some reason it doesn't, the depth-bounded stack in
 * FractureRenderContext is self-healing on world unload.</p>
 */
@Mixin(ModelFormRenderer.class)
public abstract class ModelFormRendererFractureCaptureMixin
{
    @Inject(method = "render3D", at = @At("HEAD"), require = 0)
    private void bbstools$pushFractureContext(FormRenderingContext context, CallbackInfo ci)
    {
        MixinBruecke.rufe("FrakturKontextLogik", "merken", context);
    }

    @Inject(method = "render3D", at = @At("RETURN"), require = 0)
    private void bbstools$popFractureContext(FormRenderingContext context, CallbackInfo ci)
    {
        MixinBruecke.rufe("FrakturKontextLogik", "ablegen");
    }

}
