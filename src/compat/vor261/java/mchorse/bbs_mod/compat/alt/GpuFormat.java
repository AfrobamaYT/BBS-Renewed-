package mchorse.bbs_mod.compat.alt;

import com.mojang.blaze3d.textures.TextureFormat;

/**
 * Minecraft 26.2's GpuFormat, for the OpenGL versions before 26.1. Constants, component types
 * and the three GL codes per format are taken from 26.2's GpuFormat and GlConst (generated,
 * not typed in). {@link #textureFormat()} names the counterpart among the four texture formats
 * Mojang has before 26.1; every other format goes through the raw GL layer or fails loudly.
 */
public enum GpuFormat
{
    R8_UNORM(ComponentType.UNORM_8, 1, 33321, 6403, 5121),
    R8_SNORM(ComponentType.SNORM_8, 1, 36756, 6403, 5120),
    RG8_UNORM(ComponentType.UNORM_8, 2, 33323, 33319, 5121),
    RG8_SNORM(ComponentType.SNORM_8, 2, 36757, 33319, 5120),
    RGB8_UNORM(ComponentType.UNORM_8, 3, 0, 6407, 5121),
    RGB8_SNORM(ComponentType.SNORM_8, 3, 0, 6407, 5120),
    RGBA8_UNORM(ComponentType.UNORM_8, 4, 32856, 6408, 5121),
    RGBA8_SNORM(ComponentType.SNORM_8, 4, 36759, 6408, 5120),
    R16_UNORM(ComponentType.UNORM_16, 1, 33322, 6403, 5123),
    R16_SNORM(ComponentType.SNORM_16, 1, 36760, 6403, 5122),
    RG16_UNORM(ComponentType.UNORM_16, 2, 33324, 33319, 5123),
    RG16_SNORM(ComponentType.SNORM_16, 2, 36761, 33319, 5122),
    RGB16_UNORM(ComponentType.UNORM_16, 3, 0, 6407, 5123),
    RGB16_SNORM(ComponentType.SNORM_16, 3, 0, 6407, 5122),
    RGBA16_UNORM(ComponentType.UNORM_16, 4, 32859, 6408, 5123),
    RGBA16_SNORM(ComponentType.SNORM_16, 4, 36763, 6408, 5122),
    R8_UINT(ComponentType.UINT_8, 1, 33330, 36244, 5121),
    R8_SINT(ComponentType.SINT_8, 1, 33329, 36244, 5120),
    RG8_UINT(ComponentType.UINT_8, 2, 33336, 33320, 5121),
    RG8_SINT(ComponentType.SINT_8, 2, 33335, 33320, 5120),
    RGB8_UINT(ComponentType.UINT_8, 3, 0, 36248, 5121),
    RGB8_SINT(ComponentType.SINT_8, 3, 0, 36248, 5120),
    RGBA8_UINT(ComponentType.UINT_8, 4, 36220, 36249, 5121),
    RGBA8_SINT(ComponentType.SINT_8, 4, 36238, 36249, 5120),
    R16_UINT(ComponentType.UINT_16, 1, 33332, 36244, 5123),
    R16_SINT(ComponentType.SINT_16, 1, 33331, 36244, 5122),
    RG16_UINT(ComponentType.UINT_16, 2, 33338, 33320, 5123),
    RG16_SINT(ComponentType.SINT_16, 2, 33337, 33320, 5122),
    RGB16_UINT(ComponentType.UINT_16, 3, 0, 36248, 5123),
    RGB16_SINT(ComponentType.SINT_16, 3, 0, 36248, 5122),
    RGBA16_UINT(ComponentType.UINT_16, 4, 36214, 36249, 5123),
    RGBA16_SINT(ComponentType.SINT_16, 4, 36232, 36249, 5122),
    R32_UINT(ComponentType.UINT_32, 1, 33334, 36244, 5125),
    R32_SINT(ComponentType.SINT_32, 1, 33333, 36244, 5124),
    RG32_UINT(ComponentType.UINT_32, 2, 33340, 33320, 5125),
    RG32_SINT(ComponentType.SINT_32, 2, 33339, 33320, 5124),
    RGB32_UINT(ComponentType.UINT_32, 3, 36209, 36248, 5125),
    RGB32_SINT(ComponentType.SINT_32, 3, 36227, 36248, 5124),
    RGBA32_UINT(ComponentType.UINT_32, 4, 36208, 36249, 5125),
    RGBA32_SINT(ComponentType.SINT_32, 4, 36226, 36249, 5124),
    R16_FLOAT(ComponentType.FLOAT_16, 1, 33325, 6403, 5131),
    RG16_FLOAT(ComponentType.FLOAT_16, 2, 33327, 33319, 5131),
    RGB16_FLOAT(ComponentType.FLOAT_16, 3, 0, 6407, 5131),
    RGBA16_FLOAT(ComponentType.FLOAT_16, 4, 34842, 6408, 5131),
    R32_FLOAT(ComponentType.FLOAT_32, 1, 33326, 6403, 5126),
    RG32_FLOAT(ComponentType.FLOAT_32, 2, 33328, 33319, 5126),
    RGB32_FLOAT(ComponentType.FLOAT_32, 3, 0, 6407, 5126),
    RGBA32_FLOAT(ComponentType.FLOAT_32, 4, 34836, 6408, 5126),
    RGB10A2_UNORM(ComponentType.OPAQUE_32, 1, 32857, 6408, 33640),
    RGB10A2_UINT(ComponentType.OPAQUE_32, 1, 36975, 36249, 33640),
    RG11B10_FLOAT(ComponentType.OPAQUE_32, 1, 35898, 6407, 35899),
    D32_FLOAT(ComponentType.OPAQUE_32, 1, 36012, 6402, 5126),
    D32_FLOAT_S8_UINT(ComponentType.OPAQUE_64, 1, 36013, 34041, 36269),
    D24_UNORM_S8_UINT(ComponentType.OPAQUE_32, 1, 35056, 34041, 34042),
    D16_UNORM(ComponentType.OPAQUE_16, 1, 33189, 6402, 5123),
    S8_UINT(ComponentType.OPAQUE_8, 1, 36168, 6401, 5121);

    private final ComponentType componentType;
    private final int componentCount;
    public final int glInternal;
    public final int glExternal;
    public final int glType;

    GpuFormat(ComponentType componentType, int componentCount, int glInternal, int glExternal, int glType)
    {
        this.componentType = componentType;
        this.componentCount = componentCount;
        this.glInternal = glInternal;
        this.glExternal = glExternal;
        this.glType = glType;
    }

    public int blockSize()
    {
        return this.componentType.byteSize() * this.componentCount;
    }

    public int byteAlignment()
    {
        return this.componentType.byteSize();
    }

    public ComponentType componentType()
    {
        return this.componentType;
    }

    public int componentCount()
    {
        return this.componentCount;
    }

    public boolean hasColorAspect()
    {
        return !this.hasDepthAspect() && !this.hasStencilAspect();
    }

    public boolean hasDepthAspect()
    {
        return this == D32_FLOAT || this == D32_FLOAT_S8_UINT || this == D24_UNORM_S8_UINT || this == D16_UNORM;
    }

    public boolean hasStencilAspect()
    {
        return this == S8_UINT || this == D32_FLOAT_S8_UINT || this == D24_UNORM_S8_UINT;
    }

    /** As 26.2's GlConst.isFormatNormalized. */
    public boolean normalized()
    {
        return switch (this)
        {
            case R8_UNORM, R8_SNORM, R16_UNORM, R16_SNORM, RG8_UNORM, RG8_SNORM, RG16_UNORM, RG16_SNORM, RGB8_UNORM, RGB8_SNORM,
                RGB16_UNORM, RGB16_SNORM, RGBA8_UNORM, RGBA8_SNORM, RGBA16_UNORM, RGB10A2_UNORM, D16_UNORM -> true;
            default -> false;
        };
    }

    /** Integer formats are read by shaders as int/uint (glVertexAttribIPointer, usampler). */
    public boolean integer()
    {
        return switch (this.componentType)
        {
            case UINT_8, SINT_8, UINT_16, SINT_16, UINT_32, SINT_32 -> true;
            default -> this == RGB10A2_UINT;
        };
    }

    public TextureFormat textureFormat()
    {
        return switch (this)
        {
            case RGBA8_UNORM -> TextureFormat.RGBA8;
            case R8_UNORM -> TextureFormat.RED8;
            case R8_SINT -> TextureFormat.RED8I;
            case D32_FLOAT -> TextureFormat.DEPTH32;
            default -> throw new UnsupportedOperationException("GpuFormat " + this + " has no Minecraft TextureFormat before 26.1");
        };
    }

    public static GpuFormat von(TextureFormat format)
    {
        return switch (format)
        {
            case RGBA8 -> RGBA8_UNORM;
            case RED8 -> R8_UNORM;
            case RED8I -> R8_SINT;
            case DEPTH32 -> D32_FLOAT;
        };
    }

    public enum ComponentType
    {
        UNORM_8(1), SNORM_8(1), UINT_8(1), SINT_8(1), UNORM_16(2), SNORM_16(2), UINT_16(2), SINT_16(2), FLOAT_16(2),
        UINT_32(4), SINT_32(4), FLOAT_32(4), OPAQUE_8(1), OPAQUE_16(2), OPAQUE_32(4), OPAQUE_64(8);

        private final int byteSize;

        ComponentType(int byteSize)
        {
            this.byteSize = byteSize;
        }

        public int byteSize()
        {
            return this.byteSize;
        }
    }
}
