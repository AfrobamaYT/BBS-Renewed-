package mchorse.bbs_mod.integrations.vitrail;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.VertexFormat;

/** Names, formats and ranges shared by the actual GPU source and the Vitrail pipeline adapter. */
public final class VitrailGpuLayout
{
    public static final int DRAW_BYTES = 128;
    public static final VertexFormat CORNER = VertexFormat.builder(0)
        .addAttribute("BbsCorner", GpuFormat.R32_UINT).build();
    public static final VertexFormat INSTANCE = VertexFormat.builder(1)
        .addAttribute("BbsWorld0", GpuFormat.RGBA32_FLOAT)
        .addAttribute("BbsWorld1", GpuFormat.RGBA32_FLOAT)
        .addAttribute("BbsWorld2", GpuFormat.RGBA32_FLOAT)
        .addAttribute("BbsWorld3", GpuFormat.RGBA32_FLOAT)
        .addAttribute("BbsPackedLight", GpuFormat.R32_UINT)
        .addAttribute("BbsPackedColor", GpuFormat.R32_UINT)
        .addAttribute("BbsPaletteRow", GpuFormat.R32_UINT)
        .addAttribute("BbsPackedOverlay", GpuFormat.R32_UINT).build();

    private VitrailGpuLayout() {}

    public static BindGroupLayout bindings(VitrailVertexSource.Kind kind)
    {
        return bindings(kind, false);
    }

    public static BindGroupLayout bindings(VitrailVertexSource.Kind kind, boolean captureEmission)
    {
        var bindings = BindGroupLayout.builder().withUniform("BbsDraw", UniformType.UNIFORM_BUFFER)
            .withSampler("BbsAlphaTexture")
            .withUniform("BbsIndexBuffer", UniformType.TEXEL_BUFFER, GpuFormat.R32_UINT);
        if (captureEmission) bindings.withUniform("BbsEmissionFragments", UniformType.UNIFORM_BUFFER)
            .withUniform("BbsFog", UniformType.UNIFORM_BUFFER);
        if (kind == VitrailVertexSource.Kind.CROWD)
            bindings.withUniform("BbsGeometryBuffer", UniformType.TEXEL_BUFFER, GpuFormat.R32_FLOAT)
                .withSampler("BbsPalette");
        else
        {
            bindings.withUniform("BbsPositionBuffer", UniformType.TEXEL_BUFFER, GpuFormat.R32_FLOAT)
                .withUniform("BbsNormalBuffer", UniformType.TEXEL_BUFFER, GpuFormat.R32_FLOAT)
                .withUniform("BbsUvBuffer", UniformType.TEXEL_BUFFER, GpuFormat.R32_FLOAT);
            if (kind == VitrailVertexSource.Kind.SKIN)
                bindings.withUniform("BbsBones", UniformType.UNIFORM_BUFFER)
                    .withUniform("BbsJoints0", UniformType.TEXEL_BUFFER, GpuFormat.R32_UINT)
                    .withUniform("BbsJoints1", UniformType.TEXEL_BUFFER, GpuFormat.R32_UINT)
                    .withUniform("BbsWeights0", UniformType.TEXEL_BUFFER, GpuFormat.R32_FLOAT)
                    .withUniform("BbsWeights1", UniformType.TEXEL_BUFFER, GpuFormat.R32_FLOAT);
        }
        return bindings.build();
    }

    /** The alignment/elements arguments are measured physical-device limits, never invented defaults. */
    public static void validateTexels(GpuBufferSlice slice, GpuFormat format, long required,
        long alignment, long maximumElements)
    {
        if (slice.buffer().isClosed() || (slice.buffer().usage() & GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER) == 0)
            throw new IllegalArgumentException("Native Vitrail input needs a live UNIFORM_TEXEL_BUFFER");
        validateRange(slice.buffer().size(), slice.offset(), slice.length(), format.blockSize(), required,
            alignment, maximumElements);
    }

    public static void validateRange(long bufferBytes, long offset, long bytes, int texelBytes,
        long required, long alignment, long maximumElements)
    {
        if (texelBytes <= 0 || required < 1 || alignment < 1 || maximumElements < 1
            || offset < 0 || bytes <= 0 || offset % alignment != 0 || bytes % texelBytes != 0
            || offset >= bufferBytes || bytes > bufferBytes - offset
            || bytes / texelBytes < required || bytes / texelBytes > maximumElements)
            throw new IllegalArgumentException("Invalid native Vitrail texel buffer offset/range/alignment/elements");
    }
}
