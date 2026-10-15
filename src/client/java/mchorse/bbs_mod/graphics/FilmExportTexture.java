package mchorse.bbs_mod.graphics;

import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.texture.TextureFormat;
import org.lwjgl.opengl.GL11;

/** Owns the persistent film readback destination until Minecraft closes its GPU device. */
public final class FilmExportTexture
{
    private static Texture texture;

    private FilmExportTexture() {}

    public static Texture get()
    {
        if (texture == null)
        {
            texture = new Texture();
            // RenderPearl requires four channels; ExportBlit writes opaque alpha.
            texture.setFormat(TextureFormat.RGBA_U8);
            texture.setFilter(GL11.GL_NEAREST);
        }

        return texture;
    }

    public static void close()
    {
        if (texture != null)
        {
            texture.delete();
            texture = null;
        }
    }
}
