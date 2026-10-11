package mchorse.bbs_mod.ui.film.audio;

import mchorse.bbs_mod.audio.Wave;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALC11;
import org.lwjgl.openal.ALUtil;
import org.lwjgl.system.MemoryUtil;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.Consumer;

/** A selected input is armed for metering; only an explicit begin stores a take. */
public class OpenALRecorder implements Runnable
{
    private final String deviceName;
    private final int sampleRate;
    private final ByteArrayOutputStream pcm = new ByteArrayOutputStream();
    private final float[] peaks = new float[256];
    private final float[] averages = new float[256];
    private final Consumer<Wave> legacyConsumer;
    private long device;
    private ByteBuffer buffer;
    private int waveformHead;
    private volatile boolean running = true;
    private volatile boolean ready;
    private volatile boolean recording;
    private volatile boolean done;
    private volatile boolean cancelled;
    private volatile long samples;
    private volatile float volume;
    private volatile Throwable failure;
    private volatile Wave result;

    public static List<String> devices()
    {
        List<String> names = ALUtil.getStringList(0L, ALC11.ALC_CAPTURE_DEVICE_SPECIFIER);

        return names == null ? List.of() : List.copyOf(names);
    }

    public OpenALRecorder(String deviceName, int sampleRate)
    {
        this(deviceName, sampleRate, null);
    }

    /** Retain the existing callback-based recorder API for callers outside the new panel. */
    public OpenALRecorder(Consumer<Wave> consumer)
    {
        this(null, 44100, consumer);
    }

    private OpenALRecorder(String deviceName, int sampleRate, Consumer<Wave> legacyConsumer)
    {
        if (sampleRate != 44100 && sampleRate != 48000)
        {
            throw new IllegalArgumentException("Unsupported voice-over sample rate: " + sampleRate);
        }

        this.deviceName = deviceName;
        this.sampleRate = sampleRate;
        this.legacyConsumer = legacyConsumer;
    }

    public boolean isReady() { return this.running && this.ready && !this.done && this.failure == null; }
    public boolean isRecording() { return this.recording && !this.done; }
    public boolean isDone() { return this.done; }
    public Throwable getFailure() { return this.failure; }
    public Wave getResult() { return this.result; }
    public float getVolume() { return this.volume; }
    public long getTime() { return this.samples * 1000L / this.sampleRate; }

    public synchronized float[] getWaveform(float[] out)
    {
        if (out == null || out.length != this.peaks.length)
        {
            out = new float[this.peaks.length];
        }

        for (int i = 0; i < out.length; i++)
        {
            out[i] = this.peaks[(this.waveformHead + i) % this.peaks.length];
        }

        return out;
    }

    public synchronized float[][] getWaveform(float[][] out)
    {
        if (out == null || out.length != 2 || out[0].length != this.peaks.length || out[1].length != this.peaks.length)
        {
            out = new float[][] {new float[this.peaks.length], new float[this.peaks.length]};
        }

        for (int i = 0; i < this.peaks.length; i++)
        {
            out[0][i] = this.peaks[(this.waveformHead + i) % this.peaks.length];
            out[1][i] = this.averages[(this.waveformHead + i) % this.peaks.length];
        }

        return out;
    }

    /** Restart capture before playback, excluding all input from preview/count-in. */
    public synchronized void begin()
    {
        if (!this.isReady() || this.recording)
        {
            throw new IllegalStateException("Microphone is not ready for a new take");
        }

        ALC11.alcCaptureStop(this.device);
        this.poll();
        this.pcm.reset();
        this.samples = 0L;
        ALC11.alcCaptureStart(this.device);
        this.checkError();
        this.recording = true;
    }

    public synchronized void stop()
    {
        if (this.ready)
        {
            ALC11.alcCaptureStop(this.device);
        }

        this.running = false;
    }

    public synchronized void cancel()
    {
        this.cancelled = true;
        this.stop();
    }

    private synchronized void init()
    {
        if (!this.running)
        {
            return;
        }

        this.device = ALC11.alcCaptureOpenDevice(this.deviceName, this.sampleRate,
            AL10.AL_FORMAT_MONO16, this.sampleRate);

        if (this.device == 0L)
        {
            throw new IllegalStateException("Cannot open microphone: " + this.deviceName);
        }

        this.buffer = MemoryUtil.memAlloc(this.sampleRate * 2);
        ALC11.alcCaptureStart(this.device);
        this.checkError();
        this.ready = true;
    }

    private void checkError()
    {
        int error = ALC10.alcGetError(this.device);

        if (error != ALC10.ALC_NO_ERROR)
        {
            throw new IllegalStateException("Microphone capture error: 0x" + Integer.toHexString(error));
        }
    }

    private synchronized void poll()
    {
        int available = ALC10.alcGetInteger(this.device, ALC11.ALC_CAPTURE_SAMPLES);

        if (available == 0)
        {
            this.checkError();

            return;
        }
        if (available < 0 || available > this.sampleRate)
        {
            throw new IllegalStateException("Microphone capture buffer overrun");
        }

        this.buffer.clear();
        ALC11.alcCaptureSamples(this.device, this.buffer, available);
        this.checkError();
        float peak = 0F;
        float sum = 0F;

        for (int i = 0; i < available; i++)
        {
            float amplitude = Math.abs(this.buffer.getShort(i * 2) / 32768F);

            peak = Math.max(peak, amplitude);
            sum += amplitude;
        }

        this.volume = peak;
        this.peaks[this.waveformHead] = peak;
        this.averages[this.waveformHead] = sum / available;
        this.waveformHead = (this.waveformHead + 1) % this.peaks.length;

        if (this.recording && !this.cancelled)
        {
            byte[] chunk = new byte[available * 2];

            this.buffer.get(0, chunk);
            this.pcm.writeBytes(chunk);
            this.samples += available;
        }
    }

    @Override
    public void run()
    {
        try
        {
            this.init();

            if (this.ready && this.legacyConsumer != null)
            {
                this.begin();
            }

            while (this.running)
            {
                this.poll();
                Thread.sleep(20L);
            }

            if (this.ready)
            {
                this.poll();
            }

            if (this.recording && !this.cancelled)
            {
                this.result = new Wave(1, 1, this.sampleRate, 16, this.pcm.toByteArray());

                if (this.legacyConsumer != null)
                {
                    this.legacyConsumer.accept(this.result);
                }
            }
        }
        catch (Exception error)
        {
            this.failure = error;
        }
        finally
        {
            synchronized (this)
            {
                if (this.device != 0L)
                {
                    ALC11.alcCaptureStop(this.device);
                    ALC11.alcCaptureCloseDevice(this.device);
                    this.device = 0L;
                }
                if (this.buffer != null)
                {
                    MemoryUtil.memFree(this.buffer);
                    this.buffer = null;
                }

                this.ready = false;
                this.done = true;
            }
        }
    }
}
