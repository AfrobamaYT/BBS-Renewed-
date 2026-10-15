package mchorse.bbs_mod.integrations.vitrail;

/** Recording time in Vitrail's seconds, independent of GPU and wall-clock speed. */
public final class VitrailFilmClock
{
    public static final int FRAME_WRAP = 720720;
    public static final float COUNTER_WRAP = 3600F;

    private VitrailFilmClock() {}

    public record Step(int frame, float elapsed, float total) {}

    /**
     * The renderer frame counter also advances on held images so temporal jitter can settle.
     * Film time advances only on the game's actual recording tick; that same decision guards
     * the recorder's frame capture and includes held frames and the frame-rate limiter.
     */
    public static Step advance(boolean recording, boolean capture, float fps, int frame, float total)
    {
        if (!recording) return null;
        if (!Float.isFinite(fps) || fps <= 0F)
            throw new IllegalArgumentException("Vitrail recording frame rate must be finite and positive: " + fps);
        if (frame < 0 || frame >= FRAME_WRAP || !Float.isFinite(total) || total < 0F)
            throw new IllegalArgumentException("Invalid Vitrail frame clock state");

        float elapsed = capture ? 1F / fps : 0F;
        float next = total + elapsed;
        if (next >= COUNTER_WRAP) next = 0F;
        return new Step((frame + 1) % FRAME_WRAP, elapsed, next);
    }
}
