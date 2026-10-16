package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.actions.types.ActionClip;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.Envelope;
import mchorse.bbs_mod.utils.colors.Colors;
import org.joml.Vector2f;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.List;

/**
 * Fade handles on a clip: two small squares in its top strip, at the end of the fade in and the
 * start of the fade out (in the corners while there is none). Dragging one sets that fade on every
 * grabbed clip, snapped like an edge; the clip's own bounds stay put. This replaces Shift+edge
 * dragging. From BBS FS e24692991 (Wemppy4/bbs-fs, MIT); grab modes 3 and 4 are the two handles.
 */
final class ClipFadeGriffe
{
    private static final int SIZE = 3;
    private static final int REACH = 5;
    private static final int MIN_WIDTH = 16;

    /** Fade in and out of each grabbed clip at grab time, zero for a clip whose envelope is off. */
    private final List<Vector2f> gegriffen = new ArrayList<>();

    void merken(List<Clip> clips)
    {
        this.gegriffen.clear();

        for (Clip clip : clips)
        {
            boolean an = clip.envelope.enabled.get();

            this.gegriffen.add(new Vector2f(an ? clip.envelope.fadeIn.get() : 0F, an ? clip.envelope.fadeOut.get() : 0F));
        }
    }

    /** 3 is the fade in handle, 4 the fade out one, 0 neither. */
    static int griff(UIClips clips, Clip clip, int mouseX, int mouseY)
    {
        int top = clips.toLayerY(clip.layer.get());

        if (!hat(clips, clip) || mouseY >= top + Math.min(clips.getLayerHeight() / 2, SIZE + 4)) return 0;

        int in = Math.abs(mouseX - x(clips, clip, true));
        int out = Math.abs(mouseX - x(clips, clip, false));

        if (Math.min(in, out) > REACH) return 0;

        /* Handles that meet go apart by the side of the clip the mouse is on */
        int middle = clips.toGraphX(clip.tick.get() + clip.duration.get() / 2);

        return in < out || (in == out && mouseX < middle) ? 3 : 4;
    }

    /** Where the fade ends (in) or begins (out), always fully inside the clip. */
    private static int x(UIClips clips, Clip clip, boolean fadeIn)
    {
        Envelope envelope = clip.envelope;
        float fade = envelope.enabled.get() ? (fadeIn ? envelope.fadeIn.get() : envelope.fadeOut.get()) : 0F;
        int tick = clip.tick.get();
        int end = tick + clip.duration.get();
        int half = SIZE / 2 + 1;

        return MathUtils.clamp(fadeIn ? clips.toGraphX(Math.round(tick + fade)) : clips.toGraphX(Math.round(end - fade)), clips.toGraphX(tick) + half, clips.toGraphX(end) - half - 1);
    }

    /** Only where the envelope does something, isn't keyframed, and the clip holds both handles. */
    static boolean hat(UIClips clips, Clip clip)
    {
        int tick = clip.tick.get();

        return clips.toGraphX(tick + clip.duration.get()) - clips.toGraphX(tick) >= MIN_WIDTH && !(clip instanceof ActionClip) && !clip.envelope.keyframes.get();
    }

    static void zeichnen(UIContext context, UIClips clips, Clip clip, boolean fadeIn, boolean aktiv)
    {
        int x = x(clips, clip, fadeIn) - SIZE / 2;
        /* The clip box is inset by a pixel from its row, the handle sits just inside it */
        int y = clips.toLayerY(clip.layer.get()) + 2;

        context.batcher.box(x, y, x + SIZE, y + SIZE, aktiv ? BBSSettings.primaryColor(Colors.A100) : Colors.WHITE);
    }

    /** The handle of the clip under the mouse snaps; every grabbed clip gets the same change. */
    void ziehen(UIClips clips, List<Clip> geklammert, List<Vector3i> daten, boolean fadeIn, int dx)
    {
        Vector3i vorne = daten.get(daten.size() - 1);
        Vector2f fades = this.gegriffen.get(this.gegriffen.size() - 1);
        int griff = fadeIn ? vorne.x() + (int) fades.x : vorne.x() + vorne.z() - (int) fades.y;
        int delta = clips.snapEdge(griff + dx) - griff;

        for (int i = 0; i < geklammert.size(); i++)
        {
            Clip clip = geklammert.get(i);
            Vector2f f = this.gegriffen.get(i);
            int duration = daten.get(i).z();

            if (!hat(clips, clip)) continue;

            clip.envelope.enabled.set(true);
            clip.envelope.fadeIn.set(fadeIn ? Math.max(0F, Math.min(f.x + delta, duration - f.y)) : f.x);
            clip.envelope.fadeOut.set(fadeIn ? f.y : Math.max(0F, Math.min(f.y - delta, duration - f.x)));
        }
    }
}
