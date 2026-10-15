// Coded by Afrobama/Claude — Orbital Laser event state.
// Source pattern: destroying-minecraft 1.0.0 / LaserDestructionClient.java
//
// Uses bbstools:cracks/cracks shader. Drives uniforms:
//   - CENTER_POS (vec2) — world XZ position of laser impact
//   - laserLength (0..1) — laser column extends downward as this ramps
//   - cracksTime (0..1) — terrain cracks spread outward from impact
//   - flashTimer — bright pulse on initial fire
//
// Total event: ~12s of laser + terrain cracking.
package com.afrobama.bbstools.client.veil;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MASLaserEvent
{
    private static final Logger LOG = LoggerFactory.getLogger("mas/laser");

    private static final float TOTAL_DURATION_SEC = 12.0F;
    private static final float CHARGE_DURATION_SEC = 2.0F;     /* laser charges + extends to full length */
    private static final float CRACK_DURATION_SEC = 6.0F;      /* cracks spread out from impact */
    private static final float FLASH_DURATION_SEC = 1.5F;

    private static volatile Vec3 position = Vec3.ZERO;
    private static volatile long startTimeNanos = 0L;
    private static volatile boolean active = false;

    private MASLaserEvent() {}

    public static void trigger(Vec3 pos)
    {
        position = pos;
        startTimeNanos = System.nanoTime();
        active = true;
        LOG.info("Laser triggered at XZ ({}, {})", pos.x, pos.z);
    }

    public static Vec3 triggerAtPlayerTarget()
    {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return null;
        Vec3 look = mc.player.getLookAngle();
        Vec3 horiz = new Vec3(look.x, 0.0, look.z).normalize();
        Vec3 target = new Vec3(
            mc.player.getX() + horiz.x * 15.0,
            0.0,
            mc.player.getZ() + horiz.z * 15.0);
        trigger(target);
        return target;
    }

    public static void deactivate()
    {
        active = false;
        LOG.info("Laser deactivated");
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

    public static Vec3 getPosition() { return position; }

    public static float elapsedSeconds()
    {
        return (System.nanoTime() - startTimeNanos) / 1_000_000_000F;
    }

    /** Laser column length: 0..1 over CHARGE_DURATION_SEC, then stays 1. */
    public static float getLaserLength()
    {
        if (!active) return 0.0F;
        float t = elapsedSeconds() / CHARGE_DURATION_SEC;
        return Math.max(0.0F, Math.min(1.0F, t));
    }

    /** Cracks spread: 0..1 over CRACK_DURATION_SEC (starts at charge complete). */
    public static float getCracksTime()
    {
        if (!active) return 0.0F;
        float e = elapsedSeconds() - CHARGE_DURATION_SEC;
        if (e < 0.0F) return 0.0F;
        float t = e / CRACK_DURATION_SEC;
        return Math.max(0.0F, Math.min(1.0F, t));
    }

    /** Flash on initial fire — peaks at 2 then fades over FLASH_DURATION_SEC. */
    public static float getFlashTimer()
    {
        if (!active) return 0.0F;
        float e = elapsedSeconds();
        if (e > FLASH_DURATION_SEC) return 0.0F;
        return 2.0F * (1.0F - e / FLASH_DURATION_SEC);
    }

}
