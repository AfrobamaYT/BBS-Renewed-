// Coded by Afrobama/Claude — BBS <-> DaVinci Resolve live link (task 109, docs/DAVINCI-LINK-PLAN.md).
package com.davincilink;

import com.mojang.logging.LogUtils;
import mchorse.bbs_mod.utils.FFMpegUtils;
import org.slf4j.Logger;

import java.io.DataInputStream;
import java.nio.ByteBuffer;
import java.util.function.Consumer;

/**
 * The private X server's picture: ffmpeg x11grab (XShm, measured 60 fps at about 3 % of a core
 * in its native bgr0, swapped to RGBA here on the grab thread -- BBS uploads RGBA as it is, and
 * rgb24 had cost 57 % of the render thread in BBS' own conversion, measured 2026-10-12 with
 * JFR) into two fixed buffers,
 * and where Resolve's viewer is ({@link DavinciViewerFinder}) every sixth frame.
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
        this.grab = new ProcessBuilder(FFMpegUtils.getFFMPEG(), "-v", "error", "-f", "x11grab",
            "-framerate", "60", "-video_size", width + "x" + height, "-i", display,
            "-f", "rawvideo", "-pix_fmt", "bgr0", "-").redirectError(ProcessBuilder.Redirect.DISCARD).start();

        Thread reader = new Thread(this::read, "davincilink-screen");

        reader.setDaemon(true);
        reader.start();
    }

    boolean running()
    {
        return this.grab.isAlive();
    }

    void stop()
    {
        this.grab.destroy();
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

        try (DataInputStream in = new DataInputStream(this.grab.getInputStream()))
        {
            byte[] bytes = new byte[size];
            /* The last frame as grabbed: a still Resolve still sends 60 frames a second, and swapping,
             * copying and uploading each cost BBS ~40 % of a core with nothing moving (2026-10-12). */
            byte[] last = new byte[size];
            boolean seen = false;

            synchronized (this.swap)
            {
                this.front = ByteBuffer.allocateDirect(size);
                this.back = ByteBuffer.allocateDirect(size);
            }

            while (this.grab.isAlive())
            {
                in.readFully(bytes);
                if (seen && java.util.Arrays.equals(bytes, last))
                {
                    continue;
                }
                System.arraycopy(bytes, 0, last, 0, size);
                seen = true;
                toRgba(bytes);

                synchronized (this.swap)
                {
                    this.back.clear();
                    this.back.put(bytes).flip();

                    ByteBuffer swapped = this.front;

                    this.front = this.back;
                    this.back = swapped;
                    this.fresh = true;
                }
                if (this.frames++ % 6 == 0)
                {
                    this.viewer = DavinciViewerFinder.find(bytes, this.width, this.height);
                }
            }
        }
        catch (Exception e)
        {
            if (this.grab.isAlive())
            {
                LOGGER.error("[davincilink] Resolve tab picture stopped", e);
            }
        }
    }
}
