// Coded by Afrobama/Claude — Black hole event state.
// Pattern same as MASNukeEvent. Holds world position + start time.
// BH_POS uniform pushed to bbstools:blackhole/black_hole shader each
// frame by MASShaderUniformPusher. /mas blackhole on triggers the
// event at the player's look target.
//
// Unlike nuke which has expansion timers (smokeRiseTimer, flashTimer),
// blackhole is static once spawned — no per-frame state animation
// beyond GameTime which is global. Auto-deactivates after 60 seconds.
package com.afrobama.bbstools.client.veil;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MASBlackHoleEvent
{
    private static final Logger LOG = LoggerFactory.getLogger("mas/blackhole");

    private static final float TOTAL_DURATION_SEC = 60.0F;

    private static volatile Vec3d position = Vec3d.ZERO;
    private static volatile long startTimeNanos = 0L;
    private static volatile boolean active = false;

    private MASBlackHoleEvent() {}

    public static void trigger(Vec3d pos)
    {
        position = pos;
        startTimeNanos = System.nanoTime();
        active = true;
        LOG.info("Black hole triggered at ({}, {}, {})", pos.x, pos.y, pos.z);
    }

    public static Vec3d triggerAtPlayerTarget()
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return null;
        Vec3d look = mc.player.getRotationVector();
        Vec3d horiz = new Vec3d(look.x, 0.0, look.z).normalize();
        Vec3d target = new Vec3d(
            mc.player.getX() + horiz.x * 8.0,
            mc.player.getY() + 1.5,
            mc.player.getZ() + horiz.z * 8.0);
        trigger(target);
        return target;
    }

    public static void deactivate()
    {
        active = false;
        LOG.info("Black hole deactivated");
    }

    public static boolean isActive()
    {
        if (!active) return false;
        float elapsed = (System.nanoTime() - startTimeNanos) / 1_000_000_000F;
        if (elapsed > TOTAL_DURATION_SEC)
        {
            active = false;
            return false;
        }
        return true;
    }

    public static Vec3d getPosition() { return position; }

}
