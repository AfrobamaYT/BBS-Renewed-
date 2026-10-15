package mchorse.bbs_mod.utils.iris;

import com.mojang.blaze3d.textures.GpuTexture;
import mchorse.bbs_mod.client.BBSRendering;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.minecraft.client.Minecraft;

/** Rebuild against the active world target once, rather than from the editor's GUI phase. */
public final class IrisFrameTarget
{
    private static GpuTexture target;
    private static WorldRenderingPipeline pipeline;

    private IrisFrameTarget()
    {}

    public static void sync()
    {
        if (!BBSRendering.isIrisShadersEnabled())
        {
            target = null;
            pipeline = null;
            return;
        }

        GpuTexture active = Minecraft.getInstance().gameRenderer.mainRenderTarget().getColorTexture();
        WorldRenderingPipeline current = Iris.getPipelineManager().getPipelineNullable();

        if (target == null && !BBSRendering.canReplaceFramebuffer()) return;
        if (target == active && pipeline == current) return;

        try
        {
            Iris.reload();
            Iris.getPipelineManager().preparePipeline(Iris.getCurrentDimension());
            target = active;
            pipeline = Iris.getPipelineManager().getPipelineNullable();
        }
        catch (java.io.IOException failure)
        {
            throw new IllegalStateException("Cannot rebuild Iris for the active BBS render target", failure);
        }
    }
}
