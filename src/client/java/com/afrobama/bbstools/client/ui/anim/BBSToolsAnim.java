// Coded by Afrobama/Claude — UI Overhaul Phase 13b: smooth-transition animation helpers
package com.afrobama.bbstools.client.ui.anim;

/**
 * Tiny easing + tween utilities used across BBSTools UI to give panel
 * switches, hover states, and overlay opens a fluid feel instead of
 * instant snaps.
 *
 * <p>Two patterns supported:</p>
 * <ol>
 *   <li>Stateless eased-lerp via {@link #easeOut(float)} — caller tracks
 *       its own 0..1 progress per frame (delta * speed) and reads back
 *       the eased value to interpolate between two endpoints.</li>
 *   <li>Live tween via {@link Tween} — caller holds a Tween instance
 *       per animated value, calls {@link Tween#tick(float)} per frame
 *       with a target, and reads back the current value.</li>
 * </ol>
 *
 * <p>All functions are pure and side-effect-free — safe to call from
 *  the render thread without synchronization.</p>
 */
public final class BBSToolsAnim
{
    /** Per-second tween rate that "feels right" for chrome / overlay
     *  transitions — matches DaVinci's ~150 ms ease-out feel. */
    public static final float DEFAULT_SPEED_PER_SEC = 12F;

    /** Hover transitions can be a bit faster so they feel responsive. */
    public static final float HOVER_SPEED_PER_SEC = 18F;

    private BBSToolsAnim() {}

    /** Cubic ease-out — fast at start, gentle landing. Maps 0..1 to 0..1. */
    public static float easeOut(float t)
    {
        if (t <= 0F) return 0F;
        if (t >= 1F) return 1F;
        float inv = 1F - t;
        return 1F - inv * inv * inv;
    }

    /** Symmetric ease-in-out — useful for collapse / expand transitions. */
    public static float easeInOut(float t)
    {
        if (t <= 0F) return 0F;
        if (t >= 1F) return 1F;
        return t < 0.5F
            ? 4F * t * t * t
            : 1F - (float) Math.pow(-2F * t + 2F, 3F) / 2F;
    }

    /** Float lerp, no clamping. */
    public static float lerp(float a, float b, float t)
    {
        return a + (b - a) * t;
    }

    /** Linear-to-channel-lerp on packed ARGB ints. {@code t} should be
     *  in 0..1; values outside are clamped. */
    public static int lerpColor(int a, int b, float t)
    {
        if (t <= 0F) return a;
        if (t >= 1F) return b;
        int aA = (a >>> 24) & 0xFF;
        int aR = (a >> 16) & 0xFF;
        int aG = (a >> 8) & 0xFF;
        int aB = a & 0xFF;
        int bA = (b >>> 24) & 0xFF;
        int bR = (b >> 16) & 0xFF;
        int bG = (b >> 8) & 0xFF;
        int bB = b & 0xFF;
        int rA = Math.round(aA + (bA - aA) * t);
        int rR = Math.round(aR + (bR - aR) * t);
        int rG = Math.round(aG + (bG - aG) * t);
        int rB = Math.round(aB + (bB - aB) * t);
        return (rA << 24) | (rR << 16) | (rG << 8) | rB;
    }

    /** Stateful tween — exponential approach toward a target, frame-rate
     *  independent if you pass a real {@code dtSeconds}. */
    public static final class Tween
    {
        public float value;
        public float speed;

        public Tween(float initial)
        {
            this.value = initial;
            this.speed = DEFAULT_SPEED_PER_SEC;
        }

        public Tween(float initial, float speedPerSec)
        {
            this.value = initial;
            this.speed = speedPerSec;
        }

        /** Advance the tween toward {@code target}. Pass time delta in
         *  seconds since the last frame (Minecraft's {@code tickDelta}
         *  divided by 20 works fine; or just {@code 1/60F} as a default). */
        public void tick(float target, float dtSeconds)
        {
            float k = 1F - (float) Math.exp(-this.speed * Math.max(0F, dtSeconds));
            this.value = this.value + (target - this.value) * k;
        }

        /** Snap to target — skips animation. Use when an animation
         *  would be unhelpful (initial open, hard reset). */
        public void snap(float target)
        {
            this.value = target;
        }

        /** True if the tween is "close enough" to its target that we
         *  can stop redrawing for animation purposes. */
        public boolean settled(float target, float epsilon)
        {
            return Math.abs(this.value - target) < epsilon;
        }
    }

}
