package com.afrobama.bbstools.client.bbs.fastpath;

import mchorse.bbs_mod.compat.alt.GpuFormat;
import mchorse.bbs_mod.compat.alt.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import mchorse.bbs_mod.compat.alt.ColorTargetState;
import mchorse.bbs_mod.compat.alt.CompareOp;
import mchorse.bbs_mod.compat.alt.DepthStencilState;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.VertexFormat;
import mchorse.bbs_mod.compat.alt.BindGroupLayouts;
import net.minecraft.resources.ResourceLocation;

/** The 36-byte mesh and 80-byte native instance contracts, including the actual overlay. */
final class Tier2hNativePipeline
{
    static final int VERTEX_BYTES = 36;
    static final int INSTANCE_BYTES = 80;
    static final VertexFormat MESH = mchorse.bbs_mod.compat.alt.VertexFormats.builder(0)
        .addAttribute("Position", GpuFormat.RGB32_FLOAT)
        .addAttribute("UV0", GpuFormat.RG32_FLOAT)
        .addAttribute("Normal", GpuFormat.RGB32_FLOAT)
        .addAttribute("BoneIndex", GpuFormat.R32_UINT).build();
    static final VertexFormat INSTANCE = mchorse.bbs_mod.compat.alt.VertexFormats.builder(1)
        .addAttribute("World0", GpuFormat.RGBA32_FLOAT)
        .addAttribute("World1", GpuFormat.RGBA32_FLOAT)
        .addAttribute("World2", GpuFormat.RGBA32_FLOAT)
        .addAttribute("World3", GpuFormat.RGBA32_FLOAT)
        .addAttribute("PackedLight", GpuFormat.R32_UINT)
        .addAttribute("PackedColor", GpuFormat.R32_UINT)
        .addAttribute("PaletteRow", GpuFormat.R32_UINT)
        .addAttribute("PackedOverlay", GpuFormat.R32_UINT).build();

    static final RenderPipeline PIPELINE = RenderPipeline.builder()
        .withLocation(new ResourceLocation("bbs", "pipeline/tier2h_native"))
        .withVertexShader(new ResourceLocation("bbs", "core/tier2h_native"))
        .withFragmentShader(new ResourceLocation("bbs", "core/tier2h_native"))
        .withPrimitiveTopology(VertexFormat.Mode.TRIANGLES)
        .withVertexBinding(0, MESH).withVertexBinding(1, INSTANCE)
        .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
        .withBindGroupLayout(BindGroupLayouts.PROJECTION)
        .withBindGroupLayout(BindGroupLayouts.LIGHTING)
        .withBindGroupLayout(BindGroupLayouts.FOG)
        .withBindGroupLayout(BindGroupLayout.builder()
            .withSampler("Sampler0")
            .withSampler("Sampler1")
            .withSampler("Sampler2")
            .withSampler("BoneTex").build())
        .withCull(false)
        .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT)).build();

    private Tier2hNativePipeline()
    {}
}
