package mchorse.bbs_mod.graphics.texture;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.textures.GpuTexture;
//? if >=26.1 {
import com.mojang.renderpearl.backend.vulkan.VulkanCommandEncoder;
import com.mojang.renderpearl.backend.vulkan.VulkanGpuTexture;
//?}
import com.mojang.renderpearl.frontend.FrontendCommandEncoder;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Converts BBS input pixels to the declared GPU storage, without changing input ownership. */
public final class TextureUpload
{
    private TextureUpload()
    {}

    public static final class Encoded implements AutoCloseable
    {
        private final ByteBuffer bytes;
        private final boolean owned;
        private boolean closed;

        Encoded(ByteBuffer bytes, boolean owned) { this.bytes = bytes; this.owned = owned; }
        public ByteBuffer bytes()
        {
            if (this.closed) throw new IllegalStateException("Encoded upload is closed");
            return this.bytes;
        }
        public boolean owned() { return this.owned; }
        @Override
        public void close()
        {
            if (!this.closed && this.owned) MemoryUtil.memFree(this.bytes);
            this.closed = true;
        }
    }

    public static GpuFormat gpuFormat(TextureFormat format)
    {
        return switch (format)
        {
            case RGB_U8, RGBA_U8 -> GpuFormat.RGBA8_UNORM;
            case RGBA_F16 -> GpuFormat.RGBA16_FLOAT;
            case DEPTH_F24 -> GpuFormat.D32_FLOAT;
        };
    }

    public static void validateSource(TextureFormat format, int width, int height, ByteBuffer source)
    {
        if (width < 1 || height < 1 || source == null || !source.isDirect())
        {
            throw new IllegalArgumentException("Texture upload requires positive dimensions and direct pixel storage");
        }

        int pixels = Math.multiplyExact(width, height);
        int required = Math.multiplyExact(pixels, format.bytesProPixel());

        if (source.remaining() < required)
        {
            throw new IllegalArgumentException("Texture upload " + width + "x" + height + " as "
                + format + " requires " + required + " bytes, available " + source.remaining()
                + " at position " + source.position());
        }

    }

    public static Encoded encode(TextureFormat format, int width, int height, ByteBuffer source)
    {
        validateSource(format, width, height, source);
        int pixels = Math.multiplyExact(width, height);
        int required = Math.multiplyExact(pixels, format.bytesProPixel());
        ByteBuffer input = source.slice().order(ByteOrder.nativeOrder());
        input.limit(required);

        if (format == TextureFormat.RGBA_U8 || format == TextureFormat.DEPTH_F24)
        {
            return new Encoded(input, false);
        }

        int stride = format == TextureFormat.RGB_U8 ? 4 : 8;
        ByteBuffer output = MemoryUtil.memAlloc(Math.multiplyExact(pixels, stride)).order(ByteOrder.nativeOrder());

        try
        {
            for (int pixel = 0; pixel < pixels; pixel++)
            {
                if (format == TextureFormat.RGB_U8)
                {
                    output.put(input.get()).put(input.get()).put(input.get()).put((byte) 255);
                }
                else if (format == TextureFormat.RGBA_F16)
                {
                    for (int channel = 0; channel < 4; channel++)
                    {
                        output.putShort(Float.floatToFloat16(input.getFloat()));
                    }
                }
                else
                {
                    throw new IllegalArgumentException("Unsupported BBS texture format: " + format);
                }
            }

            output.flip();
            return new Encoded(output, true);
        }
        catch (Throwable error)
        {
            MemoryUtil.memFree(output);
            throw error;
        }
    }

    public static void write(GpuTexture texture, TextureFormat format, int level,
        int width, int height, ByteBuffer source)
    {
        RenderSystem.assertOnRenderThread();

        validateDestination(texture, format, level, width, height);
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        if (encoder instanceof FrontendCommandEncoder frontend && frontend.isInRenderPass())
            throw new IllegalStateException("Close the render pass before BBS texture upload");
        try (Encoded encoded = encode(format, width, height, source))
        {
            //? if >=26.1 {
            if (format.isDepth() && texture instanceof VulkanGpuTexture image)
            {
                if (!(encoder instanceof FrontendCommandEncoder frontend)
                    || !(frontend.backend() instanceof VulkanCommandEncoder nativeEncoder))
                    throw new IllegalStateException("Depth image and actual command encoder backend disagree");
                VulkanDepthUpload.write(nativeEncoder, image, level, width, height, encoded.bytes());
            }
            else
            //?}
            {
                /* Both backends copy into their own storage before returning. */
                encoder.writeToTexture(texture, encoded.bytes(), level, 0, 0, 0, width, height);
            }
        }
    }

    public static void validateDestination(GpuTexture texture, TextureFormat format, int level, int width, int height)
    {
        if (texture.isClosed() || level < 0 || level >= texture.getMipLevels() || width < 1 || height < 1
            || texture.getWidth(level) != width || texture.getHeight(level) != height
            || texture.getFormat() != gpuFormat(format) || texture.getDepthOrLayers() != 1
            || (texture.usage() & GpuTexture.USAGE_COPY_DST) == 0)
            throw new IllegalArgumentException("BBS upload requires matching live format/level/dimensions, one layer and COPY_DST");
    }

    static boolean hasTranslucency(TextureFormat format, int width, int height, ByteBuffer source)
    {
        if (format == TextureFormat.RGB_U8 || format.isDepth()) return false;
        ByteBuffer input = source.slice().order(ByteOrder.nativeOrder());
        for (int pixel = 0, count = Math.multiplyExact(width, height); pixel < count; pixel++)
        {
            if (format == TextureFormat.RGBA_U8)
            {
                int alpha = input.get(pixel * 4 + 3) & 255;
                if (alpha >= 26 && alpha <= 254) return true;
            }
            else
            {
                float alpha = input.getFloat(pixel * 16 + 12);
                if (alpha > 0.1F && alpha < 1F) return true;
            }
        }
        return false;
    }
}
