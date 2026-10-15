package com.afrobama.bbstools.client.bbs.fastpath;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;

/** The 36-byte mesh and 80-byte native instance contracts, including the actual overlay. */
final class Tier2hNativePipeline
{
    static final int VERTEX_BYTES = 36;
    static final int INSTANCE_BYTES = 80;
    static final VertexFormat MESH = VertexFormat.builder(0)
        .addAttribute("Position", GpuFormat.RGB32_FLOAT)
        .addAttribute("UV0", GpuFormat.RG32_FLOAT)
        .addAttribute("Normal", GpuFormat.RGB32_FLOAT)
        .addAttribute("BoneIndex", GpuFormat.R32_UINT).build();
    static final VertexFormat INSTANCE = VertexFormat.builder(1)
        .addAttribute("World0", GpuFormat.RGBA32_FLOAT)
        .addAttribute("World1", GpuFormat.RGBA32_FLOAT)
        .addAttribute("World2", GpuFormat.RGBA32_FLOAT)
        .addAttribute("World3", GpuFormat.RGBA32_FLOAT)
        .addAttribute("PackedLight", GpuFormat.R32_UINT)
        .addAttribute("PackedColor", GpuFormat.R32_UINT)
        .addAttribute("PaletteRow", GpuFormat.R32_UINT)
        .addAttribute("PackedOverlay", GpuFormat.R32_UINT).build();

    static final RenderPipeline PIPELINE = RenderPipeline.builder()
        .withLocation(Identifier.fromNamespaceAndPath("bbs", "pipeline/tier2h_native"))
        .withVertexShader(Identifier.fromNamespaceAndPath("bbs", "core/tier2h_native"))
        .withFragmentShader(Identifier.fromNamespaceAndPath("bbs", "core/tier2h_native"))
        .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
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
