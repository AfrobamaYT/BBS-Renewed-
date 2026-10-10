package mchorse.bbs_mod.film.markers;

import mchorse.bbs_mod.settings.values.core.ValueList;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.HashSet;
import java.util.Set;

/** Separate from timeline markers: Blender's full-marker writes cannot erase cue metadata. */
public class TimerCues extends ValueList<TimerCue>
{
    public TimerCues(String id)
    {
        super(id);
    }

    public TimerCue addCue()
    {
        TimerCue cue = this.create(String.valueOf(this.list.size()));

        cue.startColor.set(this.nextColor());
        this.preNotify();
        this.add(cue);
        cue.endColor.set(this.nextColor());
        this.postNotify();

        return cue;
    }

    public void remove(TimerCue cue)
    {
        if (this.list.contains(cue))
        {
            this.preNotify();
            this.list.remove(cue);
            this.sync();
            this.postNotify();
        }
    }

    public TimerCue find(String uuid)
    {
        for (TimerCue cue : this.list)
        {
            if (cue.uuid.get().equals(uuid))
            {
                return cue;
            }
        }

        return null;
    }

    private int nextColor()
    {
        Set<Integer> used = new HashSet<>();

        for (TimerCue cue : this.list)
        {
            used.add(cue.startColor.get());
            used.add(cue.endColor.get());
        }

        for (int index = 0; index < 65536; index++)
        {
            float hue = (float) ((index * 0.6180339887498949D) % 1D);
            int color = Colors.HSVtoRGB(hue, 0.65F, 1F).getARGBColor();

            if (!used.contains(color))
            {
                return color;
            }
        }

        throw new IllegalStateException("No unused timer marker color available");
    }

    @Override
    protected TimerCue create(String id)
    {
        return new TimerCue(id);
    }
}
