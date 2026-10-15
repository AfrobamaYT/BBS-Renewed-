package mchorse.bbs_mod.compat;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import mchorse.bbs_mod.compat.alt.BindGroupLayout;
import mchorse.bbs_mod.compat.alt.ColorTargetState;
import mchorse.bbs_mod.compat.alt.CompareOp;
import mchorse.bbs_mod.compat.alt.DepthStencilState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 26.2's RenderPipeline getters, for the versions before 26.1. Pipelines built through
 * {@link RenderPipelineBuilderAlt} carry their 26.2 description; vanilla pipelines are
 * described from Mojang's older getters (depth mirrored back to 26.2's reversed depth).
 */
public interface RenderPipelineAlt
{
    /** Set by the builder mixin; null for pipelines built without the 26.2 calls. */
    default void bbs$setAlt(VertexFormat[] bindungen, ColorTargetState farbe, DepthStencilState tiefe, List<BindGroupLayout> gruppen)
    {
        throw new UnsupportedOperationException("compat-Mixin fehlt");
    }

    default VertexFormat[] bbs$bindungen()
    {
        throw new UnsupportedOperationException("compat-Mixin fehlt");
    }

    default ColorTargetState bbs$farbe()
    {
        throw new UnsupportedOperationException("compat-Mixin fehlt");
    }

    default DepthStencilState bbs$tiefe()
    {
        throw new UnsupportedOperationException("compat-Mixin fehlt");
    }

    default List<BindGroupLayout> bbs$gruppen()
    {
        throw new UnsupportedOperationException("compat-Mixin fehlt");
    }

    private RenderPipeline mojang()
    {
        return (RenderPipeline) this;
    }

    /** True when the pipeline needs the raw GL vertex path (several bindings or 26.2 formats). */
    default boolean bbsEigeneBindungen()
    {
        VertexFormat[] bindungen = this.bbs$bindungen();

        if (bindungen == null) return false;
        if (bindungen.length > 1) return true;

        return bindungen.length == 1 && bindungen[0] != null && ((VertexFormatAlt) bindungen[0]).bbsEigenes();
    }

    default VertexFormat.Mode getPrimitiveTopology()
    {
        return this.mojang().getVertexFormatMode();
    }

    default VertexFormat getVertexFormatBinding(int binding)
    {
        VertexFormat[] bindungen = this.getVertexFormatBindings();

        return binding < bindungen.length ? bindungen[binding] : null;
    }

    default VertexFormat[] getVertexFormatBindings()
    {
        VertexFormat[] bindungen = this.bbs$bindungen();

        /* Eine einzelne Mojang-Bindung ist getVertexFormat() selbst: Iris tauscht dessen
         * Rueckgabe beim Weltrendern gegen sein erweitertes Format, das muss hier durchkommen. */
        if (bindungen != null && !this.bbsEigeneBindungen() && bindungen.length == 1) bindungen = null;

        return bindungen != null ? bindungen.clone() : new VertexFormat[] {this.mojang().getVertexFormat()};
    }

    default List<VertexFormat> bbsVertexFormatBindings()
    {
        return Arrays.asList(this.getVertexFormatBindings());
    }

    default ColorTargetState getColorTargetState()
    {
        if (this.bbs$farbe() != null) return this.bbs$farbe();

        RenderPipeline pipeline = this.mojang();
        int maske = (pipeline.isWriteColor() ? ColorTargetState.WRITE_COLOR : 0) | (pipeline.isWriteAlpha() ? ColorTargetState.WRITE_ALPHA : 0);

        return new ColorTargetState(pipeline.getBlendFunction(), mchorse.bbs_mod.compat.alt.GpuFormat.RGBA8_UNORM, maske);
    }

    default ColorTargetState[] getColorTargetStates()
    {
        return new ColorTargetState[] {this.getColorTargetState()};
    }

    default List<ColorTargetState> bbsColorTargetStates()
    {
        return Arrays.asList(this.getColorTargetStates());
    }

    default DepthStencilState getDepthStencilState()
    {
        if (this.bbs$tiefe() != null) return this.bbs$tiefe();

        RenderPipeline pipeline = this.mojang();

        return new DepthStencilState(CompareOp.von(pipeline.getDepthTestFunction()), pipeline.isWriteDepth(),
            -pipeline.getDepthBiasScaleFactor(), -pipeline.getDepthBiasConstant());
    }

    default List<BindGroupLayout> getBindGroupLayouts()
    {
        if (this.bbs$gruppen() != null) return this.bbs$gruppen();

        RenderPipeline pipeline = this.mojang();
        BindGroupLayout.Builder builder = BindGroupLayout.builder();

        for (String sampler : pipeline.getSamplers()) builder.withSampler(sampler);
        for (RenderPipeline.UniformDescription uniform : pipeline.getUniforms())
        {
            if (uniform.textureFormat() != null) builder.withUniform(uniform.name(), uniform.type(), mchorse.bbs_mod.compat.alt.GpuFormat.von(uniform.textureFormat()));
            else builder.withUniform(uniform.name(), uniform.type());
        }

        return new ArrayList<>(List.of(builder.build()));
    }
}
