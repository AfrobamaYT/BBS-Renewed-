package mchorse.bbs_mod.video;

import mchorse.bbs_mod.utils.FFMpegUtils;
import org.lwjgl.system.MemoryUtil;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Ein ffmpeg-Prozess je Video, auf eigenem Thread: liest Rohbilder (RGBA) fortlaufend und
 * veroeffentlicht das zuletzt fertige. Der Render-Thread wartet nie auf ffmpeg.
 *
 * Springt das Ziel zurueck oder weit nach vorn, startet ffmpeg neu mit -ss VOR -i: das ist
 * seit ffmpeg 2.1 bildgenau und schnell (sucht den Keyframe davor und dekodiert bis zum Ziel).
 */
public final class VideoDecoder implements AutoCloseable
{
    /** Weiter als das voraus wird neu gestartet statt durchgelesen (in Sekunden). */
    private static final double SKIP_LIMIT = 2D;

    public final VideoInfo info;
    public final int width;
    public final int height;

    private final File file;
    private final Thread thread;
    private final ByteBuffer[] buffers = new ByteBuffer[2];
    private final byte[] row;

    private volatile long wanted;
    private volatile long published = -1;
    private volatile int publishedSlot;
    private volatile boolean running = true;
    private volatile String error = "";
    private long uploaded = -1;

    private Process process;
    private InputStream stream;
    private long next;

    public VideoDecoder(File file, VideoInfo info, int maxSide)
    {
        this.file = file;
        this.info = info;

        float scale = Math.min(1F, maxSide / (float) Math.max(info.width, info.height));

        /* Gerade Masse: yuv-Quellen und manche Skalierer verweigern ungerade Breiten. */
        this.width = Math.max(2, Math.round(info.width * scale) & ~1);
        this.height = Math.max(2, Math.round(info.height * scale) & ~1);
        this.row = new byte[this.width * this.height * 4];
        this.buffers[0] = MemoryUtil.memAlloc(this.row.length);
        this.buffers[1] = MemoryUtil.memAlloc(this.row.length);
        this.thread = new Thread(this::run, "BBS video " + file.getName());
        this.thread.setDaemon(true);
        this.thread.start();
    }

    /** Vom Render-Thread: welches Bild jetzt zu sehen sein soll. */
    public void want(long frame)
    {
        this.wanted = Math.max(0, frame);
    }

    public long published()
    {
        return this.published;
    }

    /** Wartet hoechstens {@code ms}, bis genau dieses Bild fertig ist. */
    public synchronized boolean waitFor(long frame, long ms)
    {
        long ende = System.nanoTime() + ms * 1_000_000L;

        while (this.published != frame && this.running && this.error.isEmpty())
        {
            long rest = ende - System.nanoTime();

            if (rest <= 0)
            {
                return false;
            }

            try
            {
                this.wait(Math.max(1L, rest / 1_000_000L));
            }
            catch (InterruptedException e)
            {
                return false;
            }
        }

        return this.published == frame;
    }

    public String error()
    {
        return this.error;
    }

    /** Uebergibt das neueste Bild, wenn es noch nicht hochgeladen wurde. Nur Render-Thread. */
    public synchronized boolean upload(Consumer<ByteBuffer> upload)
    {
        long frame = this.published;

        if (frame < 0 || frame == this.uploaded)
        {
            return false;
        }

        ByteBuffer buffer = this.buffers[this.publishedSlot];

        buffer.rewind();
        upload.accept(buffer);
        this.uploaded = frame;

        return true;
    }

    private void run()
    {
        try
        {
            while (this.running)
            {
                long target = this.wanted;

                if (target == this.published)
                {
                    Thread.sleep(2);

                    continue;
                }

                if (this.process == null || target < this.next || target - this.next > SKIP_LIMIT * this.info.fps)
                {
                    this.restart(target);
                }

                /* Bilder vor dem Ziel lesen und verwerfen; das Zielbild in den freien Puffer. */
                while (this.running && this.next < target && this.read(null))
                {}

                int slot = 1 - this.publishedSlot;

                if (this.running && this.read(this.buffers[slot]))
                {
                    synchronized (this)
                    {
                        this.publishedSlot = slot;
                        this.published = this.next - 1;
                        this.notifyAll();
                    }
                }
                else if (this.running)
                {
                    /* Videoende: das letzte Bild bleibt stehen, bis ein anderes Ziel kommt. */
                    synchronized (this)
                    {
                        this.published = target;
                        this.notifyAll();
                    }
                }
            }
        }
        catch (Exception e)
        {
            this.error = e.getMessage() == null ? e.toString() : e.getMessage();
        }
        finally
        {
            this.stop();
        }
    }

    private boolean read(ByteBuffer into) throws Exception
    {
        int n = this.stream.readNBytes(this.row, 0, this.row.length);

        if (n < this.row.length)
        {
            return false;
        }

        if (into != null)
        {
            /* Unter derselben Sperre wie upload(): sonst schreibt der Decoder in den Puffer,
             * den der Render-Thread gerade zur GPU kopiert. */
            synchronized (this)
            {
                into.clear();
                into.put(this.row);
                into.flip();
            }
        }

        this.next += 1;

        return true;
    }

    private void restart(long frame) throws Exception
    {
        this.stop();

        String seconds = String.format(Locale.ROOT, "%.4f", frame / this.info.fps);

        this.process = new ProcessBuilder(FFMpegUtils.getFFMPEG(), "-hide_banner", "-loglevel", "error",
            "-ss", seconds, "-i", this.file.getAbsolutePath(),
            "-vf", "scale=" + this.width + ":" + this.height, "-an",
            "-f", "rawvideo", "-pix_fmt", "rgba", "-")
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start();
        this.stream = new BufferedInputStream(this.process.getInputStream(), this.row.length * 2);
        this.next = frame;
    }

    private void stop()
    {
        if (this.process != null)
        {
            this.process.destroyForcibly();
            this.process = null;
            this.stream = null;
        }
    }

    @Override
    public void close()
    {
        this.running = false;
        this.thread.interrupt();

        try
        {
            this.thread.join(500);
        }
        catch (InterruptedException ignored)
        {}

        this.stop();
        MemoryUtil.memFree(this.buffers[0]);
        MemoryUtil.memFree(this.buffers[1]);
    }
}
