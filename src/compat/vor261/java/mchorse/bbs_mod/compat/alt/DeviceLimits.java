package mchorse.bbs_mod.compat.alt;

/** The part of 26.2's DeviceLimits BBS reads, for the versions before 26.1 (from Mojang's GpuDevice and GL). */
public record DeviceLimits(int maxAnisotropy, int minUniformOffsetAlignment, int maxTextureSize, int maxColorAttachments)
{
    public int maxTextureSizeForFormat(GpuFormat format)
    {
        return this.maxTextureSize;
    }
}
