// Coded by Afrobama/Claude — #4 sub-profiling: descent wall time.
//
// Times the OUTER FormUtilsClient.render(Form, FormRenderingContext) call
// per replay. This window covers everything our fast-path costs inside the
// BBS render loop: the descent FormRenderer.render → ModelFormRenderer
// .render3D HEAD (where our classify + Submission build + FastPathBatch
// .submit run, then we cancel) → renderBodyParts. Body-part recursion
// re-enters FormUtilsClient.render, so a depth counter ensures we only
// accumulate the outermost (per-replay) wall time once — nested body-part
// time is naturally included in the outer window.
//
// Subtract this + shadow from render-loop to isolate BBS's renderEntity
// prework (matrix alloc + 2× world.getLightLevel + FormRenderingContext).
package com.afrobama.bbstools.client.mixin.bbs;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import com.afrobama.bbstools.perf.BBSPerfConfig;

import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.invoke.MethodHandle;

@Mixin(value = FormUtilsClient.class, remap = false)
public abstract class FormRenderDescentTimingMixin
{
    /* Rekursions-tiefe bleibt lokal — reiner zähler, keine fachlogik (siehe klassen-header).
     * Der nanoTime/accum-teil liegt hinter der brücke. */
    private static int  bbstools$descDepth   = 0;
    private static long bbstools$descStartNs = 0L;

    /* BRÜCKEN-MUSTER, PRO-ENTITY-VARIANTE (2026-07-26) — siehe ModelShadowTimingMixin für die
     * volle begründung. Griff pro frame, nie über frame-grenzen gecacht. */
    private static long bbstools$griffFrameStamp = Long.MIN_VALUE;
    private static MethodHandle bbstools$enterGriff;
    private static MethodHandle bbstools$exitGriff;

    @Inject(
        method = "render(Lmchorse/bbs_mod/forms/forms/Form;Lmchorse/bbs_mod/forms/renderers/FormRenderingContext;)V",
        at = @At("HEAD"),
        remap = false)
    private static void bbstools$descentEnter(Form form, FormRenderingContext context, CallbackInfo ci)
    {
        bbstools$refreshGriffe();

        if (bbstools$descDepth == 0)
        {
            bbstools$descStartNs = bbstools$callEnter();
        }
        bbstools$descDepth++;
    }

    @Inject(
        method = "render(Lmchorse/bbs_mod/forms/forms/Form;Lmchorse/bbs_mod/forms/renderers/FormRenderingContext;)V",
        at = @At("RETURN"),
        remap = false)
    private static void bbstools$descentExit(Form form, FormRenderingContext context, CallbackInfo ci)
    {
        bbstools$refreshGriffe();

        bbstools$descDepth--;
        if (bbstools$descDepth <= 0)
        {
            bbstools$descDepth = 0;
            bbstools$callExit(bbstools$descStartNs);
        }
    }

    private static long bbstools$callEnter()
    {
        try
        {
            return bbstools$enterGriff != null ? (long) bbstools$enterGriff.invoke() : System.nanoTime();
        }
        catch (Throwable ignored) { return System.nanoTime(); }
    }

    private static void bbstools$callExit(long startNs)
    {
        try
        {
            if (bbstools$exitGriff != null) bbstools$exitGriff.invoke(startNs);
        }
        catch (Throwable ignored) { /* siehe MixinBruecke-javadoc: fehler = frame auslassen */ }
    }

    /** Holt beide griffe neu, sobald ein neuer render-frame begonnen hat. */
    private static void bbstools$refreshGriffe()
    {
        long stamp = BBSPerfConfig.bbsRenderHeadNs;

        if (stamp != bbstools$griffFrameStamp)
        {
            bbstools$griffFrameStamp = stamp;
            bbstools$enterGriff = MixinBruecke.griff("FormRenderDescentTimingLogik", "enter", 0);
            bbstools$exitGriff  = MixinBruecke.griff("FormRenderDescentTimingLogik", "exit", 1);
        }
    }

}
