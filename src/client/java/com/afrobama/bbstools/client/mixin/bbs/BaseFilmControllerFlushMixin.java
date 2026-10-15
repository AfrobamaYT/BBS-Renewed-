// Coded by Afrobama/Claude — Tier 1 fast-path flush hook.
//
// Injects at RETURN of BaseFilmController.render(WorldRenderContext) to
// call FastPathBatch.flush() after all per-replay submissions for the
// frame have been collected by ModelFormRendererFastPathMixin.
//
// This pairs with that mixin: HEAD-inject classifies + submits, RETURN-
// inject flushes. Per-frame lifecycle:
//   1. BaseFilmController.render enters → iterate replays
//   2. Each replay → FormUtilsClient.render → ModelFormRenderer.render3D
//   3. Our HEAD-inject classifies + submits to FastPathBatch
//   4. BBS continues vanilla render in parallel (Tier 1 measure mode)
//   5. BaseFilmController.render exits → our RETURN-inject calls flush()
package com.afrobama.bbstools.client.mixin.bbs;

import com.afrobama.bbstools.client.bbs.fastpath.FastPathBatch;
import com.afrobama.bbstools.client.blender.MixinBruecke;
import com.afrobama.bbstools.perf.BBSPerfConfig;

import mchorse.bbs_mod.film.BaseFilmController;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BaseFilmController.class, remap = false)
public abstract class BaseFilmControllerFlushMixin
{
    /* #4 profiling — stamp render() entry so flush start can derive the
     * BBS serial per-replay render-loop window (HEAD → flush-start). */
    @Inject(
        method = "render",
        at = @At("HEAD"),
        remap = false)
    private void bbstools$stampRenderHead(WorldRenderContext context, CallbackInfo ci)
    {
        BBSPerfConfig.bbsRenderHeadNs = System.nanoTime();
    }

    @Inject(
        method = "render",
        at = @At("RETURN"),
        remap = false)
    private void bbstools$flushFastPathBatch(WorldRenderContext context, CallbackInfo ci)
    {
        /* BRÜCKEN-MUSTER (2026-07-26): der rumpf dieses handlers wird beim klassenladen in
         * BaseFilmController gewoben und liegt damit im KnotClassLoader — vom kernel-swap
         * unerreichbar. Stünde die logik hier, bräuchte JEDE änderung daran einen boot.
         *
         * Deshalb nur dieser einzeiler; die logik liegt in
         * `com.afrobama.blendertransform.FlushLogik` und wird bei jedem swap mitgetauscht.
         * Das gate `tools/mixin-gate.mjs` hält diese eigenschaft fest — auch für mixins, die
         * es heute noch nicht gibt. */
        MixinBruecke.rufe("FlushLogik", "flush");
    }

}
