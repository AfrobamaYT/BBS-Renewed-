// Coded by Afrobama/Claude — Phase 15 v3: world-skip alt-preview, grid + replays only
package com.afrobama.bbstools.client.mixin;

import com.afrobama.bbstools.client.blender.MixinBruecke;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Phase 15 v3 — World-skip alt-camera preview.
 *
 * <p>Earlier iterations (Phase 12) recursively called
 * {@code mc.gameRenderer.renderWorld(tickCounter)} per active clip. That
 * was correct but cost a full vanilla+Iris world render per camera per
 * frame and BBS replays did not appear in the result (Fabric event
 * re-entry suppression). User feedback explicitly rejected this cost
 * model: cinematic setups can't afford 5-15ms × N-cameras per frame.</p>
 *
 * <p>This rewrite skips the world render entirely. The alt-preview tile
 * shows a black background with a 1-block reference grid at player Y
 * plus BBS film/replay rendering from the alt camera POV. Chunks,
 * shadows, Iris shaders — all bypassed. Cost per camera per frame:
 * one grid quad + however many forms/replays the active films
 * contribute. Typically &lt; 0.5ms.</p>
 *
 * <p>Replays render via {@code BBSRendering#renderCoolStuff} with a
 * manually-prepared {@code WorldRenderContextImpl}, so the same form
 * pipeline that fills the main preview also fills our preview FB.</p>
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererPreviewPrePassMixin
{
    @Inject(method = "renderWorld", at = @At("HEAD"), require = 0)
    private void bbstools$renderClipViewportsBeforeMain(RenderTickCounter tickCounter, CallbackInfo ci)
    {
        /* BRÜCKEN-MUSTER (2026-07-26): dieser handler wird beim klassenladen in GameRenderer
         * gewoben und liegt damit im KnotClassLoader — vom kernel-swap unerreichbar. Die
         * gesamte Phase-15-v3-logik (world-skip alt-camera preview) liegt jetzt in
         * `com.afrobama.blendertransform.GameRendererPreviewPrePassLogik` und wird bei jedem
         * swap mitgetauscht. */
        MixinBruecke.rufe("GameRendererPreviewPrePassLogik", "render", tickCounter);
    }

}
