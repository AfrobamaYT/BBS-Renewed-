// Coded by Afrobama/Claude — Nuke event state.
// Source pattern: destroying-minecraft 1.0.0 / NukeDestructionClient.java
// Holds per-event state for an active nuke: world position + start time.
// Used by MASShaderUniformPusher to push NUKE_POS + smokeRiseTimer +
// flashTimer + GameTime uniforms to the nuke shader each frame.
//
// Lifecycle: /mas nuke at <pos> → trigger(pos) sets active+startTime.
// smokeRiseTimer ramps 0→1 over 5 seconds (mushroom cloud expansion).
// flashTimer ramps 2→0 over 1 second (white flash on detonation).
// Event auto-deactivates after 30 seconds (configurable).
//
// To trigger: MASNukeEvent.triggerAtPlayerTarget().
// To check state: MASNukeEvent.isActive(), getPosition(), getSmokeRiseTimer().
package com.afrobama.bbstools.client.veil;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MASNukeEvent
{
    private static final Logger LOG = LoggerFactory.getLogger("mas/nuke");

    /** How long the nuke event stays active before auto-deactivating. */
    private static final float TOTAL_DURATION_SEC = 30.0F;

    /** Time over which the mushroom cloud expands from 0 to full size. */
    private static final float EXPAND_DURATION_SEC = 5.0F;

    /** Time over which the detonation flash fades from peak (2.0) to 0. */
    private static final float FLASH_DURATION_SEC = 1.0F;

    private static volatile Vec3 position = Vec3.ZERO;
    private static volatile long startTimeNanos = 0L;
    private static volatile boolean active = false;

    private MASNukeEvent() {}

    /** Trigger a nuke at the given world position. Activates state +
     *  resets timers. Pipeline must be enabled separately
     *  (MASPostFXFeature.enable on "nuke"). */
    public static void trigger(Vec3 pos)
    {
        position = pos;
        startTimeNanos = System.nanoTime();
        active = true;
        LOG.info("Nuke triggered at ({}, {}, {})", pos.x, pos.y, pos.z);
    }

    /** Trigger at the player's horizontal look-target, 30 blocks ahead
     *  at the player's feet level. Returns the target position or null
     *  if no player. */
    public static Vec3 triggerAtPlayerTarget()
    {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return null;
        Vec3 look = mc.player.getLookAngle();
        Vec3 horiz = new Vec3(look.x, 0.0, look.z).normalize();
        Vec3 target = new Vec3(
            mc.player.getX() + horiz.x * 30.0,
            mc.player.getY(),
            mc.player.getZ() + horiz.z * 30.0);
        trigger(target);
        return target;
    }

    public static void deactivate()
    {
        active = false;
        LOG.info("Nuke deactivated");
    }

    public static boolean isActive()
    {
        if (!active) return false;
        /* Auto-deactivate after TOTAL_DURATION_SEC. */
        if (elapsedSeconds() > TOTAL_DURATION_SEC)
        {
            active = false;
            return false;
        }
        return true;
    }

    public static Vec3 getPosition() { return position; }

    /** Seconds since trigger. */
    public static float elapsedSeconds()
    {
        return (System.nanoTime() - startTimeNanos) / 1_000_000_000F;
    }

    /** Mushroom cloud expansion timer — clamped 0..1 over EXPAND_DURATION. */
    public static float getSmokeRiseTimer()
    {
        if (!active) return 0.0F;
        float t = elapsedSeconds() / EXPAND_DURATION_SEC;
        return Math.max(0.0F, Math.min(1.0F, t));
    }

    /** Detonation flash timer — peaks at 2.0, fades to 0 over FLASH_DURATION. */
    public static float getFlashTimer()
    {
        if (!active) return 0.0F;
        float e = elapsedSeconds();
        if (e > FLASH_DURATION_SEC) return 0.0F;
        return 2.0F * (1.0F - e / FLASH_DURATION_SEC);
    }

}
