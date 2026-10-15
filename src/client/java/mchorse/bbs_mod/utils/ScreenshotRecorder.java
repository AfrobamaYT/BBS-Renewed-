package mchorse.bbs_mod.utils;

import com.mojang.blaze3d.textures.GpuTexture;
import mchorse.bbs_mod.graphics.GpuReadback;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.utils.resources.Pixels;
import org.lwjgl.system.MemoryUtil;
import net.minecraft.client.Minecraft;

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
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.concurrent.CompletableFuture;

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
        this.takeScreenshot(output, texture.gpu(), texture.width, texture.height);
    }

    /**
     * Take a screenshot from a texture and save it to designated file
     */
    public void takeScreenshot(File output, GpuTexture texture, int width, int height)
    {
        this.takeScreenshotAsync(output, texture, width, height);
    }

    /** Completes only after the captured image was actually saved or copied to the clipboard. */
    public CompletableFuture<File> takeScreenshotAsync(File output, GpuTexture texture, int width, int height)
    {
        int actualWidth = texture.getWidth(0);
        int actualHeight = texture.getHeight(0);

        if (actualWidth != width || actualHeight != height)
        {
            org.apache.logging.log4j.LogManager.getLogger("bbs/screenshot").warn(
                "Screenshot texture {} is {}x{}, requested {}x{}; reading actual size",
                texture.getLabel(), actualWidth, actualHeight, width, height);
        }

        CompletableFuture<File> completion = new CompletableFuture<>();
        GpuReadback.requestRaw(texture, new GpuReadback.Region(0, 0, actualWidth, actualHeight)).whenReady(
            raw -> new Thread(new ScreenshotRunner(raw, output, completion), "bbs-screenshot-writer").start(),
            completion::completeExceptionally);
        return completion;
    }

    /**
     * Take a screenshot from the screen and save it to designated file
     */
    public void takeScreenshot(File output, int width, int height)
    {
        GpuTexture texture = Minecraft.getInstance().gameRenderer.mainRenderTarget().getColorTexture();
        GpuReadback.requestRaw(texture, new GpuReadback.Region(0, 0, width, height)).whenReady(
            raw -> new Thread(new ScreenshotRunner(raw, output, null), "bbs-screenshot-writer").start());
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
        private GpuReadback.Raw raw;
        private CompletableFuture<File> completion;

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

        public ScreenshotRunner(GpuReadback.Raw raw, File destination, CompletableFuture<File> completion)
        {
            this(raw.width(), raw.height(), (int[]) null, destination);
            this.raw = raw;
            this.completion = completion;
        }

        @Override
        public void lostOwnership(Clipboard clipboard, Transferable contents)
        {}

        @Override
        public void run()
        {
            try
            {
                this.write();
                if (this.completion != null) this.completion.complete(this.destination);
            }
            catch (Throwable error)
            {
                error.printStackTrace();
                if (this.completion != null) this.completion.completeExceptionally(error);
            }
        }

        private void write() throws IOException
        {
            if (this.raw != null)
            {
                ByteBuffer rgba = null;

                try (GpuReadback.Raw owned = this.raw)
                {
                    rgba = owned.rgba8(GpuReadback.Origin.TOP_LEFT);
                    this.data = new int[this.width * this.height];

                    for (int i = 0; i < this.data.length; i++)
                    {
                        int index = i * 4;
                        this.data[i] = (rgba.get(index + 3) & 0xFF) << 24
                            | (rgba.get(index) & 0xFF) << 16 | (rgba.get(index + 1) & 0xFF) << 8
                            | (rgba.get(index + 2) & 0xFF);
                    }
                }
                finally
                {
                    if (rgba != null) MemoryUtil.memFree(rgba);
                    this.raw = null;
                }
            }
            if (this.data == null && this.gelesen != null)
            {
                this.data = new int[this.width * this.height];

                for (int y = 0; y < this.height; y++)
                {
                    this.gelesen.get(y * this.width, this.data, (this.height - 1 - y) * this.width, this.width);
                }
            }

            if (this.destination == null)
            {
                BufferedImage image = new BufferedImage(this.width, this.height, BufferedImage.TYPE_INT_ARGB);
                image.setRGB(0, 0, this.width, this.height, this.data, 0, this.width);
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new TransferableImage(image), this);
            }
            else
            {
                Pixels pixels = Pixels.fromIntArray(this.width, this.height, this.data);
                try { PNGEncoder.writeToFile(pixels, this.destination); }
                finally { pixels.delete(); }
            }

            UIUtils.playClick();
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
