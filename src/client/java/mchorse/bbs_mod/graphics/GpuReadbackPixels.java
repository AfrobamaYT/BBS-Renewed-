package mchorse.bbs_mod.graphics;

import mchorse.bbs_mod.compat.alt.GpuFormat;
import org.lwjgl.system.MemoryUtil;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

class GpuReadbackPixels implements AutoCloseable
{
    private final GpuFormat format;
    private final int width;
    private final int height;
    private ByteBuffer data;

    /** Takes ownership of a memAlloc buffer; the caller must not free it afterward. */
    GpuReadbackPixels(GpuFormat format, int width, int height, ByteBuffer data)
    {
        this.format = Objects.requireNonNull(format);
        this.width = width;
        this.height = height;
        int bytes = GpuReadback.byteSize(format, width, height);

        if (!data.isDirect() || data.position() != 0 || data.remaining() != bytes)
        {
            throw new IllegalArgumentException("Raw snapshot requires exactly " + bytes + " native bytes");
        }

        this.data = data.order(ByteOrder.nativeOrder());
    }

    public GpuFormat format() { return this.format; }
    public int width() { return this.width; }
    public int height() { return this.height; }

    public ByteBuffer data()
    {
        if (this.data == null)
        {
            throw new IllegalStateException("CPU readback snapshot is closed");
        }

        return this.data.asReadOnlyBuffer().order(ByteOrder.nativeOrder());
    }

    /** Returns a separate memAlloc buffer; its recipient must memFree it. */
    public ByteBuffer rgba8(GpuReadback.Origin origin)
    {
        Objects.requireNonNull(origin);
        GpuReadback.requireRgba8Convertible(this.format);
        ByteBuffer source = this.data();
        ByteBuffer rgba = MemoryUtil.memAlloc(Math.multiplyExact(Math.multiplyExact(this.width, this.height), 4));

        try
        {
            boolean flip = origin == GpuReadback.Origin.FLIP_Y || origin == GpuReadback.Origin.TOP_LEFT;
            int stride = this.format.blockSize();

            if (this.format == GpuFormat.RGBA8_UNORM)
            {
                int rowBytes = this.width * 4;
                for (int y = 0; y < this.height; y++)
                {
                    int inputY = flip ? this.height - 1 - y : y;
                    rgba.put(source.duplicate().position(inputY * rowBytes).limit((inputY + 1) * rowBytes));
                }

                return rgba.flip();
            }

            for (int y = 0; y < this.height; y++)
            {
                int inputY = flip ? this.height - 1 - y : y;

                for (int x = 0; x < this.width; x++)
                {
                    int index = (inputY * this.width + x) * stride;

                    for (int channel = 0; channel < 4; channel++)
                    {
                        float value = this.component(source, index, channel);
                        rgba.put((byte) GpuReadback.quantize(value));
                    }
                }
            }

            return rgba.flip();
        }
        catch (Throwable error)
        {
            MemoryUtil.memFree(rgba);
            throw error;
        }
    }

    /** BGR24 for the existing ffmpeg input contract; recipient owns the allocation. */
    public ByteBuffer bgr8(GpuReadback.Origin origin)
    {
        Objects.requireNonNull(origin);
        if (this.format == GpuFormat.RGBA8_UNORM || this.format == GpuFormat.RGB8_UNORM)
        {
            ByteBuffer source = this.data();
            ByteBuffer bgr = MemoryUtil.memAlloc(Math.multiplyExact(Math.multiplyExact(this.width, this.height), 3));
            int stride = this.format.blockSize();
            boolean flip = origin == GpuReadback.Origin.FLIP_Y || origin == GpuReadback.Origin.TOP_LEFT;
            for (int y = 0; y < this.height; y++)
            {
                int inputY = flip ? this.height - 1 - y : y;
                for (int x = 0; x < this.width; x++)
                {
                    int i = (inputY * this.width + x) * stride;
                    bgr.put(source.get(i + 2)).put(source.get(i + 1)).put(source.get(i));
                }
            }

            return bgr.flip();
        }

        ByteBuffer rgba = this.rgba8(origin);
        ByteBuffer bgr = MemoryUtil.memAlloc(Math.multiplyExact(Math.multiplyExact(this.width, this.height), 3));

        try
        {
            for (int i = 0; i < rgba.remaining(); i += 4)
            {
                bgr.put(rgba.get(i + 2)).put(rgba.get(i + 1)).put(rgba.get(i));
            }

            return bgr.flip();
        }
        catch (Throwable error)
        {
            MemoryUtil.memFree(bgr);
            throw error;
        }
        finally
        {
            MemoryUtil.memFree(rgba);
        }
    }

    private float component(ByteBuffer source, int index, int channel)
    {
        if (this.format.hasDepthAspect())
        {
            if (channel == 3) return 1F;
            if (this.format == GpuFormat.D32_FLOAT) return source.getFloat(index);
            if (this.format == GpuFormat.D16_UNORM) return (source.getShort(index) & 0xFFFF) / 65535F;
            throw new IllegalArgumentException("Depth conversion is unsupported for " + this.format);
        }

        if (channel >= this.format.componentCount()) return channel == 3 ? 1F : 0F;

        return switch (this.format.componentType())
        {
            case UNORM_8 -> (source.get(index + channel) & 0xFF) / 255F;
            case UNORM_16 -> (source.getShort(index + channel * 2) & 0xFFFF) / 65535F;
            case FLOAT_16 -> Float.float16ToFloat(source.getShort(index + channel * 2));
            case FLOAT_32 -> source.getFloat(index + channel * 4);
            default -> throw new IllegalArgumentException("RGBA8 conversion is unsupported for " + this.format);
        };
    }

    @Override
    public void close()
    {
        if (this.data != null)
        {
            MemoryUtil.memFree(this.data);
            this.data = null;
        }
    }
}
