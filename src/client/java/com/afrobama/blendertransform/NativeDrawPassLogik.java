package com.afrobama.blendertransform;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.commands.RenderPass;
import mchorse.bbs_mod.graphics.GpuBackend;
import mchorse.bbs_mod.graphics.NativeDrawPassScope;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;

/** Keep the real solid pass available to BBS callbacks without claiming ownership. */
public final class NativeDrawPassLogik
{
    private NativeDrawPassLogik()
    {}

    public static void beginNativeFrame()
    {
        com.afrobama.bbstools.client.bbs.fastpath.Tier2hInstancedDraw.beginNativeFrame();
    }

    public static void executeSolid(ChunkSectionsToRender sections,
        FeatureRenderDispatcher.PreparedFrame prepared, RenderPass pass, Operation<Void> original)
    {
        if (!GpuBackend.isVulkan())
        {
            original.call(sections, prepared, pass);
            return;
        }

        /* LevelRenderer.render imports GameRenderer.mainRenderTarget into the framegraph;
         * its opaque pass uses that same color+depth pair in 26.3. */
        RenderTarget target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        try (NativeDrawPassScope scope = NativeDrawPassScope.enter(pass,
            target.getColorTextureView(), target.getDepthTextureView()))
        {
            original.call(sections, prepared, pass);
            com.afrobama.bbstools.client.bbs.fastpath.Tier2hInstancedDraw.drawNativeDeferred(pass);
        }
    }
}
