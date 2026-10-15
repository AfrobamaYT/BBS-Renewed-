// Coded by Afrobama/Claude — Phase 17: skip chunk rendering in editor mode
package com.afrobama.bbstools.client.mixin;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.RenderType;
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
/* ⚠️ 26.2 UMGEHAENGT: das Zeichnen der Chunk-Schichten ist von LevelRenderer.renderSectionLayer
 * nach ChunkSectionsToRender.renderGroup gewandert -- ANDERE KLASSE, nicht nur anderer Name.
 *
 * Zwei Dinge aendern sich dadurch inhaltlich:
 * 1. Der Haken bekommt eine GRUPPE (OPAQUE / TRANSLUCENT) statt einer einzelnen Ebene. Die
 *    Trennung SOLID/CUTOUT gibt es hier nicht mehr -- beide stecken in OPAQUE. Fuer diesen
 *    Haken egal: er sprang ohnehin ALLE Ebenen.
 * 2. Das require=0 stand hier, weil das Ziel schon vorher wacklig war. Es bleibt -- aber es
 *    ist jetzt eine echte Zusicherung und keine Ausrede mehr: das Ziel EXISTIERT wieder
 *    (gegen das 26.2-JAR geprueft). */
@Mixin(ChunkSectionsToRender.class)
public abstract class EditorModeChunkSkipMixin
{
    @Inject(method = "renderGroup", at = @At("HEAD"), cancellable = true, require = 0)
    public void bbstools$skipChunksInEditorMode(ChunkSectionLayerGroup gruppe,
        //? if >=26.3 {
        /*com.mojang.blaze3d.systems.RenderPass pass,
        com.mojang.blaze3d.textures.GpuSampler abtaster,
        com.mojang.blaze3d.textures.GpuTextureView textur, boolean tiefenpass, CallbackInfo ci)
        *///?} else {
                                                 com.mojang.blaze3d.textures.GpuSampler abtaster,
                                                 CallbackInfo ci)
        //?}
    {
        /* BRÜCKEN-MUSTER (2026-07-26): logik liegt in
         * com.afrobama.blendertransform.EditorModeChunkSkipLogik und ist damit
         * hotswapbar — siehe MixinBruecke-javadoc. */
        if (MixinBruecke.rufeUndAbbrechen("EditorModeChunkSkipLogik", "shouldSkipChunks")) ci.cancel();
    }

}
