package mchorse.bbs_mod.compat.alt;

import com.mojang.blaze3d.shaders.UniformType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 26.2's bind group layout, for the OpenGL versions before 26.1. Mojang's pipeline builder
 * there takes the samplers and uniforms one by one; {@link mchorse.bbs_mod.compat.RenderPipelineAlt}
 * hands them over when a layout is added.
 */
public class BindGroupLayout
{
    private final List<String> samplers;
    private final List<UniformDescription> uniforms;

    private BindGroupLayout(List<String> samplers, List<UniformDescription> uniforms)
    {
        this.samplers = List.copyOf(samplers);
        this.uniforms = List.copyOf(uniforms);
    }

    public static Builder builder()
    {
        return new Builder();
    }

    public List<String> getSamplers()
    {
        return this.samplers;
    }

    public List<UniformDescription> getUniforms()
    {
        return this.uniforms;
    }

    public static List<String> flattenSamplers(List<BindGroupLayout> layouts)
    {
        List<String> all = new ArrayList<>();

        for (BindGroupLayout layout : layouts) all.addAll(layout.samplers);

        return all;
    }

    public static List<UniformDescription> flattenUniforms(List<BindGroupLayout> layouts)
    {
        List<UniformDescription> all = new ArrayList<>();

        for (BindGroupLayout layout : layouts) all.addAll(layout.uniforms);

        return all;
    }

    public static void ensureCompatible(List<BindGroupLayout> layouts)
    {
        Set<String> names = new HashSet<>();

        for (String sampler : flattenSamplers(layouts))
        {
            if (!names.add(sampler)) throw new IllegalStateException("Duplicate sampler in bind group layouts: " + sampler);
        }

        for (UniformDescription uniform : flattenUniforms(layouts))
        {
            if (!names.add(uniform.name())) throw new IllegalStateException("Duplicate uniform in bind group layouts: " + uniform.name());
        }
    }

    public static class Builder
    {
        private final List<String> samplers = new ArrayList<>();
        private final List<UniformDescription> uniforms = new ArrayList<>();

        public Builder withSampler(String name)
        {
            this.samplers.add(name);

            return this;
        }

        public Builder withUniform(String name, UniformType type)
        {
            this.uniforms.add(new UniformDescription(name, type, null));

            return this;
        }

        public Builder withUniform(String name, UniformType type, GpuFormat format)
        {
            this.uniforms.add(new UniformDescription(name, type, format));

            return this;
        }

        public BindGroupLayout build()
        {
            return new BindGroupLayout(this.samplers, this.uniforms);
        }
    }

    public record UniformDescription(String name, UniformType type, GpuFormat gpuFormat)
    {
        public UniformDescription(String name, UniformType type)
        {
            this(name, type, null);
        }

        public UniformDescription(String name, GpuFormat format)
        {
            this(name, UniformType.TEXEL_BUFFER, format);
        }
    }
}
