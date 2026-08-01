// Coded by Afrobama/Claude — Tier 1 fast-path hook.
//
// Injects at HEAD of ModelFormRenderer.render3D to classify the replay
// and submit it to the FastPathBatch. Tier 1 = measure-only: ci is
// NEVER cancelled, both code paths run. The cancel flag is the architectural
// plumbing for Tier 1.5 when the actual render replacement lands.
//
// Convergence: this single mixin catches BOTH the replay-stub-entity path
// AND the actor-spawned ActorEntity path because both end up calling
// ModelFormRenderer.render3D via FormUtilsClient.render → FormRenderer.render.
// See research_bbs_render_hooks.md for the full call hierarchy.
package com.afrobama.bbstools.client.mixin.bbs;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import com.afrobama.bbstools.perf.BBSPerfConfig;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.invoke.MethodHandle;

@Mixin(value = ModelFormRenderer.class, remap = false)
public abstract class ModelFormRendererFastPathMixin
{
    /* Per-renderer classify cache. Bleibt mixin-lokal (@Unique, an EINE instanz gebunden) —
     * wird als argument an den kernel gereicht und über den rückgabewert aktualisiert. Siehe
     * ModelFormRendererFastPathLogik-javadoc. */
    @org.spongepowered.asm.mixin.Unique
    private Object bbstools$cachedInstance;
    @org.spongepowered.asm.mixin.Unique
    private Object bbstools$cachedReason;

    /* BRÜCKEN-MUSTER, PRO-ENTITY-VARIANTE (2026-07-26) — siehe ModelShadowTimingMixin für die
     * volle begründung. DER teuerste der sechs mixins: trägt den ganzen crowd-fast-path. */
    @org.spongepowered.asm.mixin.Unique
    private static long bbstools$classifyGriffStamp = Long.MIN_VALUE;
    @org.spongepowered.asm.mixin.Unique
    private static MethodHandle bbstools$classifyGriff;

    @Inject(
        method = "render3D(Lmchorse/bbs_mod/forms/renderers/FormRenderingContext;)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false)
    private void bbstools$fastPathClassify(FormRenderingContext context, CallbackInfo ci)
    {
        bbstools$refreshClassifyGriff();

        if (bbstools$classifyGriff == null) return;

        try
        {
            ModelFormRenderer self = (ModelFormRenderer) (Object) this;

            Object[] result = (Object[]) bbstools$classifyGriff.invoke(
                self, context, this.bbstools$cachedInstance, this.bbstools$cachedReason);

            this.bbstools$cachedInstance = result[1];
            this.bbstools$cachedReason   = result[2];

            if (Boolean.TRUE.equals(result[0])) ci.cancel();
        }
        catch (Throwable ignored)
        {
            /* Non-fatal: vanilla render path continues regardless (siehe MixinBruecke-javadoc). */
        }
    }

    /** Holt den griff neu, sobald ein neuer render-frame begonnen hat. */
    private static void bbstools$refreshClassifyGriff()
    {
        long stamp = BBSPerfConfig.bbsRenderHeadNs;

        if (stamp != bbstools$classifyGriffStamp)
        {
            bbstools$classifyGriffStamp = stamp;
            bbstools$classifyGriff = MixinBruecke.griff("ModelFormRendererFastPathLogik", "classify", 4);
        }
    }

}
