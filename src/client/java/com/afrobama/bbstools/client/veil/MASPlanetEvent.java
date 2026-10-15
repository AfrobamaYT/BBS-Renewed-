// Coded by Afrobama/Claude — Planet event state.
// Source pattern: destroying-minecraft 1.0.0 / PlanetDestructionClient.java
//
// Drives the bbstools:planet/planet shader uniforms:
//   - planetFallTimer (0..1 over PLANET_FALL_DURATION_SEC) — planet
//     descends from y=20000 toward y=16000 as this ramps up
//   - flashTimer — bright lava-core flash on impact (peaks then fades)
//
// Unlike nuke/blackhole, planet doesn't have a user-positioned origin.
// It uses VeilCamera.CameraPosition + (20000, varies, 0) internally —
// the planet always appears 20000 blocks east of the player. So the
// "trigger" just starts the descent animation.
package com.afrobama.bbstools.client.veil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MASPlanetEvent
{
    private static final Logger LOG = LoggerFactory.getLogger("mas/planet");

    /** How long planetFallTimer ramps 0->1. */
    private static final float PLANET_FALL_DURATION_SEC = 15.0F;

    /** Flash timer fades 2->0 over this duration after impact. */
    private static final float FLASH_DURATION_SEC = 1.0F;

    /** Time after planetFallTimer hits 1.0 before auto-deactivating. */
    private static final float HOLD_AFTER_IMPACT_SEC = 8.0F;

    private static volatile long startTimeNanos = 0L;
    private static volatile boolean active = false;

    private MASPlanetEvent() {}

    public static void trigger()
    {
        startTimeNanos = System.nanoTime();
        active = true;
        LOG.info("Planet fall triggered — descent over {}s then {}s hold", PLANET_FALL_DURATION_SEC, HOLD_AFTER_IMPACT_SEC);
    }

    public static void deactivate()
    {
        active = false;
        LOG.info("Planet event deactivated");
    }

    public static boolean isActive()
    {
        if (!active) return false;
        float elapsed = elapsedSeconds();
        if (elapsed > PLANET_FALL_DURATION_SEC + HOLD_AFTER_IMPACT_SEC)
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

    /** 0..1 over PLANET_FALL_DURATION_SEC, clamps at 1. */
    public static float getPlanetFallTimer()
    {
        if (!active) return 0.0F;
        float t = elapsedSeconds() / PLANET_FALL_DURATION_SEC;
        return Math.max(0.0F, Math.min(1.0F, t));
    }

    /** Flash that peaks at impact (planetFallTimer == 1.0) and fades over 1s. */
    public static float getFlashTimer()
    {
        if (!active) return 0.0F;
        float elapsed = elapsedSeconds();
        float impactTime = PLANET_FALL_DURATION_SEC;
        if (elapsed < impactTime) return 0.0F;
        float afterImpact = elapsed - impactTime;
        if (afterImpact > FLASH_DURATION_SEC) return 0.0F;
        return 2.0F * (1.0F - afterImpact / FLASH_DURATION_SEC);
    }

}
