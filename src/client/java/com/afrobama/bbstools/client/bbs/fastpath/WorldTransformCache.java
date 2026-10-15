// Coded by Afrobama/Claude — per-(replay, tick) entity WORLD-TRANSFORM cache.
//
// The Army bake (AnimationBakeCache) caches BONE poses → Tier2h draws them cheaply. But the
// entity's WORLD transform (position + body/head rotation, from ReplayKeyframes.apply's ~40
// KeyframeChannel.interpolate calls) plus the animator-config resolve in updateEntityAndForm
// are still computed LIVE every frame for every replay (measured: interpolate 22% + gecko 14%
// at 20k playback — the wall after the playback decouple). Both are PURE functions of (replay,
// tick), so we cache them here: on a hit, BaseFilmControllerWorldTransformMixin restores the
// entity transform and cancels updateEntityAndForm + applyReplay (skipping both interpolate and
// the gecko config resolve). Bone pose still comes from the Tier2h bake; this only kills the
// remaining live CPU.
//
// NO dedup (positions are unique per replay, unlike bone poses), so this is byte-capped + LRU
// by entity. 7 floats/tick (x,y,z,yaw,pitch,headYaw,bodyYaw); prev* is read from tick-1's entry.
//
// Controllability: the SELECTED replay is never cached (the mixin's edit-live carve-out), and a
// replay's cache is invalidated when its keyframes change — so moving/editing stays live + exact.
package com.afrobama.bbstools.client.bbs.fastpath;

import com.afrobama.bbstools.perf.BBSPerfConfig;

import mchorse.bbs_mod.forms.entities.IEntity;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class WorldTransformCache
{
    public static final int FLOATS_PER_TICK = 7;   /* x,y,z,yaw,pitch,headYaw,bodyYaw */
    private static final int BYTES_PER_TICK = FLOATS_PER_TICK * 4;

    /** Per-entity dense store: data[tick*7 .. +7] + a present bitset. */
    private static final class Entry
    {
        float[] data = new float[0];
        boolean[] present = new boolean[0];
        int bytes;

        void ensure(int tick)
        {
            int need = (tick + 1) * FLOATS_PER_TICK;
            if (need > this.data.length)
            {
                int newTicks = Math.max(tick + 1, this.present.length * 2);
                float[] nd = new float[newTicks * FLOATS_PER_TICK];
                System.arraycopy(this.data, 0, nd, 0, this.data.length);
                boolean[] np = new boolean[newTicks];
                System.arraycopy(this.present, 0, np, 0, this.present.length);
                this.data = nd;
                this.present = np;
            }
        }
    }

    /** access-ordered for LRU eviction. */
    private static final LinkedHashMap<IEntity, Entry> CACHE = new LinkedHashMap<>(1024, 0.75F, true);
    private static long totalBytes;

    private WorldTransformCache() {}

    private static long budget()
    {
        /* Share the army-bake budget; half of it (bone bake gets the other half). */
        long b = BBSPerfConfig.ARMY_BAKE_MAX_BYTES;
        return b > 0 ? b / 2 : 512L * 1024 * 1024;
    }

    /** Fill {@code out} (length >= 7) from the cache, or return false on miss. */
    public static synchronized boolean get(IEntity entity, int tick, float[] out)
    {
        if (entity == null || tick < 0) return false;
        Entry e = CACHE.get(entity);
        if (e == null || tick >= e.present.length || !e.present[tick])
        {
            BBSPerfConfig.wtBakeMisses++;
            return false;
        }
        System.arraycopy(e.data, tick * FLOATS_PER_TICK, out, 0, FLOATS_PER_TICK);
        BBSPerfConfig.wtBakeHits++;
        return true;
    }

    /** Like {@link #get} but does not touch hit/miss counters (used for the prev-tick lookup). */
    public static synchronized boolean peek(IEntity entity, int tick, float[] out)
    {
        if (entity == null || tick < 0) return false;
        Entry e = CACHE.get(entity);
        if (e == null || tick >= e.present.length || !e.present[tick]) return false;
        System.arraycopy(e.data, tick * FLOATS_PER_TICK, out, 0, FLOATS_PER_TICK);
        return true;
    }

    public static synchronized boolean has(IEntity entity, int tick)
    {
        if (entity == null || tick < 0) return false;
        Entry e = CACHE.get(entity);
        return e != null && tick < e.present.length && e.present[tick];
    }

    public static synchronized void put(IEntity entity, int tick,
        float x, float y, float z, float yaw, float pitch, float headYaw, float bodyYaw)
    {
        if (entity == null || tick < 0) return;
        Entry e = CACHE.get(entity);
        if (e == null) { e = new Entry(); CACHE.put(entity, e); }
        e.ensure(tick);
        if (!e.present[tick]) { e.present[tick] = true; e.bytes += BYTES_PER_TICK; totalBytes += BYTES_PER_TICK; }
        int o = tick * FLOATS_PER_TICK;
        e.data[o] = x; e.data[o + 1] = y; e.data[o + 2] = z;
        e.data[o + 3] = yaw; e.data[o + 4] = pitch; e.data[o + 5] = headYaw; e.data[o + 6] = bodyYaw;
        BBSPerfConfig.wtBakeBytes = totalBytes;
        evictIfOverCap();
    }

    private static void evictIfOverCap()
    {
        long cap = budget();
        Iterator<Map.Entry<IEntity, Entry>> it = CACHE.entrySet().iterator();
        while (totalBytes > cap && it.hasNext())
        {
            Map.Entry<IEntity, Entry> me = it.next();   /* access-order → eldest first */
            totalBytes -= me.getValue().bytes;
            it.remove();
            BBSPerfConfig.wtBakeEvictions++;
        }
        BBSPerfConfig.wtBakeBytes = totalBytes;
    }

    public static synchronized void invalidateEntity(IEntity entity)
    {
        if (entity == null) return;
        Entry e = CACHE.remove(entity);
        if (e != null) { totalBytes -= e.bytes; BBSPerfConfig.wtBakeBytes = totalBytes; }
    }

    public static synchronized void clear()
    {
        CACHE.clear();
        totalBytes = 0;
        BBSPerfConfig.wtBakeBytes = 0;
    }

}
