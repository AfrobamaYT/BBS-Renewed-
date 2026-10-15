package mchorse.bbs_mod.compat.gpu;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import mchorse.bbs_mod.compat.alt.GpuFormat;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

/** Schreiben, Lesen und Kopieren von Texturen fuer die Schicht vor 1.21.5. */
public final class TexturBefehle
{
    private final RahmenPuffer rahmen;
    private final GpuAbfragen abfragen;
    private int lesePuffer;

    public TexturBefehle(RahmenPuffer rahmen, GpuAbfragen abfragen)
    {
        this.rahmen = rahmen;
        this.abfragen = abfragen;
    }

    public static void pruefen(GpuTexture texture, int mipLevel, int x, int y, int width, int height)
    {
        if (mipLevel < 0 || mipLevel >= texture.getMipLevels()) throw new IllegalArgumentException("Ungueltige Mip-Stufe " + mipLevel);
        if (x + width > texture.getWidth(mipLevel) || y + height > texture.getHeight(mipLevel)) throw new IllegalArgumentException("Bereich ausserhalb von " + texture.getLabel());
        if (texture.isClosed()) throw new IllegalStateException("Textur " + texture.getLabel() + " ist geschlossen");
    }

    public void bild(GpuTexture texture, NativeImage image, int mipLevel, int x, int y, int width, int height, int sourceX, int sourceY)
    {
        pruefen(texture, mipLevel, x, y, width, height);
        GlStateManager._bindTexture(((GlTexture) texture).id);
        BildHochladen.hochladen(image, mipLevel, x, y, sourceX, sourceY, width, height);
    }

    public void bytes(GpuTexture texture, ByteBuffer data, NativeImage.Format format, int mipLevel, int x, int y, int width, int height)
    {
        pruefen(texture, mipLevel, x, y, width, height);
        GlStateManager._bindTexture(((GlTexture) texture).id);
        auspacken(width, format.components());
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, mipLevel, x, y, width, height, format.glFormat(), GL11.GL_UNSIGNED_BYTE, data);
    }

    /** 26.2-Reihenfolge: Mip, Schicht, x, y, Breite, Hoehe; das Format kommt aus der Textur. */
    public void bytes262(GpuTexture texture, ByteBuffer source, int mipLevel, int layer, int x, int y, int width, int height)
    {
        pruefen(texture, mipLevel, x, y, width, height);

        GpuFormat format = texture.bbsGpuFormat();

        if ((long) width * height * format.blockSize() > source.remaining()) throw new IllegalArgumentException("Schreiben liefe ueber das Quellende");

        int ziel = GL11.GL_TEXTURE_2D;

        if ((texture.usage() & GpuTexture.USAGE_CUBEMAP_COMPATIBLE) != 0)
        {
            ziel = 34069 + layer % 6;
            GL11.glBindTexture(34067, ((GlTexture) texture).id);
        }
        else
        {
            GlStateManager._bindTexture(((GlTexture) texture).id);
        }

        int b = format.blockSize();

        auspacken(width, b % 8 == 0 ? 8 : b % 4 == 0 ? 4 : b % 2 == 0 ? 2 : 1);
        GL11.glTexSubImage2D(ziel, mipLevel, x, y, width, height, format.glExternal, format.glType, source);
    }

    private static void auspacken(int breite, int ausrichtung)
    {
        GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, breite);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, ausrichtung);
    }

    /** Liest einen Bereich in einen Puffer; fertig laeuft, sobald die GPU durch ist. */
    public void lesen(GpuTexture texture, GpuBuffer buffer, long offset, Runnable fertig, int mipLevel, int x, int y, int width, int height)
    {
        GpuFormat format = texture.bbsGpuFormat();

        if ((long) width * height * format.blockSize() + offset > buffer.size()) throw new IllegalArgumentException("Puffer zu klein fuer " + width + "x" + height + " " + format);
        if (x + width > texture.getWidth(mipLevel) || y + height > texture.getHeight(mipLevel)) throw new IllegalArgumentException("Lesebereich ausserhalb von " + texture.getLabel());

        boolean tiefe = format.hasDepthAspect();
        int anhang = tiefe ? GL30.GL_DEPTH_ATTACHMENT : GL30.GL_COLOR_ATTACHMENT0;

        if (this.lesePuffer == 0) this.lesePuffer = GL30.glGenFramebuffers();

        int alt = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);

        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, this.lesePuffer);
        GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, anhang, GL11.GL_TEXTURE_2D, ((GlTexture) texture).id, mipLevel);
        GL11.glReadBuffer(tiefe ? GL11.GL_NONE : GL30.GL_COLOR_ATTACHMENT0);
        GlStateManager._glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, buffer.handle);
        GlStateManager._pixelStore(GL11.GL_PACK_ROW_LENGTH, width);
        GlStateManager._pixelStore(GL11.GL_PACK_ALIGNMENT, format.blockSize() % 4 == 0 ? 4 : 1);
        GL11.glReadPixels(x, y, width, height, format.glExternal, format.glType, offset);
        GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, anhang, GL11.GL_TEXTURE_2D, 0, mipLevel);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, alt);
        GlStateManager._glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
        GlStateManager._pixelStore(GL11.GL_PACK_ROW_LENGTH, 0);

        int fehler = GL11.glGetError();

        if (fehler != 0) throw new IllegalStateException("Lesen aus " + texture.getLabel() + " scheitert: GL-Fehler " + fehler);

        this.abfragen.nachGpu(fertig);
    }

    public void kopieren(GpuTexture source, GpuTexture target, int mipLevel, int targetX, int targetY, int sourceX, int sourceY, int width, int height)
    {
        boolean tiefe = source.getFormat().hasDepthAspect();

        this.blitten(this.rahmen.framebuffer((GlTexture) source, mipLevel, null), this.rahmen.framebuffer((GlTexture) target, mipLevel, null),
            sourceX, sourceY, targetX, targetY, width, height, tiefe ? GL11.GL_DEPTH_BUFFER_BIT : GL11.GL_COLOR_BUFFER_BIT);
    }

    /** Ins Fenster (Framebuffer 0). */
    public void zeigen(GpuTextureView view)
    {
        this.blitten(this.rahmen.framebuffer((GlTexture) view.texture(), view.baseMipLevel(), null), 0,
            0, 0, 0, 0, view.getWidth(0), view.getHeight(0), GL11.GL_COLOR_BUFFER_BIT);
    }

    private void blitten(int von, int nach, int vx, int vy, int nx, int ny, int breite, int hoehe, int maske)
    {
        int altLesen = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int altZeichnen = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);

        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, von);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, nach);
        GL30.glBlitFramebuffer(vx, vy, vx + breite, vy + hoehe, nx, ny, nx + breite, ny + hoehe, maske, GL11.GL_NEAREST);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, altLesen);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, altZeichnen);
    }
}
