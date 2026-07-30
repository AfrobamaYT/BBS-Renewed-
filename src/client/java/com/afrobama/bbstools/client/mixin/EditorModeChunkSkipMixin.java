// Coded by Afrobama/Claude — Phase 17: skip chunk rendering in editor mode
package com.afrobama.bbstools.client.mixin;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * When editor-mode is active and BBS is NOT doing a video-export render,
 * cancel {@link LevelRenderer#renderSectionLayer} so terrain chunks don't draw.
 * Result: in-game view is a void background, but the user's scene
 * elements (replays, forms, mobs, etc.) keep rendering because those go
 * through different code paths.
 *
 * <p>The auto-self-disable during BBS export ({@link
 * EditorModeClient#shouldBypassWorld()}) means the recorded video keeps
 * all chunks — only the live preview loses them.</p>
 */
@Mixin(LevelRenderer.class)
public abstract class EditorModeChunkSkipMixin
{
    @Inject(method = "renderSectionLayer", at = @At("HEAD"), cancellable = true, require = 0)
    public void bbstools$skipChunksInEditorMode(RenderType renderLayer,
                                                 double cameraX, double cameraY, double cameraZ,
                                                 Matrix4f positionMatrix, Matrix4f projectionMatrix,
                                                 CallbackInfo ci)
    {
        /* BRÜCKEN-MUSTER (2026-07-26): logik liegt in
         * com.afrobama.blendertransform.EditorModeChunkSkipLogik und ist damit
         * hotswapbar — siehe MixinBruecke-javadoc. */
        if (MixinBruecke.rufeUndAbbrechen("EditorModeChunkSkipLogik", "shouldSkipChunks")) ci.cancel();
    }

}
