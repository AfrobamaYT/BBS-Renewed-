package mchorse.bbs_mod.compat.alt;

import com.mojang.blaze3d.pipeline.BlendFunction;

import java.util.Optional;

/** 26.2's colour target state, for the OpenGL versions before 26.1. */
public record ColorTargetState(Optional<BlendFunction> blendFunction, GpuFormat format, int writeMask)
{
    public static final int WRITE_RED = 1;
    public static final int WRITE_GREEN = 2;
    public static final int WRITE_BLUE = 4;
    public static final int WRITE_ALPHA = 8;
    public static final int WRITE_COLOR = 7;
    public static final int WRITE_ALL = 15;
    public static final int WRITE_NONE = 0;
    public static final int MAX_COLOR_TARGETS = 8;
    public static final ColorTargetState DEFAULT = new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, WRITE_ALL);

    public ColorTargetState(BlendFunction blendFunction)
    {
        this(Optional.of(blendFunction), GpuFormat.RGBA8_UNORM, WRITE_ALL);
    }

    public boolean writeRed()
    {
        return (this.writeMask & WRITE_RED) != 0;
    }

    public boolean writeGreen()
    {
        return (this.writeMask & WRITE_GREEN) != 0;
    }

    public boolean writeBlue()
    {
        return (this.writeMask & WRITE_BLUE) != 0;
    }

    public boolean writeAlpha()
    {
        return (this.writeMask & WRITE_ALPHA) != 0;
    }
}
