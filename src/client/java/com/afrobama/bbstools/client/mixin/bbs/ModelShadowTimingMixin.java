// Coded by Afrobama/Claude — #4 sub-profiling: blob-shadow buffer-fill time.
//
// BaseFilmController.renderEntity calls ModelBlockEntityRenderer.renderShadow
// once per visible replay (when replay.shadow + shadowRadius > 0). It fills a
// shadow quad into the vertex consumer — CPU work that runs SERIAL inside the
// render loop for every replay. This times it so we know how much of the
// 10ms render-loop is shadow vs prework. renderShadow does not nest, so a
// plain HEAD/RETURN pair (render thread serial) is accurate.
package com.afrobama.bbstools.client.mixin.bbs;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import com.afrobama.bbstools.perf.BBSPerfConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.client.renderer.ModelBlockEntityRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.invoke.MethodHandle;

@Mixin(value = ModelBlockEntityRenderer.class, remap = false)
public abstract class ModelShadowTimingMixin
{
    /* BRÜCKEN-MUSTER, PRO-ENTITY-VARIANTE (2026-07-26): renderShadow feuert bis zu 3000×/frame
     * bei army-scale — MixinBruecke.rufe() JE AUFRUF kostet gemessen +1307ns (siehe
     * BrueckenKosten), das wäre 23,5% eines 16,7ms-frames NUR für den aufrufweg. Deshalb hier
     * MixinBruecke.griff(): EIN lookup pro frame, dann direkter MethodHandle.invoke() pro
     * entity. Frame-grenze erkannt an BBSPerfConfig.bbsRenderHeadNs, das
     * BaseFilmControllerFlushMixin einmal pro frame VOR jedem renderShadow-aufruf stempelt.
     * ⚠ Die griffe dürfen NICHT über frame-grenzen gecacht bleiben — nach einem swap zeigten
     * sie sonst auf eine tote generation. Deshalb neu holen, sobald sich der stempel ändert. */
    private static long bbstools$griffFrameStamp = Long.MIN_VALUE;
    private static MethodHandle bbstools$enterGriff;
    private static MethodHandle bbstools$exitGriff;

    @Inject(
        method = "renderShadow(Lnet/minecraft/client/renderer/SubmitNodeCollector;Lcom/mojang/blaze3d/vertex/PoseStack;FDDDFFFFF)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false)
    private static void bbstools$shadowEnter(SubmitNodeCollector provider, PoseStack matrices,
        float tickDelta, double x, double y, double z, float tx, float ty, float tz,
        float radius, float opacity, CallbackInfo ci)
    {
        bbstools$refreshGriffe();

        try
        {
            if (bbstools$enterGriff != null && (boolean) bbstools$enterGriff.invoke()) ci.cancel();
        }
        catch (Throwable ignored) { /* siehe MixinBruecke-javadoc: fehler = frame auslassen */ }
    }

    @Inject(
        method = "renderShadow(Lnet/minecraft/client/renderer/SubmitNodeCollector;Lcom/mojang/blaze3d/vertex/PoseStack;FDDDFFFFF)V",
        at = @At("RETURN"),
        remap = false)
    private static void bbstools$shadowExit(SubmitNodeCollector provider, PoseStack matrices,
        float tickDelta, double x, double y, double z, float tx, float ty, float tz,
        float radius, float opacity, CallbackInfo ci)
    {
        bbstools$refreshGriffe();

        try
        {
            if (bbstools$exitGriff != null) bbstools$exitGriff.invoke();
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
            bbstools$enterGriff = MixinBruecke.griff("ModelShadowTimingLogik", "enter", 0);
            bbstools$exitGriff  = MixinBruecke.griff("ModelShadowTimingLogik", "exit", 0);
        }
    }

}
