package com.mojang.blaze3d.pipeline;

import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.LogicOp;
import com.mojang.blaze3d.platform.PolygonMode;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import mchorse.bbs_mod.compat.RenderPipelineBuilderAlt;
import mchorse.bbs_mod.compat.gpu.PipelineAltDaten;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * BBS-Shim fuer Minecraft vor 1.21.5: Mojangs RenderPipeline gibt es dort nicht. Oeffentliche
 * Form wie 1.21.11, eigene Implementierung; gezeichnet wird ueber mchorse.bbs_mod.compat.gpu.
 * Die 26.2-Beschreibung (RenderPipelineAlt) traegt die Klasse selbst; den Builder ergaenzt
 * RenderPipelineBuilderAltMixin aus vor261 (Mixins duerfen auch Mod-Klassen treffen), darum
 * heissen die Builder-Felder wie bei Mojang.
 */
public class RenderPipeline extends PipelineAltDaten
{
    private static int sortKeySeed;
    private static int naechsteNummer;

    private final ResourceLocation location;
    /** Alle Werte, Vorgaben schon eingesetzt (siehe Builder.build). */
    private final Snippet w;
    private final float depthBiasScaleFactor;
    private final float depthBiasConstant;
    private final int sortKey;

    protected RenderPipeline(ResourceLocation location, Snippet werte, float depthBiasScaleFactor, float depthBiasConstant)
    {
        this.location = location;
        this.w = werte;
        this.depthBiasScaleFactor = depthBiasScaleFactor;
        this.depthBiasConstant = depthBiasConstant;
        this.sortKey = naechsteNummer++;
    }

    public int getSortKey()
    {
        return this.sortKey * (sortKeySeed + 1);
    }

    public static void updateSortKeySeed()
    {
        sortKeySeed = Math.round(100000.0F * (float) Math.random());
    }

    @Override
    public String toString()
    {
        return this.location.toString();
    }

    public DepthTestFunction getDepthTestFunction() { return this.w.depthTestFunction().get(); }
    public PolygonMode getPolygonMode() { return this.w.polygonMode().get(); }
    public boolean isCull() { return this.w.cull().get(); }
    public LogicOp getColorLogic() { return this.w.colorLogic().get(); }
    public Optional<BlendFunction> getBlendFunction() { return this.w.blendFunction(); }
    public boolean isWriteColor() { return this.w.writeColor().get(); }
    public boolean isWriteAlpha() { return this.w.writeAlpha().get(); }
    public boolean isWriteDepth() { return this.w.writeDepth().get(); }
    public float getDepthBiasScaleFactor() { return this.depthBiasScaleFactor; }
    public float getDepthBiasConstant() { return this.depthBiasConstant; }
    public ResourceLocation getLocation() { return this.location; }
    public VertexFormat getVertexFormat() { return this.w.vertexFormat().get(); }
    public VertexFormat.Mode getVertexFormatMode() { return this.w.vertexFormatMode().get(); }
    public ResourceLocation getVertexShader() { return this.w.vertexShader().get(); }
    public ResourceLocation getFragmentShader() { return this.w.fragmentShader().get(); }
    public ShaderDefines getShaderDefines() { return this.w.shaderDefines().get(); }
    public List<String> getSamplers() { return this.w.samplers().get(); }
    public List<UniformDescription> getUniforms() { return this.w.uniforms().get(); }

    public boolean wantsDepthTexture()
    {
        return this.getDepthTestFunction() != DepthTestFunction.NO_DEPTH_TEST || this.depthBiasConstant != 0.0F
            || this.depthBiasScaleFactor != 0.0F || this.isWriteDepth();
    }

    public static Builder builder(Snippet... snippets)
    {
        Builder builder = new Builder();

        for (Snippet snippet : snippets)
        {
            builder.withSnippet(snippet);
        }

        return builder;
    }

    public static class Builder implements RenderPipelineBuilderAlt
    {
        private Optional<ResourceLocation> location = Optional.empty();
        private Optional<ResourceLocation> fragmentShader = Optional.empty();
        private Optional<ResourceLocation> vertexShader = Optional.empty();
        private Optional<ShaderDefines.Builder> definesBuilder = Optional.empty();
        private Optional<List<String>> samplers = Optional.empty();
        private Optional<List<UniformDescription>> uniforms = Optional.empty();
        private Optional<DepthTestFunction> depthTestFunction = Optional.empty();
        private Optional<PolygonMode> polygonMode = Optional.empty();
        private Optional<Boolean> cull = Optional.empty();
        private Optional<Boolean> writeColor = Optional.empty();
        private Optional<Boolean> writeAlpha = Optional.empty();
        private Optional<Boolean> writeDepth = Optional.empty();
        private Optional<LogicOp> colorLogic = Optional.empty();
        private Optional<BlendFunction> blendFunction = Optional.empty();
        private Optional<VertexFormat> vertexFormat = Optional.empty();
        private Optional<VertexFormat.Mode> vertexFormatMode = Optional.empty();
        private float depthBiasScaleFactor;
        private float depthBiasConstant;

        Builder()
        {}

        private ShaderDefines.Builder defines()
        {
            if (this.definesBuilder.isEmpty())
            {
                this.definesBuilder = Optional.of(ShaderDefines.builder());
            }

            return this.definesBuilder.get();
        }

        public Builder withLocation(String location) { return this.withLocation(mchorse.bbs_mod.compat.alt.Orte.standard(location)); }
        public Builder withLocation(ResourceLocation location) { this.location = Optional.of(location); return this; }
        public Builder withFragmentShader(String shader) { return this.withFragmentShader(mchorse.bbs_mod.compat.alt.Orte.standard(shader)); }
        public Builder withFragmentShader(ResourceLocation shader) { this.fragmentShader = Optional.of(shader); return this; }
        public Builder withVertexShader(String shader) { return this.withVertexShader(mchorse.bbs_mod.compat.alt.Orte.standard(shader)); }
        public Builder withVertexShader(ResourceLocation shader) { this.vertexShader = Optional.of(shader); return this; }
        public Builder withShaderDefine(String flag) { this.defines().define(flag); return this; }
        public Builder withShaderDefine(String name, int value) { this.defines().define(name, String.valueOf(value)); return this; }
        public Builder withShaderDefine(String name, float value) { this.defines().define(name, value); return this; }

        public Builder withSampler(String sampler)
        {
            if (this.samplers.isEmpty()) this.samplers = Optional.of(new ArrayList<>());
            this.samplers.get().add(sampler);
            return this;
        }

        public Builder withUniform(String name, UniformType type)
        {
            if (this.uniforms.isEmpty()) this.uniforms = Optional.of(new ArrayList<>());
            if (type == UniformType.TEXEL_BUFFER) throw new IllegalArgumentException("Texel-Puffer brauchen ein Format: " + name);
            this.uniforms.get().add(new UniformDescription(name, type));
            return this;
        }

        public Builder withUniform(String name, UniformType type, TextureFormat format)
        {
            if (this.uniforms.isEmpty()) this.uniforms = Optional.of(new ArrayList<>());
            if (type != UniformType.TEXEL_BUFFER) throw new IllegalArgumentException("Nur Texel-Puffer haben ein Format: " + name);
            this.uniforms.get().add(new UniformDescription(name, type, format));
            return this;
        }

        public Builder withDepthTestFunction(DepthTestFunction function) { this.depthTestFunction = Optional.of(function); return this; }
        public Builder withPolygonMode(PolygonMode mode) { this.polygonMode = Optional.of(mode); return this; }
        public Builder withCull(boolean cull) { this.cull = Optional.of(cull); return this; }
        public Builder withBlend(BlendFunction function) { this.blendFunction = Optional.of(function); return this; }
        public Builder withoutBlend() { this.blendFunction = Optional.empty(); return this; }
        public Builder withColorWrite(boolean color) { this.writeColor = Optional.of(color); this.writeAlpha = Optional.of(color); return this; }
        public Builder withColorWrite(boolean color, boolean alpha) { this.writeColor = Optional.of(color); this.writeAlpha = Optional.of(alpha); return this; }
        public Builder withDepthWrite(boolean depth) { this.writeDepth = Optional.of(depth); return this; }
        public Builder withColorLogic(LogicOp logic) { this.colorLogic = Optional.of(logic); return this; }
        public Builder withVertexFormat(VertexFormat format, VertexFormat.Mode mode) { this.vertexFormat = Optional.of(format); this.vertexFormatMode = Optional.of(mode); return this; }
        public Builder withDepthBias(float scale, float constant) { this.depthBiasScaleFactor = scale; this.depthBiasConstant = constant; return this; }

        void withSnippet(Snippet s)
        {
            s.vertexShader.ifPresent(this::withVertexShader);
            s.fragmentShader.ifPresent(this::withFragmentShader);
            s.shaderDefines.ifPresent(d -> { d.values().forEach(this.defines()::define); d.flags().forEach(this.defines()::define); });
            s.samplers.ifPresent(list -> list.forEach(this::withSampler));
            s.uniforms.ifPresent(list -> { if (this.uniforms.isEmpty()) this.uniforms = Optional.of(new ArrayList<>()); this.uniforms.get().addAll(list); });
            s.blendFunction.ifPresent(this::withBlend);
            s.depthTestFunction.ifPresent(this::withDepthTestFunction);
            s.polygonMode.ifPresent(this::withPolygonMode);
            s.cull.ifPresent(this::withCull);
            s.writeColor.ifPresent(c -> this.writeColor = Optional.of(c));
            s.writeAlpha.ifPresent(a -> this.writeAlpha = Optional.of(a));
            s.writeDepth.ifPresent(this::withDepthWrite);
            s.colorLogic.ifPresent(this::withColorLogic);
            s.vertexFormat.ifPresent(f -> this.vertexFormat = Optional.of(f));
            s.vertexFormatMode.ifPresent(m -> this.vertexFormatMode = Optional.of(m));
        }

        public Snippet buildSnippet()
        {
            return new Snippet(this.vertexShader, this.fragmentShader, this.definesBuilder.map(ShaderDefines.Builder::build),
                this.samplers.map(List::copyOf), this.uniforms.map(List::copyOf), this.blendFunction, this.depthTestFunction,
                this.polygonMode, this.cull, this.writeColor, this.writeAlpha, this.writeDepth, this.colorLogic,
                this.vertexFormat, this.vertexFormatMode);
        }

        public RenderPipeline build()
        {
            if (this.location.isEmpty()) throw new IllegalStateException("Pipeline ohne Ort");
            if (this.vertexShader.isEmpty()) throw new IllegalStateException("Pipeline " + this.location.get() + " ohne Vertex-Shader");
            if (this.fragmentShader.isEmpty()) throw new IllegalStateException("Pipeline " + this.location.get() + " ohne Fragment-Shader");
            if (this.vertexFormat.isEmpty()) throw new IllegalStateException("Pipeline " + this.location.get() + " ohne Eckenformat");
            if (this.vertexFormatMode.isEmpty()) throw new IllegalStateException("Pipeline " + this.location.get() + " ohne Topologie");

            return new RenderPipeline(this.location.get(), new Snippet(this.vertexShader, this.fragmentShader,
                Optional.of(this.definesBuilder.orElse(ShaderDefines.builder()).build()), Optional.of(List.copyOf(this.samplers.orElse(List.of()))),
                Optional.of(List.copyOf(this.uniforms.orElse(List.of()))), this.blendFunction,
                Optional.of(this.depthTestFunction.orElse(DepthTestFunction.LEQUAL_DEPTH_TEST)), Optional.of(this.polygonMode.orElse(PolygonMode.FILL)),
                Optional.of(this.cull.orElse(true)), Optional.of(this.writeColor.orElse(true)), Optional.of(this.writeAlpha.orElse(true)),
                Optional.of(this.writeDepth.orElse(true)), Optional.of(this.colorLogic.orElse(LogicOp.NONE)), this.vertexFormat, this.vertexFormatMode),
                this.depthBiasScaleFactor, this.depthBiasConstant);
        }
    }

    public record Snippet(Optional<ResourceLocation> vertexShader, Optional<ResourceLocation> fragmentShader,
        Optional<ShaderDefines> shaderDefines, Optional<List<String>> samplers, Optional<List<UniformDescription>> uniforms,
        Optional<BlendFunction> blendFunction, Optional<DepthTestFunction> depthTestFunction, Optional<PolygonMode> polygonMode,
        Optional<Boolean> cull, Optional<Boolean> writeColor, Optional<Boolean> writeAlpha, Optional<Boolean> writeDepth,
        Optional<LogicOp> colorLogic, Optional<VertexFormat> vertexFormat, Optional<VertexFormat.Mode> vertexFormatMode)
    {}

    public record UniformDescription(String name, UniformType type, TextureFormat textureFormat)
    {
        public UniformDescription(String name, UniformType type)
        {
            this(name, type, null);
        }

        public UniformDescription(String name, TextureFormat format)
        {
            this(name, UniformType.TEXEL_BUFFER, format);
        }
    }
}
