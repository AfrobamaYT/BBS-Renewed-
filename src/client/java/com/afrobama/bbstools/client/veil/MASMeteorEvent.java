// Coded by Afrobama/Claude — Custom Meteor event state.
// NOT a destroying-mc port — original design built on the patterns
// learned from porting destroying-mc's catastrophes.
//
// Animation phases:
//   0..FALL_DURATION_SEC: meteor falls diagonally from sky to target.
//     Start position is computed as target + offset (60 blocks NW, 100 high).
//     METEOR_POS interpolates start->target linearly over FALL_DURATION.
//   At fall end: impact triggers — flashTimer peaks at 2.0, afterImpactSec starts counting.
//   At afterImpactSec 0..SHOCKWAVE_DURATION: expanding shockwave ring.
//   At afterImpactSec 0..CRATER_GLOW_DURATION: lingering crater glow.
//   Auto-deactivate after FALL_DURATION + AFTERMATH_DURATION (~7s total).
package com.afrobama.bbstools.client.veil;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MASMeteorEvent
{
    private static final Logger LOG = LoggerFactory.getLogger("mas/meteor");

    /** Fall duration: meteor flies for this long before impact. */
    private static final float FALL_DURATION_SEC = 4.0F;

    /** Time after impact before event auto-deactivates. */
    private static final float AFTERMATH_DURATION_SEC = 3.0F;

    /** Flash on impact: peaks at 2.0, fades to 0 over this duration. */
    private static final float FLASH_DURATION_SEC = 0.6F;

    private static volatile Vec3 targetPos = Vec3.ZERO;
    private static volatile Vec3 startPos = Vec3.ZERO;
    private static volatile Vec3 direction = new Vec3(0.0, -1.0, 0.0);
    private static volatile long startTimeNanos = 0L;
    private static volatile boolean active = false;
    private static volatile boolean shakeFired = false;

    private MASMeteorEvent() {}

    public static void trigger(Vec3 impactTarget)
    {
        targetPos = impactTarget;
        /* Start position: 60 blocks SW horizontally + 100 blocks high → diagonal trajectory */
        startPos = new Vec3(impactTarget.x - 50.0, impactTarget.y + 90.0, impactTarget.z - 30.0);
        direction = targetPos.subtract(startPos).normalize();
        startTimeNanos = System.nanoTime();
        active = true;
        shakeFired = false;
        LOG.info("Meteor triggered, impact at ({}, {}, {})", impactTarget.x, impactTarget.y, impactTarget.z);
    }

    public static Vec3 triggerAtPlayerTarget()
    {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return null;
        /* Impact at player's horizontal look 12 blocks ahead at player's feet level. */
        Vec3 look = mc.player.getLookAngle();
        Vec3 horiz = new Vec3(look.x, 0.0, look.z).normalize();
        Vec3 target = new Vec3(
            mc.player.getX() + horiz.x * 12.0,
            mc.player.getY(),
            mc.player.getZ() + horiz.z * 12.0);
        trigger(target);
        return target;
    }

    public static void deactivate()
    {
        active = false;
        LOG.info("Meteor deactivated");
    }

    public static boolean isActive()
    {
        if (!active) return false;
        if (elapsedSeconds() > FALL_DURATION_SEC + AFTERMATH_DURATION_SEC)
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

    /** Fall progress 0..1 over FALL_DURATION_SEC. Stays at 1.0 thereafter. */
    public static float getFallProgress()
    {
        if (!active) return 0.0F;
        float t = elapsedSeconds() / FALL_DURATION_SEC;
        return Math.max(0.0F, Math.min(1.0F, t));
    }

    /** Current meteor world position interpolated start -> target. */
    public static Vec3 getMeteorPos()
    {
        if (!active) return targetPos;
        float p = getFallProgress();
        return startPos.lerp(targetPos, p);
    }

    public static Vec3 getMeteorDir() { return direction; }
    public static Vec3 getImpactPos() { return targetPos; }

    /** Seconds since impact. 0 before impact. */
    public static float getAfterImpactSec()
    {
        if (!active) return 0.0F;
        float e = elapsedSeconds() - FALL_DURATION_SEC;
        return Math.max(0.0F, e);
    }

    /** Fire camera shake ONCE on impact transition. Call from per-frame
     *  uniform pusher. Per real-meteor reference: sharp single jolt then
     *  ~2-5s decay (CameraShakeManager handles decay automatically). */
    public static void tickShakeOnImpact()
    {
        if (!active || shakeFired) return;
        if (getAfterImpactSec() > 0.0F)
        {
            /* Intensity 6.0 = strong meteor impact (max-amplitude tier).
             * CameraShakeManager.DECAY_PER_FRAME (0.88) gives ~2-3s
             * natural decay, matching Chelyabinsk-class shake duration. */
            com.afrobama.bbstools.client.fracture.CameraShakeManager.addShake(6.0F);
            shakeFired = true;
            LOG.info("Meteor impact shake fired (intensity 6.0)");
        }
    }

    /** Flash peaks at 2.0 immediately on impact, fades over FLASH_DURATION_SEC. */
    public static float getFlashTimer()
    {
        if (!active) return 0.0F;
        float ai = getAfterImpactSec();
        if (ai <= 0.0F || ai > FLASH_DURATION_SEC) return 0.0F;
        return 2.0F * (1.0F - ai / FLASH_DURATION_SEC);
    }

}
