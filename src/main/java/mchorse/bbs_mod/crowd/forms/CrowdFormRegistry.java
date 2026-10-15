package mchorse.bbs_mod.crowd.forms;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side state of which villagers have a temporary form applied, and when
 * the effect expires. The form template itself is loaded client-side only via
 * {@link CrowdFormCache}; this registry just tracks identity + expiry.
 *
 * Like {@link mchorse.bbs_mod.crowd.CrowdRegistry} this is a static singleton
 * that the integrated single-player client reads directly (same JVM). For
 * dedicated-server multiplayer it would need a sync packet — out of scope.
 */
public final class CrowdFormRegistry
{
    public record Entry(CrowdFormType type, long expireAtTick)
    {
    }

    private static final Map<UUID, Entry> ENTRIES = new ConcurrentHashMap<>();

    private CrowdFormRegistry()
    {
    }

    public static void put(UUID uuid, CrowdFormType type, long expireAtTick)
    {
        ENTRIES.put(uuid, new Entry(type, expireAtTick));
    }

    public static Entry get(UUID uuid)
    {
        return ENTRIES.get(uuid);
    }

    public static boolean contains(UUID uuid)
    {
        return ENTRIES.containsKey(uuid);
    }

    public static void remove(UUID uuid)
    {
        ENTRIES.remove(uuid);
    }

    public static int size()
    {
        return ENTRIES.size();
    }

    public static void clear()
    {
        ENTRIES.clear();
    }

    public static Set<UUID> keys()
    {
        return ENTRIES.keySet();
    }

    /**
     * Removes entries whose expireAtTick has passed. Called once per server tick
     * from MobArmy (removed) so visual forms automatically
     * fade after a /mobarmy form duration argument.
     */
    public static int tickExpire(long currentTick)
    {
        int removed = 0;

        var it = ENTRIES.entrySet().iterator();

        while (it.hasNext())
        {
            var entry = it.next();

            if (entry.getValue().expireAtTick() <= currentTick)
            {
                it.remove();
                removed++;
            }
        }

        return removed;
    }

}
