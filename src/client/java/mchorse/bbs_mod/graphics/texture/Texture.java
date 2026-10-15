package mchorse.bbs_mod.graphics.texture;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import mchorse.bbs_mod.graphics.GpuReadback;
import mchorse.bbs_mod.utils.resources.Pixels;
import net.minecraft.client.gui.render.TextureSetup;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.util.Objects;

/** BBS texture ownership and CPU input formats on Minecraft's actual graphics device. */
public class Texture
{
    /** A real OpenGL name only. Vulkan never exposes its image as an integer GL name. */
    public int id = -1;
    public int target = GL11.GL_TEXTURE_2D;
    public int width;
    public int height;

    private static boolean nullGroesseGemeldet;
    private boolean mipmap;
    private boolean clearable;
    private boolean translucent;
    private AnimatedTexture parent;
    private TextureFormat format = TextureFormat.RGBA_U8;
    private TextureStorage storage;
    private final TextureSamplerState parameters = new TextureSamplerState();

    public static Pixels pixelsFromTexture(Texture texture)
    {
        if (!texture.isValid()) return null;
        ByteBuffer pixels = GpuReadback.readRgba8(texture.gpu(), GpuReadback.Origin.NATIVE);
        return new Pixels(pixels, texture.width, texture.height);
    }

    public static Texture textureFromPixels(Pixels pixels, int filter)
    {
        Texture texture = new Texture();
        try
        {
            texture.setFilter(filter);
            texture.uploadTexture(pixels);
            return texture;
        }
        catch (Throwable error)
        {
            texture.delete();
            throw error;
        }
    }

    public Texture()
    {
        this.allocate(1, 1, this.format, 1, false);
    }

    private void allocate(int width, int height, TextureFormat format, int levels, boolean preserve)
    {
        RenderSystem.assertOnRenderThread();
        if (width <= 0 || height <= 0)
        {
            if (!nullGroesseGemeldet)
            {
                nullGroesseGemeldet = true;
                new IllegalArgumentException("BBS texture size " + width + "x" + height + " clamped to 1x1").printStackTrace();
            }
            width = Math.max(width, 1);
            height = Math.max(height, 1);
        }
        if (this.isValid() && this.width == width && this.height == height
            && this.gpu().getFormat() == TextureUpload.gpuFormat(format) && this.gpu().getMipLevels() == levels)
        {
            return;
        }
        TextureStorage replacement = TextureStorage.create(width, height, format, levels,
            preserve && this.isValid() ? this.gpu() : null);
        TextureStorage previous = this.storage;
        this.storage = replacement;
        this.width = width;
        this.height = height;
        this.id = replacement.texture instanceof GlTexture gl ? gl.glId() : -1;
        if (previous != null) previous.close();
        this.parameters.applyLegacy(this.gpu());
    }

    public GpuTexture gpu()
    {
        if (!this.isValid()) throw new IllegalStateException("BBS texture storage is closed");
        return this.storage.texture;
    }

    public GpuTextureView view()
    {
        this.gpu();
        return this.storage.samplingView(this.parameters.get(org.lwjgl.opengl.GL12.GL_TEXTURE_MAX_LEVEL));
    }

    /** Attachments address one mip; sampling views may address the full chain. */
    public GpuTextureView attachmentView()
    {
        this.gpu();
        return this.storage.attachmentView();
    }

    public GpuSampler sampler()
    {
        return this.parameters.sampler(this.mipmap, this.gpu().getMipLevels());
    }

    public TextureSetup guiSetup()
    {
        return TextureSetup.singleTexture(this.view(), this.sampler());
    }

    public void setParent(AnimatedTexture parent) { this.parent = parent; }
    public AnimatedTexture getParent() { return this.parent; }
    public void setClearable(boolean clearable) { this.clearable = clearable; }
    public boolean isClearable() { return this.clearable; }
    public TextureFormat getFormat() { return this.format; }
    public boolean isMipmap() { return this.mipmap; }
    public boolean hasTranslucency() { return this.translucent; }

    public boolean isReallyMipmap()
    {
        return this.mipmap && this.isValid() && this.gpu().getMipLevels() > 1
            && this.getParameter(org.lwjgl.opengl.GL12.GL_TEXTURE_MAX_LEVEL) > 0;
    }

    public boolean isValid()
    {
        return this.storage != null && !this.storage.texture.isClosed();
    }

    private void requireLegacyBinding()
    {
        RenderSystem.assertOnRenderThread();
        if (!(this.gpu() instanceof GlTexture) || this.target != GL11.GL_TEXTURE_2D)
        {
            throw new IllegalStateException("Legacy Texture.bind/unbind requires an OpenGL 2D texture; use gpu/view/sampler for "
                + this.gpu().getClass().getName());
        }
    }

    public void bind()
    {
        this.requireLegacyBinding();
        GlStateManager._bindTexture(this.id);
    }

    public void bind(int texture)
    {
        this.requireLegacyBinding();
        GlStateManager._activeTexture(texture);
        this.bind();
    }

    public void unbind()
    {
        this.requireLegacyBinding();
        GlStateManager._bindTexture(0);
    }

    public void unbind(int texture)
    {
        this.requireLegacyBinding();
        GlStateManager._activeTexture(texture);
        this.unbind();
    }

    public void setFormat(TextureFormat format)
    {
        Objects.requireNonNull(format);
        if (this.format != format)
        {
            this.allocate(this.width, this.height, format, this.mipmap ? TextureMipmaps.levels(this.width, this.height) : 1, false);
            this.format = format;
            this.mipmap = false;
            this.translucent = false;
        }
    }

    public int getFilter() { return this.getParameter(GL11.GL_TEXTURE_MIN_FILTER); }
    public boolean isLinear() { return TextureSamplerState.filter(this.getFilter()) == com.mojang.blaze3d.textures.FilterMode.LINEAR; }
    public int getParameter(int parameter) { return this.parameters.get(parameter); }

    public void setFilterMipmap(boolean linear, boolean mipmap)
    {
        this.setFilter(mipmap ? (linear ? GL11.GL_LINEAR_MIPMAP_NEAREST : GL11.GL_NEAREST_MIPMAP_NEAREST)
            : (linear ? GL11.GL_LINEAR : GL11.GL_NEAREST));
        if (mipmap && !this.mipmap) this.generateMipmap();
        this.setParameter(org.lwjgl.opengl.GL12.GL_TEXTURE_MAX_LEVEL, mipmap ? 4 : 0);
    }

    public void setFilter(int filter)
    {
        int magnification = TextureSamplerState.filter(filter) == com.mojang.blaze3d.textures.FilterMode.LINEAR
            ? GL11.GL_LINEAR : GL11.GL_NEAREST;
        this.setParameter(GL11.GL_TEXTURE_MAG_FILTER, magnification);
        this.setParameter(GL11.GL_TEXTURE_MIN_FILTER, filter);
    }

    public void setWrap(int mode)
    {
        this.setParameter(GL11.GL_TEXTURE_WRAP_S, mode);
        this.setParameter(GL11.GL_TEXTURE_WRAP_T, mode);
    }

    public void setParameter(int parameter, int value)
    {
        RenderSystem.assertOnRenderThread();
        this.parameters.set(parameter, value);
        if (this.isValid()) this.parameters.applyLegacy(this.gpu());
    }

    public void delete()
    {
        this.parameters.close();
        if (this.storage != null) this.storage.close();
        this.storage = null;
        this.id = -1;
        this.mipmap = false;
    }

    public void setSize(int width, int height)
    {
        int safeWidth = Math.max(width, 1), safeHeight = Math.max(height, 1);
        this.allocate(width, height, this.format, this.mipmap ? TextureMipmaps.levels(safeWidth, safeHeight) : 1, false);
        this.mipmap = false;
        this.translucent = false;
    }

    /** Updates retain caller ownership of Pixels. Upload overloads consume Pixels on success. */
    public void updateTexture(Pixels pixels) { this.updateTexture(this.target, pixels); }
    public void updateTexture(int target, Pixels pixels)
    {
        this.uploadTexture(target, 0, pixels.width, pixels.height, pixels.getBuffer());
    }
    public void uploadTexture(Pixels pixels) { this.uploadTexture(this.target, pixels); }
    public void uploadTexture(int target, Pixels pixels) { this.uploadTexture(target, 0, pixels); }

    public void uploadTexture(int target, int level, Pixels pixels)
    {
        TextureFormat format = switch (pixels.bits)
        {
            case 3 -> TextureFormat.RGB_U8;
            case 4 -> TextureFormat.RGBA_U8;
            default -> throw new IllegalArgumentException("BBS Pixels upload requires RGB or RGBA bytes, got " + pixels.bits);
        };
        if (target != GL11.GL_TEXTURE_2D || this.target != target || level < 0)
            throw new IllegalArgumentException("BBS native uploads require a nonnegative mip of a 2D texture");
        TextureUpload.validateSource(format, pixels.width, pixels.height, pixels.getBuffer());
        if (level == 0) this.setFormat(format);
        else if (format != this.format) throw new IllegalArgumentException("Mip pixel format differs from level zero");
        this.uploadTexture(target, level, pixels.width, pixels.height, pixels.getBuffer());
        pixels.delete();
    }

    public void uploadTexture(int target, int level, int width, int height, ByteBuffer buffer)
    {
        if (target != GL11.GL_TEXTURE_2D || this.target != target || level < 0)
            throw new IllegalArgumentException("BBS native uploads require a nonnegative mip of a 2D texture");
        if (width < 1 || height < 1) throw new IllegalArgumentException("Texture upload dimensions must be positive");
        if (buffer != null) TextureUpload.validateSource(this.format, width, height, buffer);
        if (level == 0)
        {
            this.allocate(width, height, this.format, this.mipmap ? TextureMipmaps.levels(width, height) : 1, false);
        }
        else
        {
            if (level >= TextureMipmaps.levels(this.width, this.height)
                || width != Math.max(1, this.width >> level) || height != Math.max(1, this.height >> level))
                throw new IllegalArgumentException("Mip upload dimensions do not match texture level " + level);
            if (this.gpu().getMipLevels() <= level) this.allocate(this.width, this.height, this.format, level + 1, true);
        }
        if (buffer != null)
        {
            TextureUpload.write(this.gpu(), this.format, level, width, height, buffer);
            if (level == 0)
            {
                this.translucent = TextureUpload.hasTranslucency(this.format, width, height, buffer);
                if (this.mipmap) TextureMipmaps.generate(this.gpu());
            }
        }
    }

    public void generateMipmap()
    {
        int levels = TextureMipmaps.levels(this.width, this.height);
        if (this.gpu().getMipLevels() != levels) this.allocate(this.width, this.height, this.format, levels, true);
        TextureMipmaps.generate(this.gpu());
        this.mipmap = true;
    }
}
