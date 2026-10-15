package mchorse.bbs_mod.compat;

import com.mojang.blaze3d.buffers.GpuBuffer;
import mchorse.bbs_mod.compat.mixin.SequenzPufferAltMixin;

/** vor1215: 1.21.11s AutoStorageIndexBuffer.getBuffer(n); 1.21.4 bindet nur (bind), das stoert das VAO. */
public interface SequenzPufferAlt
{
    default GpuBuffer getBuffer(int anzahl)
    {
        SequenzPufferAltMixin puffer = (SequenzPufferAltMixin) this;

        puffer.bbs$sicherstellen(anzahl);

        return puffer.bbs$puffer();
    }
}
