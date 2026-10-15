package com.afrobama.bbstools.client.fracture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cinematic camera shake — adds organic pitch/yaw/roll perturbations
 * around the current camera orientation when a fracture body hits with
 * high impact speed. Multiple impacts stack additively but each decays
 * exponentially, so a sustained crash produces continuous shake while
 * a one-off bump produces a quick rattle.
 *
 * <h2>Math</h2>
 * <p>Each shake event has an initial amplitude (degrees of pitch/yaw
 * perturbation). Per render frame the amplitude decays by
 * {@link #DECAY_PER_FRAME} (≈ 12% / frame). The instantaneous shake is
 * the amplitude × a noise value (sin-of-time at three different
 * frequencies summed, gives organic non-periodic jitter). Magnitude
 * caps at {@link #MAX_AMPLITUDE_DEG} so a wall of impacts can't lock
 * the camera.</p>
 *
 * <h2>Application</h2>
 * <p>{@code CameraShakeMixin} reads {@link #currentPitchDelta()} and
 * {@link #currentYawDelta()} after the camera computes its base
 * rotation, and adds them via {@code Camera.setRotation}. We don't
 * mutate the player's actual yaw/pitch — only the rendering camera's
 * — so gameplay isn't affected and recordings of player look-rotation
 * stay clean.</p>
 *
 * <h2>Trigger threshold</h2>
 * <p>Only impacts above {@link #IMPACT_SHAKE_THRESHOLD} blocks/tick
 * spawn a shake. Below that the impact is gentle (a rolling tumble)
 * and shaking the camera would be obnoxious.</p>
 */
public final class CameraShakeManager
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/fracture/shake");

    /** Above this impact speed (blocks/tick) a shake fires. */
    public static final float IMPACT_SHAKE_THRESHOLD = 0.35F;

    /** Hard cap on combined amplitude (degrees) so cascading impacts
     *  don't lock the camera in a violent loop. 6° peak-to-peak is
     *  about right for a heavy crash without losing sight of the
     *  subject. */
    private static final float MAX_AMPLITUDE_DEG = 6F;

    /** Per-frame multiplicative decay. 0.88 = ~88% retained per frame
     *  → 50% gone after 6 frames (100ms at 60fps), 90% gone after 18
     *  frames (300ms). Quick enough to not feel sluggish, slow enough
     *  to register as a real shake. */
    private static final float DECAY_PER_FRAME = 0.88F;

    /** Below this amplitude (degrees) we treat the shake as zero — no
     *  point applying a perturbation that's smaller than 1px on screen. */
    private static final float CUTOFF_AMPLITUDE = 0.02F;

    /** Current accumulated shake amplitude (0..MAX_AMPLITUDE_DEG). */
    private static volatile float amplitudeDeg;

    /** Time accumulator for the noise function — advances every frame.
     *  Multiplied by different frequencies for pitch/yaw/roll so the
     *  three axes don't move in lockstep. */
    private static volatile float noiseTime;

    private CameraShakeManager()
    {
    }

    /**
     * Add a shake event. {@code intensity} is typically the impact
     * speed (blocks/tick) of a fracture body landing. Higher = bigger
     * shake. Shake stacks on existing amplitude but is capped.
     */
    public static void addShake(float intensity)
    {
        if (intensity <= 0F) return;

        /* Map impact speed → degrees: 0.35 → 1°, 1.0 → 3°, 2.0 → 5°.
         *  Chosen empirically to feel "heavy" without being motion-
         *  sickness-inducing. */
        float deg = intensity * 2.5F;

        amplitudeDeg += deg;
        if (amplitudeDeg > MAX_AMPLITUDE_DEG) amplitudeDeg = MAX_AMPLITUDE_DEG;

        if (LOG.isDebugEnabled())
        {
            LOG.debug("[shake] +{} deg (intensity {}) → total {}", deg, intensity, amplitudeDeg);
        }
    }

    /**
     * Per-render-frame update. Decays amplitude and advances the
     * noise time. Called from the camera mixin BEFORE
     * {@link #currentPitchDelta()}/{@link #currentYawDelta()} so the
     * latest delta reflects the just-decayed amplitude.
     */
    public static void tickFrame()
    {
        if (amplitudeDeg <= CUTOFF_AMPLITUDE)
        {
            amplitudeDeg = 0F;
            return;
        }

        amplitudeDeg *= DECAY_PER_FRAME;
        noiseTime += 0.6F;
    }

    public static float currentPitchDelta()
    {
        if (amplitudeDeg <= CUTOFF_AMPLITUDE) return 0F;
        /* Layered sin at 3 frequencies → smooth pseudo-random. */
        float n = (float) (Math.sin(noiseTime * 1.7D)
            + 0.6D * Math.sin(noiseTime * 3.1D)
            + 0.4D * Math.sin(noiseTime * 5.3D));
        n /= 2F; /* normalise rough range to ~[-1, +1] */
        return n * amplitudeDeg;
    }

    public static float currentYawDelta()
    {
        if (amplitudeDeg <= CUTOFF_AMPLITUDE) return 0F;
        /* Use different frequencies than pitch so they're decorrelated. */
        float n = (float) (Math.sin(noiseTime * 2.3D + 1.0D)
            + 0.6D * Math.sin(noiseTime * 4.1D + 0.4D)
            + 0.4D * Math.sin(noiseTime * 6.7D + 1.3D));
        n /= 2F;
        return n * amplitudeDeg;
    }

    /** True if any shake is currently active (saves the mixin doing
     *  trig math when there's nothing to apply). */
    public static boolean isActive()
    {
        return amplitudeDeg > CUTOFF_AMPLITUDE;
    }

    /** Force-clear; called on world unload. */
    public static void clear()
    {
        amplitudeDeg = 0F;
        noiseTime = 0F;
    }

}
