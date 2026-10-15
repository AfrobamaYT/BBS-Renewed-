package mchorse.bbs_mod.crowd;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side bounce mode for villagers (or any LivingEntity). Each registered
 * entity bounces vertically following the keyframe pattern: cycle = 6 ticks,
 * sine half-curve from 0..0.97 over first 4 ticks, rest 2 ticks at 0. Each
 * entity gets a random phase offset so they don't all jump in sync.
 *
 * Ported from NeoForge BBSTools VillagerBounceRegistry.
 */
public final class BounceRegistry
{
    private static final double PEAK_HEIGHT = 0.96875D;
    private static final int CYCLE_TICKS = 6;
    private static final int RISE_FALL_TICKS = 4;

    public record BounceState(int phaseOffset, long expireTick, double baseY, boolean prevNoGravity)
    {
    }

    private static final Map<UUID, BounceState> ACTIVE = new ConcurrentHashMap<>();

    private BounceRegistry()
    {
    }

    public static void add(UUID uuid, int phaseOffset, long expireTick, double baseY, boolean prevNoGravity)
    {
        ACTIVE.put(uuid, new BounceState(phaseOffset, expireTick, baseY, prevNoGravity));
    }

    public static BounceState get(UUID uuid)
    {
        return ACTIVE.get(uuid);
    }

    public static boolean contains(UUID uuid)
    {
        return ACTIVE.containsKey(uuid);
    }

    public static int size()
    {
        return ACTIVE.size();
    }

    public static boolean isEmpty()
    {
        return ACTIVE.isEmpty();
    }

    public static Map<UUID, BounceState> all()
    {
        return ACTIVE;
    }

    public static BounceState remove(UUID uuid)
    {
        return ACTIVE.remove(uuid);
    }

    public static void clearAll()
    {
        ACTIVE.clear();
    }

    /** Apply the bounce curve to one entity. Returns Y offset added to baseY. */
    public static double curveHeight(long tick, int phaseOffset)
    {
        long t = ((tick + phaseOffset) % CYCLE_TICKS + CYCLE_TICKS) % CYCLE_TICKS;

        if (t >= RISE_FALL_TICKS)
        {
            return 0.0D;
        }

        double progress = t / (double) RISE_FALL_TICKS;

        return PEAK_HEIGHT * Math.sin(progress * Math.PI);
    }

}
