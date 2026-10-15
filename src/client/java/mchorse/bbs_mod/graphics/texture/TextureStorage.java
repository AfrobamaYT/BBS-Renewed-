package mchorse.bbs_mod.graphics.texture;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;

import java.util.HashMap;
import java.util.Map;

/** Transactional storage replacement; views close before their image. */
final class TextureStorage implements AutoCloseable
{
    final GpuTexture texture;
    final GpuTextureView view;
    private GpuTextureView attachment;
    private final Map<Integer, GpuTextureView> samplingViews = new HashMap<>();

    private TextureStorage(GpuTexture texture, GpuTextureView view)
    {
        this.texture = texture;
        this.view = view;
    }

    static TextureStorage create(int width, int height, TextureFormat format, int levels, GpuTexture previous)
    {
        RenderSystem.assertOnRenderThread();
        if (width < 1 || height < 1 || levels < 1 || levels > TextureMipmaps.levels(width, height))
            throw new IllegalArgumentException("Invalid BBS texture storage dimensions/mip levels");
        GpuDevice device = mchorse.bbs_mod.compat.Geraet.get();
        GpuTexture image = device.createTexture("bbs-texture", GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_COPY_DST
            | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
            TextureUpload.gpuFormat(format), width, height, 1, levels);
        GpuTextureView view = null;
        try
        {
            view = device.createTextureView(image);
            if (previous != null)
            {
                if (previous.isClosed() || previous.getWidth(0) != width || previous.getHeight(0) != height
                    || previous.bbsGpuFormat() != image.bbsGpuFormat() || previous.getDepthOrLayers() != 1)
                    throw new IllegalArgumentException("Preserved texture storage must have the exact same dimensions/format/layer");
                for (int level = 0, count = Math.min(previous.getMipLevels(), image.getMipLevels()); level < count; level++)
                {
                    device.createCommandEncoder().copyTextureToTexture(previous, image, level, 0, 0, 0, 0,
                        previous.getWidth(level), previous.getHeight(level));
                }
            }
            return new TextureStorage(image, view);
        }
        catch (Throwable error)
        {
            if (view != null) view.close();
            image.close();
            throw error;
        }
    }

    GpuTextureView samplingView(int maxLevel)
    {
        int levels = Math.min(maxLevel, this.texture.getMipLevels() - 1) + 1;
        if (levels == this.texture.getMipLevels()) return this.view;
        return this.samplingViews.computeIfAbsent(levels,
            count -> mchorse.bbs_mod.compat.Geraet.get().createTextureView(this.texture, 0, count));
    }

    GpuTextureView attachmentView()
    {
        if (this.texture.getMipLevels() == 1) return this.view;
        if (this.attachment == null) this.attachment = mchorse.bbs_mod.compat.Geraet.get().createTextureView(this.texture, 0, 1);
        return this.attachment;
    }

    @Override
    public void close()
    {
        try
        {
            if (this.attachment != null) this.attachment.close();
        }
        finally
        {
            try
            {
                for (GpuTextureView samplingView : this.samplingViews.values()) samplingView.close();
                this.samplingViews.clear();
            }
            finally
            {
                try { this.view.close(); }
                finally { this.texture.close(); }
            }
        }
    }
}
