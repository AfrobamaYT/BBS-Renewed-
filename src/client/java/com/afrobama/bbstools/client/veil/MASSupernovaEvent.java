// Coded by Afrobama/Claude — Supernova event state.
// Source pattern: destroying-minecraft 1.0.0 / SupernovaDestructionClient.java
//
// Three animation phases via three timer uniforms on the sky shader:
//   - supernovaTimer (0..1 over 3s)   — sky transitions to dark + stars
//   - flashTimer (peaks at 2.0, fades 1s) — bright white flash + ray-march
//                                            of supernova cone/cylinder SDFs
//   - explosionTimer (0..1 over 5s)   — explosion shockwave radius
//
// Total event duration: ~10s of supernova spectacle.
package com.afrobama.bbstools.client.veil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MASSupernovaEvent
{
    private static final Logger LOG = LoggerFactory.getLogger("mas/supernova");

    private static final float TOTAL_DURATION_SEC = 10.0F;
    private static final float SKY_FADE_DURATION_SEC = 3.0F;
    private static final float EXPLOSION_DURATION_SEC = 5.0F;
    private static final float FLASH_DURATION_SEC = 1.0F;
    private static final float FLASH_DELAY_SEC = 0.5F;

    private static volatile long startTimeNanos = 0L;
    private static volatile boolean active = false;

    private MASSupernovaEvent() {}

    public static void trigger()
    {
        startTimeNanos = System.nanoTime();
        active = true;
        LOG.info("Supernova triggered — 3s sky fade + 5s explosion + 1s flash");
    }

    public static void deactivate()
    {
        active = false;
        LOG.info("Supernova deactivated");
    }

    public static boolean isActive()
    {
        if (!active) return false;
        if (elapsedSeconds() > TOTAL_DURATION_SEC)
        {
            active = false;
            return false;
        }
        return true;
    }

    public static float elapsedSeconds()
    {
        return (System.nanoTime() - startTimeNanos) / 1_000_000_000F;
    }

    /** Sky transition to dark+stars: 0..1 over SKY_FADE_DURATION_SEC. */
    public static float getSupernovaTimer()
    {
        if (!active) return 0.0F;
        float t = elapsedSeconds() / SKY_FADE_DURATION_SEC;
        return Math.max(0.0F, Math.min(1.0F, t));
    }

    /** Flash brightness: peaks at 2.0 then fades to 0 over FLASH_DURATION_SEC. */
    public static float getFlashTimer()
    {
        if (!active) return 0.0F;
        float e = elapsedSeconds();
        if (e < FLASH_DELAY_SEC) return 0.0F;
        float since = e - FLASH_DELAY_SEC;
        if (since > FLASH_DURATION_SEC) return 0.0F;
        return 2.0F * (1.0F - since / FLASH_DURATION_SEC);
    }

    /** Explosion shockwave radius: 0..1 over EXPLOSION_DURATION_SEC. */
    public static float getExplosionTimer()
    {
        if (!active) return 0.0F;
        float t = elapsedSeconds() / EXPLOSION_DURATION_SEC;
        return Math.max(0.0F, Math.min(1.0F, t));
    }

}
