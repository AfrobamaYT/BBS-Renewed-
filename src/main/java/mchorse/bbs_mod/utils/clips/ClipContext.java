package mchorse.bbs_mod.utils.clips;

import mchorse.bbs_mod.camera.clips.misc.AudioClip;

import java.util.function.Predicate;

public abstract class ClipContext <T extends Clip, E>
{
    /**
     * Tick since the beginning of the camera profile.
     */
    public int ticks;

    /**
     * Tick relative to beginning of the camera clip.
     */
    public int relativeTick;

    /**
     * Transition between update ticks.
     */
    public float transition;

    /**
     * Current layer.
     */
    public int currentLayer;

    /**
     * Current clips
     */
    public Clips clips;

    /**
     * Whether currently camera is played or paused
     */
    public boolean playing = true;

    /**
     * How many camera clips were applied
     */
    /**
     * How many top-level clips were applied this frame.
     * Used for HUD overlay draw order (subtitle/image/boss bar/hotbar).
     *
     * MERGE 2026-07-27: aus dem CML-upstream — dessen HUD-clips (BossBar, Hotbar, Image)
     * stapeln sich anhand dieses zaehlers, damit sie sich nicht ueberdecken.
     */
    public int applied;

    /** Recursion depth while applying nested clips. See {@link #applied}. */
    public int applyDepth;

    public int count;

    /**
     * How many blocks did camera moved
     */
    public double distance;

    /**
     * Speed in between frames
     */
    public double velocity;

    public final ClipData clipData = new ClipData();

    public ClipContext setup(int ticks, float transition)
    {
        return this.setup(ticks, ticks, transition);
    }

    public ClipContext setup(int ticks, int relativeTick, float transition)
    {
        return this.setup(ticks, relativeTick, transition, 0);
    }

    public ClipContext setup(int ticks, int relativeTick, float transition, int currentLayer)
    {
        this.count = 0;
        this.ticks = ticks;
        this.relativeTick = relativeTick;
        this.transition = transition;
        this.currentLayer = currentLayer;

        return this;
    }

    public abstract boolean apply(Clip clip, E position);

    public boolean applyUnderneath(int ticks, float transition, E position)
    {
        return this.applyUnderneath(ticks, transition, position, AudioClip.NO_AUDIO);
    }

    /**
     * Apply clips underneath currently running
     */
    public boolean applyUnderneath(int ticks, float transition, E position, Predicate<Clip> filter)
    {
        if (this.currentLayer > 0)
        {
            int lastLayer = this.currentLayer;
            int lastTicks = this.ticks;
            int lastRelativeTicks = this.relativeTick;
            float lastTransition = this.transition;

            this.ticks = ticks;
            this.transition = transition;

            boolean applied = false;

            for (Clip clip : this.clips.getClips(ticks, lastLayer))
            {
                boolean allowed = filter == null || filter.test(clip);

                if (allowed && this.apply(clip, position))
                {
                    applied = true;
                }
            }

            this.currentLayer = lastLayer;
            this.ticks = lastTicks;
            this.relativeTick = lastRelativeTicks;
            this.transition = lastTransition;

            return applied;
        }

        return false;
    }
}