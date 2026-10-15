package mchorse.bbs_mod.compat;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import mchorse.bbs_mod.compat.alt.BindGroupLayout;
import mchorse.bbs_mod.compat.alt.ColorTargetState;
import mchorse.bbs_mod.compat.alt.DepthStencilState;

/**
 * 26.2's RenderPipeline.Builder calls, for the versions before 26.1. Injected into the builder
 * (see {@link PoseStackRotations}); {@link mchorse.bbs_mod.compat.mixin.RenderPipelineBuilderAltMixin}
 * translates them into Mojang's older calls and hands the 26.2 view to the built pipeline.
 */
public interface RenderPipelineBuilderAlt
{
    default RenderPipeline.Builder withColorTargetState(ColorTargetState state)
    {
        throw new UnsupportedOperationException("RenderPipelineBuilderAltMixin fehlt");
    }

    default RenderPipeline.Builder withDepthStencilState(DepthStencilState state)
    {
        throw new UnsupportedOperationException("RenderPipelineBuilderAltMixin fehlt");
    }

    default RenderPipeline.Builder withBindGroupLayout(BindGroupLayout layout)
    {
        throw new UnsupportedOperationException("RenderPipelineBuilderAltMixin fehlt");
    }

    default RenderPipeline.Builder withPrimitiveTopology(VertexFormat.Mode topology)
    {
        throw new UnsupportedOperationException("RenderPipelineBuilderAltMixin fehlt");
    }

    /** Die mit withPrimitiveTopology gesetzte Topologie, sonst null. */
    default VertexFormat.Mode bbs$topologie()
    {
        return null;
    }

    default RenderPipeline.Builder withVertexBinding(int binding, VertexFormat format)
    {
        throw new UnsupportedOperationException("RenderPipelineBuilderAltMixin fehlt");
    }
}
