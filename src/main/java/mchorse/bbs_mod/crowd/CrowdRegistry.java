package mchorse.bbs_mod.crowd;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-wide registry of UUIDs that are eligible for crowd LOD on the client.
 *
 * Why this exists: {@link net.minecraft.world.entity.Entity#getTags()} is a
 * server-side-only field. The vanilla entity spawn / tracking packets do NOT
 * sync command tags to clients. So a client-side render mixin reading
 * {@code entity.getCommandTags()} on a tagged crowd villager sees an empty set
 * and never triggers — confirmed in user benchmark logs (zero diagnostic dump
 * lines despite spawning thousands of tagged entities).
 *
 * Workaround for the single-player video-production use case: server and
 * integrated client share a JVM, so a static Set is visible to both. We
 * register UUIDs here from the server spawn path, and the client mixin reads
 * the same Set when deciding whether to apply LOD.
 *
 * Trade-off: this does NOT survive a server restart (in-memory only) and does
 * NOT work for dedicated-server multiplayer (separate JVMs). Both acceptable
 * for the current scope; revisit if either becomes required.
 *
 * Persistence: MobArmy and BenchmarkSpawn already write UUIDs to disk. On
 * server start they call {@link #addAll} to repopulate this registry from
 * the persisted state.
 */
public final class CrowdRegistry
{
    private static final Set<UUID> LOD_ELIGIBLE = ConcurrentHashMap.newKeySet();

    private CrowdRegistry()
    {
    }

    public static void add(UUID uuid)
    {
        LOD_ELIGIBLE.add(uuid);
    }

    public static void addAll(java.util.Collection<UUID> uuids)
    {
        LOD_ELIGIBLE.addAll(uuids);
    }

    public static void remove(UUID uuid)
    {
        LOD_ELIGIBLE.remove(uuid);
    }

    public static void removeAll(java.util.Collection<UUID> uuids)
    {
        LOD_ELIGIBLE.removeAll(uuids);
    }

    public static boolean contains(UUID uuid)
    {
        return LOD_ELIGIBLE.contains(uuid);
    }

    public static int size()
    {
        return LOD_ELIGIBLE.size();
    }

    public static void clear()
    {
        LOD_ELIGIBLE.clear();
    }

}
