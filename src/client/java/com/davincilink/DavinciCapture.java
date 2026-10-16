// Coded by Afrobama/Claude — BBS <-> DaVinci Resolve live link (task 109, docs/DAVINCI-LINK-PLAN.md).
package com.davincilink;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

/**
 * Changed BGRA frames from XDamage/XShm or Windows Graphics Capture, in two fixed buffers.
 * Colour conversion and the viewer key belong to the GPU, never this reader thread.
 */
final class DavinciCapture
{
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Process grab;
    private final int width;
    private final int height;
    private final Object swap = new Object();
    /** Two fixed buffers: the reader fills one while the render thread uploads the other. */
    private ByteBuffer front;
    private ByteBuffer back;
    private boolean fresh;
    private volatile long frames;
    /** Viewer rectangle in X pixels {x, y, w, h}, or null while no placeholder is visible. */
    private volatile int[] viewer;

    DavinciCapture(String display, int width, int height) throws java.io.IOException
    {
        this.width = width;
        this.height = height;
        this.grab = null;
        DavinciFrameSource source = DavinciFrameSource.open(this, display, width, height);

        Thread reader = new Thread(this::read, "davincilink-screen");

        reader.setDaemon(true);
        source.reader = reader;
        reader.start();
    }

    boolean running()
    {
        DavinciFrameSource source = DavinciFrameSource.get(this);
        return source != null && source.active;
    }

    void stop()
    {
        DavinciFrameSource source = DavinciFrameSource.get(this);
        if (source != null)
        {
            source.stop();
            try
            {
                if (source.reader != null) source.reader.join(1500);
                if (source.reader != null && source.reader.isAlive())
                    throw new IllegalStateException("Resolve capture did not stop before closing its X server");
            }
            catch (InterruptedException error)
            {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while stopping Resolve capture", error);
            }
        }
        else if (this.grab != null) this.grab.destroy();
    }

    int width()
    {
        return this.width;
    }

    int height()
    {
        return this.height;
    }

    long frames()
    {
        return this.frames;
    }

    int[] viewer()
    {
        return this.viewer;
    }

    /** Hands the newest frame to {@code upload} if there is one the caller has not seen yet. */
    boolean upload(Consumer<ByteBuffer> upload)
    {
        synchronized (this.swap)
        {
            if (!this.fresh || this.front == null)
            {
                return false;
            }
            this.fresh = false;
            upload.accept(this.front.duplicate());

            return true;
        }
    }

    /**
     * x11grab's own layout is BGR with a dead fourth byte. Asking ffmpeg for rgba cost 67 % of a
     * core at 60 fps (bgr0: 17 %, measured 2026-10-12); swapping here on the grab thread is a
     * plain byte loop. Alpha must be 255 -- BBS draws the texture blended.
     */
    static void toRgba(byte[] pixels)
    {
        for (int i = 0; i < pixels.length; i += 4)
        {
            byte blue = pixels[i];

            pixels[i] = pixels[i + 2];
            pixels[i + 2] = blue;
            pixels[i + 3] = (byte) 255;
        }
    }

    private void read()
    {
        int size = this.width * this.height * 4;

        DavinciFrameSource source = DavinciFrameSource.get(this);
        try
        {
            byte[] bytes = new byte[size];

            synchronized (this.swap)
            {
                this.front = ByteBuffer.allocateDirect(size);
                this.back = ByteBuffer.allocateDirect(size);
            }

            while (source.next(bytes))
            {
                /* Find from the same raw frame before publishing it, without alpha mutations. */
                int[] found = DavinciViewerFinder.find(bytes, this.width, this.height);
                synchronized (this.swap)
                {
                    this.viewer = found;
                    this.back.clear();
                    this.back.put(bytes).flip();

                    ByteBuffer swapped = this.front;

                    this.front = this.back;
                    this.back = swapped;
                    this.fresh = true;
                }
                this.frames++;
            }
        }
        catch (Exception e)
        {
            if (source.active)
            {
                source.failure = e.toString();
                LOGGER.error("[davincilink] Resolve tab picture stopped", e);
            }
        }
        finally
        {
            source.close();
        }
    }
}
