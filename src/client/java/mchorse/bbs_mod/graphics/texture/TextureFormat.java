package mchorse.bbs_mod.graphics.texture;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

public enum TextureFormat
{
    RGBA_U8(GL11.GL_RGBA8, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, GL30.GL_COLOR_ATTACHMENT0),
    RGB_U8(GL11.GL_RGB8, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, GL30.GL_COLOR_ATTACHMENT0),
    RGBA_F16(GL30.GL_RGBA16F, GL11.GL_RGBA, GL11.GL_FLOAT, GL30.GL_COLOR_ATTACHMENT0),
    DEPTH_F24(GL30.GL_DEPTH_COMPONENT24, GL30.GL_DEPTH_COMPONENT, GL30.GL_FLOAT, GL30.GL_DEPTH_ATTACHMENT);

    public final int internal;
    public final int format;
    public final int type;
    public final int attachment;

    public static TextureFormat getByName(String name)
    {
        try
        {
            return valueOf(name.toUpperCase());
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return RGBA_U8;
    }

    private TextureFormat(int internal, int format, int type, int attachment)
    {
        this.internal = internal;
        this.format = format;
        this.type = type;
        this.attachment = attachment;
    }

    /**
     * Wie viele Bytes {@code glTexImage2D} pro Pixel aus dem Puffer LIEST.
     *
     * <p>Das ist eine Eigenschaft von {@link #format} und {@link #type}, NICHT von
     * {@link #internal} -- die interne Form bestimmt nur, was die Grafikkarte daraus macht.
     * Wer hier falsch rechnet, laesst den Treiber ueber das Pufferende hinaus lesen, und
     * das endet nicht mit einer Ausnahme, sondern mit einer Zugriffsverletzung.
     */
    public int bytesProPixel()
    {
        int kanaele = this.format == GL11.GL_RGBA ? 4 : (this.format == GL11.GL_RGB ? 3 : 1);
        int proKanal = this.type == GL11.GL_FLOAT ? 4 : 1;

        return kanaele * proKanal;
    }

    public boolean isDepth()
    {
        return this.format == GL30.GL_DEPTH_COMPONENT;
    }

    public boolean isColor()
    {
        return this.attachment == GL30.GL_COLOR_ATTACHMENT0;
    }
}
