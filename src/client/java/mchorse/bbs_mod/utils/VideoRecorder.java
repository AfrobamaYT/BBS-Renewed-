package mchorse.bbs_mod.utils;

import com.mojang.renderpearl.api.textures.GpuTexture;
import mchorse.bbs_mod.graphics.GpuReadback;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.utils.UIUtils;
import net.minecraft.client.Minecraft;
import org.lwjgl.system.MemoryUtil;
import sun.misc.Unsafe;

import java.io.File;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class VideoRecorder
{
    private static final Link RENDER_COMPLETE_SOUND = Link.assets("sounds/render_complete.ogg");

    private Process process;
    private WritableByteChannel channel;
    private boolean recording;

    private GpuTexture texture;
    private int textureWidth;
    private int textureHeight;
    private int counter;
    private int writtenFrames;
    private final Deque<GpuReadback.Request> pending = new ArrayDeque<>();

    public int serverTicks;
    public int lastServerTicks;

    public boolean isRecording()
    {
        return this.recording;
    }

    public GpuTexture getTexture()
    {
        return this.texture;
    }

    public int getCounter()
    {
        return this.counter;
    }

    /**
     * Start recording the video using ffmpeg
     */
    public void startRecording(String movieName, File audioFile, GpuTexture texture, int width, int height)
    {
        if (this.recording)
        {
            return;
        }

        this.counter = 0;
        if (texture == null || texture.isClosed() || texture.getWidth(0) != width || texture.getHeight(0) != height)
        {
            throw new IllegalArgumentException("Video capture requires its exact live GPU texture dimensions");
        }

        GpuReadback.requireRgba8Convertible(texture.getFormat());
        this.texture = texture;
        this.textureWidth = width;
        this.textureHeight = height;

        this.writtenFrames = 0;
        this.pending.clear();

        try
        {
            File movies = BBSRendering.getVideoFolder();

            movies.mkdirs();

            Path path = Paths.get(movies.toString());

            if (movieName == null || movieName.isEmpty())
            {
                movieName = StringUtils.createTimestampFilename();
            }

            String params = audioFile == null
                ? BBSSettings.videoArguments.get()
                : BBSSettings.videoArgumentsAudio.get();
            StringBuilder filters = new StringBuilder("vflip");
            float frameRate = (float) BBSRendering.getVideoFrameRate();

            /* P7a: Frames kommen supersampled herein — VOR dem zeitlichen tblend auf die
             * Zielgroesse (Lanczos), sonst mittelt der Blur auf N-facher Flaeche umsonst. */
            int supersampling = BBSSettings.videoSupersampling.get();

            if (supersampling > 1)
            {
                filters.append(",scale=").append(width / supersampling).append(":").append(height / supersampling).append(":flags=lanczos");
            }

            int motionBlur = BBSRendering.getMotionBlur();

            for (int i = 0; i < motionBlur; i++)
            {
                filters.append(",tblend=all_mode=average,framestep=2");
            }

            List<String> args = new ArrayList<>();
            String encoder = FFMpegUtils.getFFMPEG();

            args.add(encoder);

            /* Tokens are substituted after splitting, so a movie name or an audio path
             * with spaces stays a single argument. ProcessBuilder passes quote characters
             * literally, so they must not be added around paths either. */
            /* Liefert die GPU RGBA8 und erwartet die Eingabe bgr24 (Standard-Argumente), bekommt
             * ffmpeg die Rohdaten als rgba und wandelt selbst um. Die Java-Schleife in bgr8() kostete
             * bei 4K60 74 % der Exportzeit (JFR, 2026-10-14); eigene Argumente ohne bgr24 behalten
             * den alten Weg. */
            String[] teile = VideoAudioInput.prepare(params.split(" "), audioFile);

            boolean roh = false;

            if (this.texture.getFormat() == com.mojang.renderpearl.api.GpuFormat.RGBA8_UNORM)
            {
                for (int i = 0; i + 1 < teile.length; i++)
                {
                    if (teile[i].equals("-pix_fmt") && teile[i + 1].equals("bgr24"))
                    {
                        teile[i + 1] = "rgba";
                        roh = true;

                        break;
                    }
                }
            }

            VideoRohRgba.setzen(this, roh);

            if (roh)
            {
                VideoReadbackRing.start(this, texture, width, height);
            }
            else
            {
                VideoReadbackRing.close(this);
            }

            for (String arg : teile)
            {
                if (arg.isEmpty())
                {
                    continue;
                }

                arg = arg.replace("%WIDTH%", String.valueOf(width));
                arg = arg.replace("%HEIGHT%", String.valueOf(height));
                arg = arg.replace("%FPS%", String.valueOf(frameRate));
                arg = arg.replace("%NAME%", movieName);
                arg = arg.replace("%FILTERS%", filters.toString());

                if (audioFile != null)
                {
                    arg = arg.replace("%AUDIO_TRACK%", audioFile.getAbsolutePath());
                }

                args.add(arg);
            }

            System.out.println("Recording video with following arguments: " + args);

            ProcessBuilder builder = new ProcessBuilder(args);
            File log = path.resolve(movieName.concat(".log")).toFile();

            if (!BBSSettings.videoEncoderLog.get())
            {
                log = BBSMod.getSettingsPath("video.log");
            }

            builder.directory(path.toFile());
            builder.redirectErrorStream(true);
            builder.redirectOutput(log);

            this.process = builder.start();

            /**
             * Java wraps the process output stream into a BufferedOutputStream,
             *
             * but its little buffer is just slowing everything down with the
             * huge amount of data we're dealing here, so unwrap it with this little
             * hack.
             */
            OutputStream os = this.process.getOutputStream();
            Unsafe unsafe = UnsafeUtils.getUnsafe();

            if (os instanceof FilterOutputStream)
            {
                try
                {
                    Field outField = FilterOutputStream.class.getDeclaredField("out");

                    os = (OutputStream) unsafe.getObject(os, unsafe.objectFieldOffset(outField));
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }

            /* A FileChannel writes a direct buffer in one call; Channels.newChannel copied every
             * frame through an 8 KB heap array first. */
            this.channel = os instanceof java.io.FileOutputStream file ? file.getChannel() : Channels.newChannel(os);
            this.recording = true;
            /* A mid-frame start must wait for the recording timer to prepare its first image. */
            BBSRendering.canRender = false;

            UIUtils.playClick(2F);
        }
        catch (Exception e)
        {
            VideoReadbackRing.close(this);
            this.texture = null;
            throw new IllegalStateException("Could not start the video encoder", e);
        }

        this.serverTicks = this.lastServerTicks = 0;
    }

    /**
     * Stop recording
     */
    public void stopRecording()
    {
        this.stopRecording(true);
    }

    /**
     * Stop recording. With {@code finishEffects} false the completion sound and the
     * folder opening are skipped - the caller runs {@link #playFinishEffects()} itself
     * once the file is actually final (audio post pass).
     */
    public void stopRecording(boolean finishEffects)
    {
        if (!this.recording)
        {
            return;
        }

        RuntimeException failure = null;
        try
        {
            this.drain(true);
            if (this.writtenFrames != this.counter)
            {
                throw new IllegalStateException("Video frame drain incomplete: " + this.writtenFrames + "/" + this.counter);
            }
        }
        catch (RuntimeException error)
        {
            failure = error;
        }
        finally
        {
            while (!this.pending.isEmpty()) this.pending.removeFirst().close();
            VideoReadbackRing.close(this);
            this.texture = null;
        }

        try
        {
            if (this.channel != null && this.channel.isOpen())
            {
                this.channel.close();
            }

            this.channel = null;
        }
        catch (IOException ex)
        {
            failure = new IllegalStateException("Could not close the video encoder input", ex);
        }

        try
        {
            if (this.process != null)
            {
                if (!this.process.waitFor(1, TimeUnit.MINUTES))
                {
                    failure = new IllegalStateException("Video encoder did not finish within one minute");
                }
                else if (this.process.exitValue() != 0)
                {
                    failure = new IllegalStateException("Video encoder exited with code " + this.process.exitValue());
                }
                this.process.destroy();
            }

            this.process = null;
        }
        catch (InterruptedException ex)
        {
            Thread.currentThread().interrupt();
            failure = new IllegalStateException("Video finalization interrupted", ex);
        }

        this.recording = false;
        this.serverTicks = this.lastServerTicks = 0;

        if (failure != null) throw failure;

        if (finishEffects)
        {
            this.playFinishEffects();
        }

    }

    /**
     * The end-of-export feedback (completion sound, opening the movies folder).
     */
    public void playFinishEffects()
    {
        if (BBSSettings.videoPlaySoundAfterExport.get())
        {
            if (BBSModClient.getSounds().play(RENDER_COMPLETE_SOUND) == null)
            {
                UIUtils.playClick(0.5F);
            }
        }

        if (BBSSettings.videoOpenFolderAfterExport.get())
        {
            File folder = BBSRendering.getVideoFolder();
            Minecraft.getInstance().execute(() -> UIUtils.openFolder(folder));
        }
    }

    /**
     * Record a frame
     */
    public void recordFrame()
    {
        if (!this.recording)
        {
            return;
        }

        VideoReadbackRing ring = VideoReadbackRing.get(this);

        if (ring != null)
        {
            try
            {
                this.writtenFrames += ring.record(this.texture, this.textureWidth, this.textureHeight, this.channel);
            }
            catch (IOException error)
            {
                throw new IllegalStateException("Could not write video frame " + this.writtenFrames, error);
            }

            this.counter++;

            return;
        }

        this.drain(false);
        if (this.pending.size() >= 2) this.drain(true);
        this.pending.addLast(GpuReadback.requestRaw(this.texture,
            new GpuReadback.Region(0, 0, this.textureWidth, this.textureHeight)));
        this.counter++;
    }

    /**
     * Drain in issue order. Stop waits for every queued frame before closing ffmpeg.
     */
    private void drain(boolean wait)
    {
        VideoReadbackRing ring = VideoReadbackRing.get(this);

        if (ring != null)
        {
            try
            {
                this.writtenFrames += ring.drain(wait, this.channel);
            }
            catch (IOException error)
            {
                throw new IllegalStateException("Could not write video frame " + this.writtenFrames, error);
            }

            return;
        }

        while (!this.pending.isEmpty())
        {
            GpuReadback.Request request = this.pending.peekFirst();
            GpuReadback.Raw raw = wait ? request.await() : request.poll();
            if (raw == null) return;
            this.pending.removeFirst();
            ByteBuffer bgr = null;

            try (request; raw)
            {
                ByteBuffer daten = VideoRohRgba.an(this) ? raw.data() : (bgr = raw.bgr8(GpuReadback.Origin.NATIVE));
                while (daten.hasRemaining())
                {
                    int count = this.channel.write(daten);
                    if (count <= 0) throw new IOException("Video encoder input made no progress");
                }
                this.writtenFrames++;
            }
            catch (IOException error)
            {
                throw new IllegalStateException("Could not write video frame " + this.writtenFrames, error);
            }
            finally
            {
                if (bgr != null) MemoryUtil.memFree(bgr);
            }
        }
    }

    /**
     * Toggle recording of the video
     */
    public void toggleRecording(GpuTexture texture, int textureWidth, int textureHeight)
    {
        if (this.recording)
        {
            this.stopRecording();
        }
        else
        {
            this.startRecording(StringUtils.createTimestampFilename(), null, texture, textureWidth, textureHeight);
        }

        UIUtils.playClick();
    }
}
