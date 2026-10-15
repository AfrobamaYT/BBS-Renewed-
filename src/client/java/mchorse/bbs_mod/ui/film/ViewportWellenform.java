package mchorse.bbs_mod.ui.film;

import java.util.List;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.audio.AudioRenderer;
import mchorse.bbs_mod.camera.clips.misc.AudioClip;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.camera.controller.RunnerCameraController;

/**
 * Die Audio-Wellenform ueber dem Kamerabild.
 *
 * <p>⛔ OHNE AUDIO-CLIP WIRD NICHTS GEZEICHNET. Die Pruefung fragte frueher nur nach
 * Replay-Zweig und Einstellung, nie nach Inhalt — mit leerer Liste malte der
 * {@link AudioRenderer} den leeren Kasten samt gruener Abspielmarke oben ins Bild
 * (2026-09-09 gemessen: x=329, y=34, 411 breit; die Einstellung ist DEFAULT TRUE).</p>
 */
public final class ViewportWellenform
{
    private ViewportWellenform()
    {}

    public static void zeichnen(UIFilmPanel panel, List<AudioClip> clips, UIContext context, Area area)
    {
        if (!panel.replayEditor.isVisible() || !BBSSettings.audioWaveformVisibleInPreview.get())
        {
            return;
        }

        clips.clear();

        for (Clip clip : panel.getData().camera.get())
        {
            if (clip instanceof AudioClip audio)
            {
                clips.add(audio);
            }
        }

        if (clips.isEmpty())
        {
            return;
        }

        RunnerCameraController runner = panel.getRunner();
        int w = (int) (area.w * BBSSettings.audioWaveformWidth.get());
        int x = area.x(0.5F, w);
        int h = BBSSettings.audioWaveformHeight.get();
        float tick = panel.getCursor() + (runner.isRunning() ? context.getTransition() : 0);

        if (BBSSettings.audioWaveformPreviewCombined.get())
        {
            AudioRenderer.renderPreviewCombined(context.batcher, clips, tick, x, area.y + 10, w, h, context.menu.width, context.menu.height);
        }
        else
        {
            AudioRenderer.renderAll(context.batcher, clips, tick, x, area.y + 10, w, h, context.menu.width, context.menu.height);
        }
    }
}
