package mchorse.bbs_mod.audio;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Colors;

public class AudioRenderer
{
    public static void renderAll(Batcher2D batcher, int x, int y, int w, int h, int sw, int sh)
    {
        if (!BBSSettings.audioWaveformVisible.get())
        {
            return;
        }

        for (SoundPlayer file : BBSModClient.getSounds().getPlayers())
        {
            if (file.getBuffer().getWaveform() != null && !file.isStopped())
            {
                renderWaveform(batcher, file, x, y, w, h, sw, sh);

                y += h + 8;
            }
        }
    }

    public static void renderWaveform(Batcher2D batcher, SoundPlayer file, int x, int y, int w, int h, int sw, int sh)
    {
        if (file == null || file.getBuffer().getWaveform() == null)
        {
            return;
        }

        final float brightness = 0.45F;
        int half = w / 2;

        /* Draw background */
        batcher.gradientVBox(x + 2, y + 2, x + w - 2, y + h, 0, Colors.A50);
        batcher.box(x + 1, y, x + 2, y + h, 0xaaffffff);
        batcher.box(x + w - 2, y, x + w - 1, y + h, 0xaaffffff);
        batcher.box(x, y + h - 1, x + w, y + h, 0xffffffff);

        batcher.clip(x + 2, y + 2, w - 4, h - 4, sw, sh);

        Waveform wave = file.getBuffer().getWaveform();

        float duration = w / (float) wave.getPixelsPerSecond();
        float playback = file.getPlaybackPosition();
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
            batcher.textCard(file.getBuffer().getId().toString(), x + 8, y + h / 2 - 4, 0xffffff, 0x99000000);
        }

        if (BBSSettings.audioWaveformTime.get())
        {
            int tick = (int) Math.floor(playback * 20);
            int seconds = tick / 20;
            int milliseconds = (int) (tick % 20 == 0 ? 0 : tick % 20 * 5D);

            String tickLabel = tick + "t (" + seconds + "." + StringUtils.leftPad(String.valueOf(milliseconds), 2, "0") + "s)";

            batcher.textCard(tickLabel, x + w - 8 - fontRenderer.getWidth(tickLabel), y + h / 2 - 4, 0xffffff, 0x99000000);
        }
    }
}