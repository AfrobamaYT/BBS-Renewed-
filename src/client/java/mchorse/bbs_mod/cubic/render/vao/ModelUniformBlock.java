package mchorse.bbs_mod.cubic.render.vao;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import mchorse.bbs_mod.utils.MathUtils;
import org.joml.Matrix3f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Exact 96-byte std140 model block, with a unique native transient slice per draw. */
public final class ModelUniformBlock
{
    public static final int BYTES = 96;

    private ModelUniformBlock()
    {}

    public static GpuBufferSlice upload(Matrix3f normal, int light, int overlay, int mode,
        int picking, float emission)
    {
        var device = RenderSystem.getDevice();
        long alignment = Math.max(16, device.getDeviceInfo().limits().minUniformOffsetAlignment());
        try (GpuBufferSlice.MappedView mapped = device.createCommandEncoder().transientMemory()
            .allocateGpuMapped(BYTES, alignment, GpuBuffer.USAGE_UNIFORM))
        {
            write(mapped.data().order(ByteOrder.nativeOrder()), normal, light, overlay, mode, picking, emission);
            return mapped.slice();
        }
    }

    public static void write(ByteBuffer buffer, Matrix3f normal, int light, int overlay,
        int mode, int picking, float emission)
    {
        buffer.clear();
        row(buffer, normal.m00(), normal.m01(), normal.m02());
        row(buffer, normal.m10(), normal.m11(), normal.m12());
        row(buffer, normal.m20(), normal.m21(), normal.m22());
        buffer.putInt(light & 65535).putInt(light >>> 16);
        buffer.putInt(overlay & 65535).putInt(overlay >>> 16);
        buffer.putInt(mode).putInt(picking).putInt(0).putInt(0);
        buffer.putFloat(MathUtils.clamp(emission, 0F, 1F)).putFloat(0F).putFloat(0F).putFloat(0F);
        buffer.flip();
    }

    private static void row(ByteBuffer buffer, float x, float y, float z)
    {
        buffer.putFloat(x).putFloat(y).putFloat(z).putFloat(0F);
    }
}
