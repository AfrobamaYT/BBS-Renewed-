package mchorse.bbs_mod.utils.resources;

import javax.imageio.ImageIO;
import mchorse.bbs_mod.utils.resources.Pixels;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Converts between BBS {@link Pixels} (LWJGL ByteBuffer-backed) and
 * Java AWT {@link BufferedImage} (system-clipboard, ImageIO PNG bytes).
 *
 * Used by the texture-editor clipboard-paste flow and the AI
 * background-removal HTTP roundtrip.
 *
 * Pixels layout: tightly-packed RGBA bytes, 4 bytes per pixel when bits=4.
 * BufferedImage uses TYPE_INT_ARGB (alpha in highest byte, RGB packed).
 */
public final class PixelsImageBridge
{
    private PixelsImageBridge() {}

    public static BufferedImage toBufferedImage(Pixels pixels)
    {
        BufferedImage image = new BufferedImage(pixels.width, pixels.height, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < pixels.height; y++)
        {
            for (int x = 0; x < pixels.width; x++)
            {
                int idx = (x + y * pixels.width) * pixels.bits;
                pixels.getBuffer().position(idx);

                int r = pixels.getBuffer().get() & 0xff;
                int g = pixels.getBuffer().get() & 0xff;
                int b = pixels.getBuffer().get() & 0xff;
                int a = pixels.bits == 4 ? (pixels.getBuffer().get() & 0xff) : 0xff;

                image.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }

        return image;
    }

    public static Pixels fromBufferedImage(BufferedImage image) throws IOException
    {
        BufferedImage rgba = image;

        if (rgba.getType() != BufferedImage.TYPE_INT_ARGB)
        {
            BufferedImage converted = new BufferedImage(rgba.getWidth(), rgba.getHeight(), BufferedImage.TYPE_INT_ARGB);
            converted.getGraphics().drawImage(rgba, 0, 0, null);
            rgba = converted;
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream(rgba.getWidth() * rgba.getHeight() * 4);
        ImageIO.write(rgba, "png", baos);
        return fromPNGBytes(baos.toByteArray());
    }

    public static byte[] toPNGBytes(Pixels pixels) throws IOException
    {
        BufferedImage image = toBufferedImage(pixels);
        ByteArrayOutputStream baos = new ByteArrayOutputStream(pixels.width * pixels.height * 4);
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }

    public static Pixels fromPNGBytes(byte[] data) throws IOException
    {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data))
        {
            return Pixels.fromPNGStream(bais);
        }
    }

}
