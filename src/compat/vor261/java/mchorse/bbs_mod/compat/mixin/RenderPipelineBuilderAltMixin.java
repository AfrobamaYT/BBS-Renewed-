package mchorse.bbs_mod.compat.mixin;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import mchorse.bbs_mod.compat.RenderPipelineAlt;
import mchorse.bbs_mod.compat.RenderPipelineBuilderAlt;
import mchorse.bbs_mod.compat.VertexFormatAlt;
import mchorse.bbs_mod.compat.alt.BindGroupLayout;
import mchorse.bbs_mod.compat.alt.ColorTargetState;
import mchorse.bbs_mod.compat.alt.DepthStencilState;
import mchorse.bbs_mod.compat.alt.VertexFormats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Translates 26.2's builder calls into Mojang's older ones before 26.1 and hands the 26.2
 * description to the built pipeline ({@link RenderPipelineAlt}). Depth arrives in 26.2's
 * reversed form and is mirrored here: comparison via CompareOp.alt(), bias negated (26.2
 * stores the additive inverse, NeoForge primer 26.2).
 */
@Mixin(RenderPipeline.Builder.class)
public abstract class RenderPipelineBuilderAltMixin implements RenderPipelineBuilderAlt
{
    @Shadow
    private Optional<VertexFormat> vertexFormat;
    @Shadow
    private Optional<VertexFormat.Mode> vertexFormatMode;
    @Shadow
    private Optional<List<String>> samplers;
    @Shadow
    private Optional<List<RenderPipeline.UniformDescription>> uniforms;

    @Unique
    private final List<VertexFormat> bbs$bindungen = new ArrayList<>();
    @Unique
    private VertexFormat.Mode bbs$topologie;
    @Unique
    private ColorTargetState bbs$farbe;
    @Unique
    private DepthStencilState bbs$tiefe;
    @Unique
    private List<BindGroupLayout> bbs$gruppen;

    @Shadow
    public abstract RenderPipeline.Builder withBlend(BlendFunction function);

    @Shadow
    public abstract RenderPipeline.Builder withoutBlend();

    @Shadow
    public abstract RenderPipeline.Builder withColorWrite(boolean color, boolean alpha);

    @Shadow
    public abstract RenderPipeline.Builder withDepthTestFunction(DepthTestFunction function);

    @Shadow
    public abstract RenderPipeline.Builder withDepthWrite(boolean write);

    @Shadow
    public abstract RenderPipeline.Builder withDepthBias(float scale, float constant);

    @Shadow
    public abstract RenderPipeline.Builder withSampler(String name);

    @Shadow
    public abstract RenderPipeline.Builder withUniform(String name, UniformType type);

    @Shadow
    public abstract RenderPipeline.Builder withUniform(String name, UniformType type, TextureFormat format);

    @Shadow
    public abstract RenderPipeline.Builder withVertexFormat(VertexFormat format, VertexFormat.Mode mode);

    @Unique
    private RenderPipeline.Builder bbs$self()
    {
        return (RenderPipeline.Builder) (Object) this;
    }

    @Override
    public RenderPipeline.Builder withColorTargetState(ColorTargetState state)
    {
        int maske = state.writeMask();

        /* Mojang's GL state before 26.1 writes RGB together; a single colour channel cannot be masked. */
        if ((maske & ColorTargetState.WRITE_COLOR) != 0 && (maske & ColorTargetState.WRITE_COLOR) != ColorTargetState.WRITE_COLOR)
        {
            throw new UnsupportedOperationException("Colour write mask " + maske + " has no counterpart before 26.1");
        }

        if (state.blendFunction().isPresent()) this.withBlend(state.blendFunction().get());
        else this.withoutBlend();

        this.withColorWrite((maske & ColorTargetState.WRITE_COLOR) != 0, state.writeAlpha());
        this.bbs$farbe = state;

        return this.bbs$self();
    }

    @Override
    public RenderPipeline.Builder withDepthStencilState(DepthStencilState state)
    {
        this.withDepthTestFunction(state.depthTest().alt());
        this.withDepthWrite(state.writeDepth());
        this.withDepthBias(-state.depthBiasScaleFactor(), -state.depthBiasConstant());
        this.bbs$tiefe = state;

        return this.bbs$self();
    }

    @Override
    public RenderPipeline.Builder withBindGroupLayout(BindGroupLayout layout)
    {
        for (String sampler : layout.getSamplers())
        {
            if (this.samplers.isEmpty() || !this.samplers.get().contains(sampler)) this.withSampler(sampler);
        }

        for (BindGroupLayout.UniformDescription uniform : layout.getUniforms())
        {
            if (this.uniforms.isPresent() && this.uniforms.get().stream().anyMatch(u -> u.name().equals(uniform.name()))) continue;

            if (uniform.gpuFormat() != null) this.withUniform(uniform.name(), uniform.type(), uniform.gpuFormat().textureFormat());
            else this.withUniform(uniform.name(), uniform.type());
        }

        if (this.bbs$gruppen == null) this.bbs$gruppen = new ArrayList<>();

        this.bbs$gruppen.add(layout);

        return this.bbs$self();
    }

    @Override
    public RenderPipeline.Builder withPrimitiveTopology(VertexFormat.Mode topology)
    {
        this.bbs$topologie = topology;

        return this.bbs$self();
    }

    @Override
    public VertexFormat.Mode bbs$topologie()
    {
        return this.bbs$topologie;
    }

    @Override
    public RenderPipeline.Builder withVertexBinding(int binding, VertexFormat format)
    {
        while (this.bbs$bindungen.size() <= binding) this.bbs$bindungen.add(null);

        this.bbs$bindungen.set(binding, format);

        return this.bbs$self();
    }

    /**
     * Before 26.1 a pipeline has exactly one vertex format. With several bindings (or a 26.2
     * format) it gets one Mojang format listing every attribute in binding order, so the GL
     * program binds each name to its own location; the raw GL layer then feeds the bindings.
     */
    @Inject(method = "build", at = @At("HEAD"))
    private void bbs$vorDemBauen(CallbackInfoReturnable<RenderPipeline> info)
    {
        if (!this.bbs$bindungen.isEmpty())
        {
            VertexFormat erstes = this.bbs$bindungen.get(0);
            boolean eigene = this.bbs$bindungen.size() > 1 || (erstes != null && ((VertexFormatAlt) erstes).bbsEigenes());
            VertexFormat format = erstes;

            if (eigene)
            {
                List<VertexFormats.Attribut> alle = new ArrayList<>();

                for (VertexFormat bindung : this.bbs$bindungen)
                {
                    if (bindung != null) alle.addAll(((VertexFormatAlt) bindung).bbsAttribute());
                }

                format = VertexFormats.mojang(alle);
            }

            VertexFormat.Mode modus = this.bbs$topologie != null ? this.bbs$topologie : this.vertexFormatMode.orElse(VertexFormat.Mode.TRIANGLES);

            this.withVertexFormat(format == null ? mchorse.bbs_mod.compat.alt.FormatLeer.EMPTY : format, modus);
        }
        else if (this.bbs$topologie != null)
        {
            this.withVertexFormat(this.vertexFormat.orElse(mchorse.bbs_mod.compat.alt.FormatLeer.EMPTY), this.bbs$topologie);
        }
    }

    @Inject(method = "build", at = @At("RETURN"))
    private void bbs$nachDemBauen(CallbackInfoReturnable<RenderPipeline> info)
    {
        VertexFormat[] bindungen = this.bbs$bindungen.isEmpty() ? null : this.bbs$bindungen.toArray(new VertexFormat[0]);

        ((RenderPipelineAlt) info.getReturnValue()).bbs$setAlt(bindungen, this.bbs$farbe, this.bbs$tiefe,
            this.bbs$gruppen == null ? null : List.copyOf(this.bbs$gruppen));
    }
}
