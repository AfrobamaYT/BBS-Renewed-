package mchorse.bbs_mod.utils;

import com.mojang.blaze3d.opengl.GlStateManager;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.utils.resources.Pixels;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Screenshot recorder
 *
 * This class is responsible for taking a screenshot and saving it in
 * the screenshot's directory
 */
public class ScreenshotRecorder
{
    public File screenshots;

    public boolean take;
    public boolean toBuffer;

    public ScreenshotRecorder(File screenshots)
    {
        this.screenshots = screenshots;
        this.screenshots.mkdirs();
    }

    public File getScreenshots()
    {
        return this.screenshots;
    }

    public void takeScreenshot(File output, Texture texture)
    {
        this.takeScreenshot(output, texture.id, texture.width, texture.height);
    }

    /**
     * Take a screenshot from a texture and save it to designated file
     */
    public void takeScreenshot(File output, int texture, int width, int height)
    {
        GlStateManager._bindTexture(texture);
        int actualWidth = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        int actualHeight = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);

        if (actualWidth <= 0 || actualHeight <= 0)
        {
            throw new IllegalStateException("Screenshot texture " + texture + " has no level-0 image");
        }

        if (actualWidth != width || actualHeight != height)
        {
            org.apache.logging.log4j.LogManager.getLogger("bbs/screenshot").warn(
                "Screenshot texture {} is {}x{}, requested {}x{}; reading actual size",
                texture, actualWidth, actualHeight, width, height);
        }

        /* Packed ARGB in the texture's own 8-bit layout: no CPU float conversion in the driver,
         * a quarter of the data, and exactly the ints the former GL_FLOAT loop built
         * ((int) ((c / 255F) * 255F) == c for all 256 c). The GL_FLOAT read plus that loop held
         * the render thread 6-33 ms on every film save (27.09.2026); the row flip now runs on
         * the writer thread. */
        IntBuffer argb = BufferUtils.createIntBuffer(Math.multiplyExact(actualWidth, actualHeight));

        GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, argb);

        new Thread(new ScreenshotRunner(actualWidth, actualHeight, argb, output)).start();
    }

    /**
     * Take a screenshot from the screen and save it to designated file
     */
    public void takeScreenshot(File output, int width, int height)
    {
        FloatBuffer pixelData = BufferUtils.createFloatBuffer(width * height * 4);

        GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_FLOAT, pixelData);
        pixelData.rewind();

        this.saveScreenshot(pixelData, output, width, height);
    }

    private void saveScreenshot(FloatBuffer pixelData, File output, int width, int height)
    {
        /* Pixel data must be converted first from floats to 32 bit hex */
        int[] pixels = new int[width * height];

        for (int y = 0; y < height; ++y)
        {
            for (int x = 0; x < width; ++x)
            {
                float r = pixelData.get() * 255;
                float g = pixelData.get() * 255;
                float b = pixelData.get() * 255;
                float a = pixelData.get() * 255;
                int i = ((height - 1) - y) * width + x;

                pixels[i] = ((int) a << 24) + ((int) r << 16) + ((int) g << 8) + (int) b;
            }
        }

        ScreenshotRunner runner = new ScreenshotRunner(width, height, pixels, output);

        new Thread(runner).start();
    }

    /**
     * Get screenshot file for a screenshot
     */
    public File getScreenshotFile()
    {
        return new File(this.screenshots, StringUtils.createTimestampFilename() + ".png");
    }

    /**
     * Screenshot runner
     * <p>
     * This dude right here is responsible for saving given RGB data to
     * a PNG file to given designated file.
     */
    public static class ScreenshotRunner implements Runnable, ClipboardOwner
    {
        public int width;
        public int height;

        public int[] data;
        public File destination;

        /** Rows bottom-up as read from GL; flipped into {@link #data} on this thread. */
        private IntBuffer gelesen;

        public ScreenshotRunner(int width, int height, int[] data, File destination)
        {
            this.width = width;
            this.height = height;
            this.data = data;
            this.destination = destination;
        }

        public ScreenshotRunner(int width, int height, IntBuffer gelesen, File destination)
        {
            this(width, height, (int[]) null, destination);

            this.gelesen = gelesen;
        }

        @Override
        public void lostOwnership(Clipboard clipboard, Transferable contents)
        {}

        @Override
        public void run()
        {
            if (this.data == null && this.gelesen != null)
            {
                this.data = new int[this.width * this.height];

                for (int y = 0; y < this.height; y++)
                {
                    this.gelesen.get(y * this.width, this.data, (this.height - 1 - y) * this.width, this.width);
                }
            }

            try
            {
                if (this.destination == null)
                {
                    /* Windows only */
                    BufferedImage image = new BufferedImage(this.width, this.height, BufferedImage.TYPE_INT_ARGB);

                    image.setRGB(0, 0, this.width, this.height, this.data, 0, this.width);
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new TransferableImage(image), this);
                }
                else
                {
                    Pixels pixels = Pixels.fromIntArray(this.width, this.height, this.data);

                    PNGEncoder.writeToFile(pixels, this.destination);

                    pixels.delete();
                }

                UIUtils.playClick();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }

    public static class TransferableImage implements Transferable
    {
        private Image image;
        private DataFlavor flavor = DataFlavor.imageFlavor;

        public TransferableImage(Image image)
        {
            this.image = image;
        }

        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException
        {
            if (this.flavor.equals(flavor))
            {
                return this.image;
            }

            throw new UnsupportedFlavorException(flavor);
        }

        public DataFlavor[] getTransferDataFlavors()
        {
            return new DataFlavor[]{this.flavor};
        }

        public boolean isDataFlavorSupported(DataFlavor flavor)
        {
            return this.flavor.equals(flavor);
        }
    }
}
