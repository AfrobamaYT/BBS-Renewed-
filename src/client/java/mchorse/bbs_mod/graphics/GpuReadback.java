package mchorse.bbs_mod.graphics;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.compat.alt.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.systems.CommandEncoder;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;
import java.util.function.Consumer;

/** Texture snapshots through Mojang's transfer API, on either graphics backend. */
public final class GpuReadback
{
    public enum Origin
    {
        /** Preserve texture Y0/row0; no interpretation of upload/render orientation. */
        NATIVE,
        FLIP_Y,
        /** Explicit framebuffer convention: interpret native rows as bottom-up and flip. */
        TOP_LEFT,
        /** Explicit framebuffer convention: preserve bottom-up rows. */
        BOTTOM_LEFT
    }

    /** Coordinates are in the texture's native mip-level pixel space. */
    public record Region(int x, int y, int width, int height)
    {
        public Region
        {
            if (x < 0 || y < 0 || width <= 0 || height <= 0)
            {
                throw new IllegalArgumentException("Readback region must be positive and inside the texture");
            }

            Math.addExact(x, width);
            Math.addExact(y, height);
        }
    }

    /** Owned CPU snapshot, tightly packed, original format and native row order. */
    public static final class Raw extends GpuReadbackPixels
    {
        public Raw(GpuFormat format, int width, int height, ByteBuffer data)
        {
            super(format, width, height, data);
        }
    }

    /** One transfer. GPU resources stay alive until completion even after cancellation. */
    public static final class Request extends GpuReadbackRequest
    {
        Request(CommandEncoder encoder, GpuFormat format, Region region, GpuBuffer buffer)
        {
            super(encoder, format, region, buffer);
        }
    }

    public static Request requestRaw(GpuTexture texture, Region region)
    {
        return requestRaw(mchorse.bbs_mod.compat.Geraet.get(), texture, 0, region);
    }

    public static Request requestRaw(GpuTextureView view, Region region)
    {
        if (view.isClosed()) throw new IllegalStateException("Readback texture view is closed");
        return requestRaw(mchorse.bbs_mod.compat.Geraet.get(), view.texture(), view.baseMipLevel(), region);
    }

    static Request requestRaw(GpuDevice device, GpuTexture texture, int mip, Region region)
    {
        return GpuReadbackRequest.create(device, texture, mip, region);
    }

    public static Raw readRaw(GpuTexture texture, Region region)
    {
        try (Request request = requestRaw(texture, region)) { return request.await(); }
    }

    public static Raw readRaw(GpuTexture texture)
    {
        return readRaw(texture, new Region(0, 0, texture.getWidth(0), texture.getHeight(0)));
    }

    /** Returned memAlloc buffer belongs to the caller. */
    public static ByteBuffer readRgba8(GpuTexture texture, Region region, Origin origin)
    {
        try (Raw raw = readRaw(texture, region)) { return raw.rgba8(origin); }
    }

    public static ByteBuffer readRgba8(GpuTexture texture, Origin origin)
    {
        return readRgba8(texture, new Region(0, 0, texture.getWidth(0), texture.getHeight(0)), origin);
    }

    public static void requireRgba8Convertible(GpuFormat format)
    {
        Objects.requireNonNull(format);
        if (format.hasDepthAspect())
        {
            if (format == GpuFormat.D32_FLOAT || format == GpuFormat.D16_UNORM) return;
        }
        else if (!format.hasStencilAspect())
        {
            switch (format.componentType())
            {
                case UNORM_8, UNORM_16, FLOAT_16, FLOAT_32: return;
                default: break;
            }
        }

        throw new IllegalArgumentException("RGBA8 conversion is unsupported for " + format);
    }

    static void outsidePass(CommandEncoder encoder)
    {
        if (encoder instanceof CommandEncoder frontend && frontend.isInRenderPass())
        {
            throw new IllegalStateException("Close the render pass before BBS GPU readback or submission");
        }
    }

    static int byteSize(GpuFormat format, int width, int height)
    {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Readback dimensions must be positive");
        return Math.multiplyExact(Math.multiplyExact(width, height), format.blockSize());
    }

    static int quantize(float value)
    {
        return Float.isNaN(value) ? 0 : Math.round(Math.clamp(value, 0F, 1F) * 255F);
    }

    private GpuReadback() {}
}
