package mchorse.bbs_mod.ui.film.audio;

import com.mcstudio.nano.NanoDesign;
import com.mcstudio.nano.NanoFlaeche;
import com.mcstudio.nano.NanoStifte;
import mchorse.bbs_mod.ui.film.UIFilmPanel;

/** Uses the existing transport layout and NanoVG icon proportions. */
public final class VoiceoverTransport
{
    private VoiceoverTransport() {}

    public static float draw(long vg, NanoFlaeche surface, float right, float y, float width,
        float height, float scale, UIFilmPanel panel)
    {
        float x = right - width - 4F * scale;
        float cx = x + width / 2F;
        float cy = y + height / 2F;

        if (surface.istHover("tr-voiceover"))
        {
            NanoStifte.widget(vg, x, y, width, height, 4F * scale, NanoDesign.WIDGET, scale);
        }

        NanoStifte.rahmen(vg, cx - 2F * scale, cy - 6F * scale, 4F * scale, 8F * scale,
            2F * scale, NanoDesign.TXT);
        NanoStifte.linie(vg, cx - 4F * scale, cy - scale, cx - 4F * scale, cy + 3F * scale, scale, NanoDesign.TXT);
        NanoStifte.linie(vg, cx + 4F * scale, cy - scale, cx + 4F * scale, cy + 3F * scale, scale, NanoDesign.TXT);
        NanoStifte.linie(vg, cx - 4F * scale, cy + 3F * scale, cx + 4F * scale, cy + 3F * scale, scale, NanoDesign.TXT);
        NanoStifte.linie(vg, cx, cy + 3F * scale, cx, cy + 6F * scale, scale, NanoDesign.TXT);
        NanoStifte.linie(vg, cx - 3F * scale, cy + 6F * scale, cx + 3F * scale, cy + 6F * scale, scale, NanoDesign.TXT);
        surface.zone("tr-voiceover", x, y, width, height, () -> UIAudioRecorder.open(panel),
            "Voice-over: choose microphone and record from playhead", "");

        return x;
    }
}
