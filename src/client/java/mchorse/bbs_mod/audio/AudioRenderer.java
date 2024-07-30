package mchorse.bbs_mod.audio;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.audio.wav.WaveWriter;
import mchorse.bbs_mod.camera.clips.misc.AudioClip;
import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import org.lwjgl.system.MemoryUtil;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AudioRenderer
{
    public static void renderAll(Batcher2D batcher, List<AudioClip> clips, float tick, int x, int y, int w, int h, int sw, int sh)
    {
        if (!BBSSettings.audioWaveformVisible.get())
        {
            return;
        }

        for (AudioClip clip : clips)
        {
            SoundBuffer audio = BBSModClient.getSounds().get(clip.audio.get(), true);

            if (audio != null && audio.getWaveform() != null && clip.isInside((int) tick))
            {
                renderWaveform(batcher, audio, clip, tick, x, y, w, h, sw, sh);

                y += h + 8;
            }
        }
    }

    public static void renderWaveform(Batcher2D batcher, SoundBuffer audio, AudioClip clip, float tick, int x, int y, int w, int h, int sw, int sh)
    {
        final float brightness = 0.45F;
        int half = w / 2;

        /* Draw background */
        batcher.gradientVBox(x + 2, y + 2, x + w - 2, y + h, 0, Colors.A50);
        batcher.box(x + 1, y, x + 2, y + h, 0xaaffffff);
        batcher.box(x + w - 2, y, x + w - 1, y + h, 0xaaffffff);
        batcher.box(x, y + h - 1, x + w, y + h, 0xffffffff);

        batcher.clip(x + 2, y + 2, w - 4, h - 4, sw, sh);

        Waveform wave = audio.getWaveform();

        float duration = w / (float) wave.getPixelsPerSecond();
        float playback = TimeUtils.toSeconds(tick - clip.tick.get() + clip.offset.get());
        int offset = (int) (playback * wave.getPixelsPerSecond());
        int waveW = wave.getWidth();

        /* Draw the waveform */
        int runningOffset = waveW - offset;

        if (runningOffset > 0)
        {
            wave.render(batcher, Colors.WHITE, x + half, y, half, h, playback, playback + duration / 2);
        }

        /* Draw the passed waveform */
        if (offset > 0)
        {
            int color = Colors.COLOR.set(brightness, brightness, brightness, 1F).getARGBColor();

            wave.render(batcher, color, x, y, half, h, playback - duration / 2, playback);
        }

        batcher.unclip(sw, sh);

        batcher.box(x + half, y + 1, x + half + 1, y + h - 1, 0xff57f52a);

        FontRenderer fontRenderer = batcher.getFont();

        if (BBSSettings.audioWaveformFilename.get())
        {
            batcher.textCard(audio.getId().toString(), x + 8, y + h / 2 - 4, 0xffffff, 0x99000000);
        }

        if (BBSSettings.audioWaveformTime.get())
        {
            int milliseconds = (int) (tick % 20 == 0 ? 0 : tick % 20 * 5D);

            String tickLabel = tick + "t (" + (int) playback + "." + StringUtils.leftPad(String.valueOf(milliseconds), 2, "0") + "s)";

            batcher.textCard(tickLabel, x + w - 8 - fontRenderer.getWidth(tickLabel), y + h / 2 - 4, 0xffffff, 0x99000000);
        }
    }

    public static boolean renderAudio(File file, List<AudioClip> clips, int totalDuration)
    {
        float total = totalDuration / 20F;
        Map<AudioClip, Wave> map = new HashMap<>();

        for (AudioClip clip : clips)
        {
            try
            {
                map.put(clip, AudioReader.read(BBSMod.getProvider(), clip.audio.get()));
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }

        if (map.isEmpty())
        {
            return false;
        }

        int totalBytes = (int) (total * map.values().iterator().next().byteRate);
        byte[] bytes = new byte[totalBytes + totalBytes % 2];
        ByteBuffer buffer = MemoryUtil.memAlloc(2);

        for (AudioClip clip : clips)
        {
            try
            {
                Wave wave = map.get(clip);

                int offset = (int) (TimeUtils.toSeconds(clip.tick.get()) * wave.byteRate);
                int length = (int) (TimeUtils.toSeconds(clip.duration.get()) * wave.byteRate);
                int start = (int) (TimeUtils.toSeconds(clip.offset.get()) * wave.byteRate);

                offset -= offset % 2;
                start -= start % 2;

                length = Math.min(wave.data.length, MathUtils.clamp(length, 0, bytes.length - offset));
                length -= length % 2;

                for (int i = 0; i < length; i += 2)
                {
                    buffer.position(0);
                    buffer.put(wave.data[start + i]);
                    buffer.put(wave.data[start + i + 1]);

                    int waveShort = buffer.getShort(0);

                    buffer.position(0);
                    buffer.put(bytes[offset + i]);
                    buffer.put(bytes[offset + i + 1]);

                    int bytesShort = buffer.getShort(0);
                    int finalShort = waveShort + bytesShort;

                    buffer.putShort(0, (short) MathUtils.clamp(finalShort, Short.MIN_VALUE, Short.MAX_VALUE));

                    bytes[offset + i + 1] = buffer.get(1);
                    bytes[offset + i] =     buffer.get(0);
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }

        try
        {
            Wave lastWave = map.values().iterator().next();
            Wave wave = new Wave(lastWave.audioFormat, lastWave.numChannels, lastWave.sampleRate, lastWave.bitsPerSample, bytes);

            WaveWriter.write(file, wave);

            return true;
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        return false;
    }
}