package mchorse.bbs_mod.utils;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.ui.utils.UIUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.system.MemoryUtil;

import java.io.File;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class VideoRecorder
{
    private Process process;
    private WritableByteChannel channel;
    private boolean recording;

    private ByteBuffer buffer;
    private int textureId = -1;
    private int textureWidth;
    private int textureHeight;
    private int counter;

    public int serverTicks;
    public int lastServerTicks;

    public boolean isRecording()
    {
        return this.recording;
    }

    public int getTextureId()
    {
        return this.textureId;
    }

    public int getCounter()
    {
        return this.counter;
    }

    /**
     * Start recording the video using ffmpeg
     */
    public void startRecording(int textureId, int width, int height)
    {
        if (this.recording)
        {
            return;
        }

        this.counter = 0;
        this.textureId = textureId;
        this.textureWidth = width;
        this.textureHeight = height;

        if (this.buffer == null)
        {
            this.buffer = MemoryUtil.memAlloc(width * height * 3);
        }

        try
        {
            File movies = BBSRendering.getVideoFolder();
            File exportPath = new File(BBSSettings.videoSettings.path.get());

            if (exportPath.isDirectory())
            {
                movies = exportPath;
            }

            Path path = Paths.get(movies.toString());
            String movieName = StringUtils.createTimestampFilename();
            String params = BBSSettings.videoSettings.arguments.get();
            StringBuilder filters = new StringBuilder("vflip");
            float frameRate = (float) BBSRendering.getVideoFrameRate();

            int motionBlur = BBSRendering.getMotionBlur();

            for (int i = 0; i < motionBlur; i++)
            {
                filters.append(",tblend=all_mode=average,framestep=2");
            }

            params = params.replace("%WIDTH%", String.valueOf(width));
            params = params.replace("%HEIGHT%", String.valueOf(height));
            params = params.replace("%FPS%", String.valueOf(frameRate));
            params = params.replace("%NAME%", movieName);
            params = params.replace("%FILTERS%", filters.toString());

            List<String> args = new ArrayList<String>();

            args.add(BBSSettings.videoEncoderPath.get());
            args.addAll(Arrays.asList(params.split(" ")));

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

            OutputStream os = this.process.getOutputStream();

            this.channel = Channels.newChannel(os);
            this.recording = true;

            UIUtils.playClick(2F);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        this.serverTicks = this.lastServerTicks = 0;
    }

    /**
     * Stop recording
     */
    public void stopRecording()
    {
        if (!this.recording)
        {
            return;
        }

        this.textureId = -1;

        if (this.buffer != null)
        {
            MemoryUtil.memFree(this.buffer);

            this.buffer = null;
        }

        try
        {
            if (this.channel.isOpen())
            {
                this.channel.close();
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        try
        {
            this.process.waitFor(1, TimeUnit.MINUTES);
            this.process.destroy();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        this.recording = false;

        UIUtils.playClick(0.5F);

        this.serverTicks = this.lastServerTicks = 0;
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

        this.buffer.rewind();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.textureId);
        GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL12.GL_BGR, GL11.GL_UNSIGNED_BYTE, this.buffer);
        this.buffer.rewind();

        try
        {
            this.channel.write(this.buffer);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        this.counter += 1;
    }

    /**
     * Toggle recording of the video
     */
    public void toggleRecording(int textureId, int textureWidth, int textureHeight)
    {
        if (this.recording)
        {
            this.stopRecording();
        }
        else
        {
            this.startRecording(textureId, textureWidth, textureHeight);
        }

        UIUtils.playClick();
    }
}