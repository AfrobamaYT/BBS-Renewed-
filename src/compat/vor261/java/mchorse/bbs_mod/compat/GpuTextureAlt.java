package mchorse.bbs_mod.compat;

import com.mojang.blaze3d.textures.GpuTexture;
import mchorse.bbs_mod.compat.alt.GpuFormat;

/**
 * 26.2's GpuTexture.getFormat() returns a GpuFormat, Mojang's before 26.1 a TextureFormat; the
 * shared code calls it as bbsGpuFormat() there (stonecutter-regeln.gradle). Injected into GpuTexture.
 */
public interface GpuTextureAlt
{
    default GpuFormat bbsGpuFormat()
    {
        return GpuFormat.von(((GpuTexture) this).getFormat());
    }
}
