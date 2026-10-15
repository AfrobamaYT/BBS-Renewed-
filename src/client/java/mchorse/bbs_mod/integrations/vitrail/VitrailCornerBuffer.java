package mchorse.bbs_mod.integrations.vitrail;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.buffers.GpuBuffer;
import org.lwjgl.system.MemoryUtil;

/** One immutable sequential corner stream per owning mesh; never CPU skinning or per-frame packing. */
public final class VitrailCornerBuffer
{
    private VitrailCornerBuffer() {}

    public static GpuBuffer create(int count)
    {
        RenderSystem.assertOnRenderThread();
        if (count < 1 || count % 3 != 0) throw new IllegalArgumentException("Native Vitrail mesh is not complete triangles");
        var data = MemoryUtil.memAlloc(Math.multiplyExact(count, 4)).order(java.nio.ByteOrder.nativeOrder());
        try
        {
            for (int corner = 0; corner < count; corner++) data.putInt(corner);
            data.flip();
            return mchorse.bbs_mod.compat.Geraet.get().createBuffer(() -> "bbs-vitrail-corners",
                mchorse.bbs_mod.compat.gpu.PufferNutzung.USAGE_VERTEX | mchorse.bbs_mod.compat.gpu.PufferNutzung.USAGE_INDEX | mchorse.bbs_mod.compat.gpu.PufferNutzung.USAGE_UNIFORM_TEXEL_BUFFER | mchorse.bbs_mod.compat.gpu.PufferNutzung.USAGE_COPY_SRC,
                data);
        }
        finally { MemoryUtil.memFree(data); }
    }
}
