package mchorse.bbs_mod.film.markers;

import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;

import java.util.UUID;

/** A recording cue owns time; its two helper replays own editable spatial transforms. */
public class TimerCue extends ValueGroup
{
    public final ValueString uuid = new ValueString("uuid", "");
    public final ValueString label = new ValueString("label", "Cue");
    public final ValueInt startTick = new ValueInt("start_tick", 0);
    public final ValueInt endTick = new ValueInt("end_tick", 40);
    public final ValueString startReplay = new ValueString("start_replay", "");
    public final ValueString endReplay = new ValueString("end_replay", "");
    public final ValueInt startColor = new ValueInt("start_color", 0xFF4EC1FF).colorAlpha();
    public final ValueInt endColor = new ValueInt("end_color", 0xFFFF854E).colorAlpha();
    public final ValueBoolean enabled = new ValueBoolean("enabled", true);

    public TimerCue(String id)
    {
        super(id);

        this.add(this.uuid);
        this.add(this.label);
        this.add(this.startTick);
        this.add(this.endTick);
        this.add(this.startReplay);
        this.add(this.endReplay);
        this.add(this.startColor);
        this.add(this.endColor);
        this.add(this.enabled);
        this.uuid.set(UUID.randomUUID().toString());
    }

    public String timingError()
    {
        return this.startTick.get() < 0 || this.endTick.get() <= this.startTick.get()
            ? "End must be after start" : "";
    }

    /** Derived from the native film/recorder tick, never from a separate elapsed-time clock. */
    public String countdown(int tick, boolean end)
    {
        String timingError = this.timingError();

        if (!timingError.isEmpty())
        {
            return "ERROR: " + timingError;
        }

        String phase;
        int remaining;

        if (tick < this.startTick.get())
        {
            phase = end ? "End in " : "Start in ";
            remaining = (end ? this.endTick.get() : this.startTick.get()) - tick;
        }
        else if (tick == this.startTick.get() && !end)
        {
            return this.label.get() + (end ? " END" : " START") + " | NOW";
        }
        else if (tick < this.endTick.get())
        {
            phase = "Remaining ";
            remaining = this.endTick.get() - tick;
        }
        else
        {
            return this.label.get() + (end ? " END" : " START")
                + (end && tick == this.endTick.get() ? " | NOW" : " | DONE");
        }

        return this.label.get() + (end ? " END" : " START") + " | " + phase
            + seconds(remaining);
    }

    public static String seconds(int ticks)
    {
        int fraction = (ticks % 20) * 5;

        return ticks / 20 + "." + (fraction < 10 ? "0" : "") + fraction + " s";
    }
}
