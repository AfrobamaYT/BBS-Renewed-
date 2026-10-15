package mchorse.bbs_mod.compat;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.textures.GpuTexture;
import mchorse.bbs_mod.compat.alt.DeviceInfo;
import mchorse.bbs_mod.compat.alt.DeviceLimits;
import mchorse.bbs_mod.compat.alt.GpuFormat;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.function.Supplier;

/** 26.2's GpuDevice calls, for the versions before 26.1. Injected into GpuDevice. */
public interface GpuDeviceAlt
{
    default GpuTexture createTexture(String label, int usage, GpuFormat format, int width, int height, int layers, int mips)
    {
        return ((GpuDevice) this).createTexture(label, usage, format.textureFormat(), width, height, layers, mips);
    }

    default GpuTexture createTexture(Supplier<String> label, int usage, GpuFormat format, int width, int height, int layers, int mips)
    {
        return ((GpuDevice) this).createTexture(label, usage, format.textureFormat(), width, height, layers, mips);
    }

    default mchorse.bbs_mod.compat.alt.GpuQueryPool createTimestampQueryPool(int size)
    {
        return new mchorse.bbs_mod.compat.alt.GpuQueryPool(size);
    }

    default DeviceInfo getDeviceInfo()
    {
        GpuDevice device = (GpuDevice) this;
        DeviceLimits limits = new DeviceLimits(device.getMaxSupportedAnisotropy(), device.getUniformOffsetAlignment(),
            device.getMaxTextureSize(), GL11.glGetInteger(GL30.GL_MAX_COLOR_ATTACHMENTS));

        return new DeviceInfo(device.getRenderer(), device.getVendor(), device.getBackendName(), 1F, limits);
    }
}
