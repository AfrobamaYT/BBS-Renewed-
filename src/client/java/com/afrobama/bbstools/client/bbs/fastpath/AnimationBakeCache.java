// Coded by Afrobama/Claude — Army System P1+: content-deduplicated bake cache.
//
// THE foundation of the Army bake engine (see ARMY_BAKE_PLAN.md). Instead of
// driving the animator to an arbitrary tick (risky — may not reproduce BBS's
// exact pose pipeline), we RECORD the live per-tick bone matrices the first
// time a replay renders at a given tick, then replay them from cache on every
// subsequent render at that tick. DaVinci-Resolve render-cache model: the cache
// IS the live output, so cached == live by construction.
//
// DEDUP (the 100k-scale fix): N identical replays (same animation, e.g. a
// duplicated villager crowd) produce BIT-IDENTICAL bone matrices each tick.
// Per-entity keying stored N copies → 3000 villagers × full film duration ×
// 4KB/tick blew past the 4GB cap and THRASHED (hitRate 82%, 1.3M evictions,
// bar never reaching 100%). Now identical arrays are interned into a shared
// POOL (content equality, NOT a hash → zero collision risk), and each
// (entity, tick) just holds an 8-byte reference. 3000 identical → 1 array per
// tick (~0.8MB total) instead of ~2.4GB.
//
// Win: a cache hit skips the entire animator (resetPose + applyActions +
// applyPose + processRenderModel) AND is a pure read of immutable data → no
// shared-model race. Eviction is reference-counted: a pooled array is freed
// only when its last (entity, tick) referrer is evicted/invalidated.
//
// Keying: per-entity identity (each replay owns one StubEntity for the session,
// see BaseFilmController.createEntities) + integer tick → interned Content.
// WeakHashMap on the entity → when the film rebuilds entities the old entity is
// GC'd; its tick→Content refs drop on the next eviction sweep / invalidate.
//
// Bone matrices are stored column-major (Matrix4f.get(float[16])), so fill is
// an exact round-trip via Matrix4f.set(float[16]).
package com.afrobama.bbstools.client.bbs.fastpath;

import com.afrobama.bbstools.perf.BBSPerfConfig;

import mchorse.bbs_mod.forms.entities.IEntity;

import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

public final class AnimationBakeCache
{
    private AnimationBakeCache() {}

    /** Interned, content-equal bone-matrix array + refcount for eviction. */
    private static final class Content
    {
        final float[] data;
        final int hash;
        int refs;

        Content(float[] data)
        {
            this.data = data;
            this.hash = Arrays.hashCode(data);
        }

        @Override
        public int hashCode() { return this.hash; }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (!(o instanceof Content c)) return false;
            return this.hash == c.hash && Arrays.equals(this.data, c.data);
        }
    }

    /** Per-entity frame store: tick -> interned Content. Weak key so a
     *  rebuilt/removed replay's entry becomes collectible. */
    private static final Map<IEntity, Map<Integer, Content>> CACHE = new WeakHashMap<>();

    /** Content intern pool (dedup): identical arrays map to one shared Content. */
    private static final Map<Content, Content> POOL = new HashMap<>();

    /** Global LRU of (entity, tick) insertion order for byte-cap eviction. */
    private static final ArrayDeque<Key> LRU = new ArrayDeque<>();

    private static long CACHED_BYTES = 0L;

    private static final class Key
    {
        final IEntity entity;
        final int tick;
        Key(IEntity e, int t) { this.entity = e; this.tick = t; }
    }

    /* ------------------------------------------------------------------
     * Per-UNIQUE-ANIMATION sharing (the 100k-scale fix).
     *
     * Entities that produce IDENTICAL bone matrices (e.g. 3000 copies of the
     * same villager, or any two replays with the same model-local animation —
     * world position is applied separately, so it doesn't count) share ONE
     * frame map. The background baker computes a signature per replay (sampled
     * bone output) and binds the entity to the signature's shared map; only the
     * first-bound entity (the "representative") is actually baked, the rest read
     * the shared result for free. Different models → different signatures → no
     * sharing (each baked once), so it's correct for varied-model crowds too.
     * ------------------------------------------------------------------ */

    /** Content-equality key over a float[] (bone-sample fingerprint) PLUS the model
     *  identity. The bone fingerprint alone is NOT enough: two DIFFERENT models can
     *  emit identical model-local bone matrices at the sampled ticks (cubic rigs at
     *  an idle/default pose all look like identity), which collapsed them into one
     *  shared frame-map → only one model rendered (the other got a foreign bone
     *  palette → degenerate/invisible; "only one of two models visible, swaps on
     *  edit"). Folding the model id in keeps distinct models in distinct groups. */
    public static final class SignatureKey
    {
        private final String modelKey;
        private final float[] data;
        private final int hash;

        public SignatureKey(String modelKey, float[] data)
        {
            this.modelKey = modelKey == null ? "" : modelKey;
            this.data = data;
            this.hash = 31 * this.modelKey.hashCode() + Arrays.hashCode(data);
        }

        @Override
        public int hashCode() { return this.hash; }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (!(o instanceof SignatureKey k)) return false;
            return this.hash == k.hash && this.modelKey.equals(k.modelKey) && Arrays.equals(this.data, k.data);
        }
    }

    /** signature -> shared frame map (identical animations share it). */
    private static final Map<Object, Map<Integer, Content>> SIG_MAPS = new HashMap<>();

    /** Build a signature key from a bone-sample fingerprint (concatenated
     *  flat matrices sampled across the animation). Equality-based → no
     *  hash-collision false-sharing. */
    public static SignatureKey signatureOf(String modelKey, float[] sampleConcat)
    {
        return new SignatureKey(modelKey, sampleConcat);
    }

    /** Bind an entity to its animation signature's SHARED frame map. Identical
     *  animations point at the same map, so baking via ANY one of them fills it
     *  for all — and the baker picks whichever entity is currently active each
     *  tick (no persistent "representative" that goes stale when entities are
     *  rebuilt, e.g. switching world↔editor — that froze the bake at half). */
    public static synchronized void bind(IEntity entity, Object signature)
    {
        if (entity == null || signature == null) return;
        Map<Integer, Content> shared = SIG_MAPS.computeIfAbsent(signature, k -> new HashMap<>());
        CACHE.put(entity, shared);
    }

    public static synchronized int signatureGroups()
    {
        return SIG_MAPS.size();
    }

    /* ------------------------------------------------------------------
     * VAT (animation-texture) support. The shared frame map bound to an
     * entity IS its unique-animation identity (same anim → same map object),
     * so the atlas builder groups instances by this object and stacks each
     * group's per-tick palettes into one GPU texture uploaded ONCE. See
     * Tier2hInstancedDraw VAT path + [[project_animation_texture_vat]].
     * ------------------------------------------------------------------ */

    /** The shared frame-map object an entity is bound to (its unique-animation
     *  group identity), or null if the entity has no cached frames yet. Use the
     *  returned reference ONLY as an identity key + pass back to {@link
     *  #paletteOf}; never mutate it. */
    public static synchronized Object groupOf(IEntity entity)
    {
        if (entity == null) return null;
        return CACHE.get(entity);
    }

    /** Flat column-major bone palette for a group (from {@link #groupOf}) at a
     *  given tick, or null if that tick isn't baked for the group. */
    @SuppressWarnings("unchecked")
    public static synchronized float[] paletteOf(Object group, int tick)
    {
        if (!(group instanceof Map)) return null;
        Content c = ((Map<Integer, Content>) group).get(tick);
        return c == null ? null : c.data;
    }

    /** Highest tick index baked across ALL groups (atlas height = maxTick+1),
     *  or -1 when nothing is baked. */
    public static synchronized int maxTick()
    {
        int m = -1;
        for (Map<Integer, Content> fm : SIG_MAPS.values())
        {
            for (Integer t : fm.keySet())
            {
                if (t != null && t > m) m = t;
            }
        }
        return m;
    }

    /** Return cached flat bone matrices for (entity, tick), or null on miss.
     *  Caller must NOT mutate the returned array (it may be shared). */
    public static synchronized float[] get(IEntity entity, int tick)
    {
        Map<Integer, Content> frames = CACHE.get(entity);
        if (frames == null) return null;
        Content c = frames.get(tick);
        if (c != null)
        {
            BBSPerfConfig.armyBakeHits++;
            return c.data;
        }
        return null;
    }

    /** Store freshly-extracted flat bone matrices for (entity, tick). The array
     *  is interned: identical content across replays collapses to one copy. */
    public static synchronized void put(IEntity entity, int tick, float[] flat)
    {
        Content probe = new Content(flat);
        Content shared = POOL.get(probe);
        if (shared == null)
        {
            shared = probe;
            POOL.put(shared, shared);
            CACHED_BYTES += (long) flat.length * 4L;
            BBSPerfConfig.armyBakeUnique = POOL.size();
        }

        Map<Integer, Content> frames = CACHE.get(entity);
        if (frames == null)
        {
            frames = new HashMap<>();
            CACHE.put(entity, frames);
        }

        Content prev = frames.put(tick, shared);
        if (prev == shared) return; /* already pointing at this content */

        if (prev != null) decRef(prev);
        shared.refs++;

        LRU.addLast(new Key(entity, tick));
        BBSPerfConfig.armyBakeMisses++;
        BBSPerfConfig.armyBakeFrames++;
        BBSPerfConfig.armyBakeBytes = CACHED_BYTES;

        evictIfOverCap();
    }

    private static void decRef(Content c)
    {
        if (c == null) return;
        if (--c.refs <= 0)
        {
            POOL.remove(c);
            CACHED_BYTES -= (long) c.data.length * 4L;
            BBSPerfConfig.armyBakeUnique = POOL.size();
        }
    }

    /** Drop cached frames for an entity in [fromTick, toTick] inclusive.
     *  Used for partial invalidation when a keyframe in that range is edited. */
    public static synchronized void invalidateRange(IEntity entity, int fromTick, int toTick)
    {
        Map<Integer, Content> frames = CACHE.get(entity);
        if (frames == null) return;
        for (int t = fromTick; t <= toTick; t++)
        {
            Content removed = frames.remove(t);
            if (removed != null)
            {
                decRef(removed);
                BBSPerfConfig.armyBakeFrames--;
            }
        }
        BBSPerfConfig.armyBakeBytes = CACHED_BYTES;
    }

    public static synchronized void invalidateEntity(IEntity entity)
    {
        Map<Integer, Content> frames = CACHE.remove(entity);
        if (frames != null)
        {
            for (Content c : frames.values()) decRef(c);
            BBSPerfConfig.armyBakeFrames -= frames.size();
            if (BBSPerfConfig.armyBakeFrames < 0L) BBSPerfConfig.armyBakeFrames = 0L;
            BBSPerfConfig.armyBakeBytes = CACHED_BYTES;
        }
    }

    /** Detach an entity from its frame map WITHOUT freeing the map's contents.
     *  CRITICAL for edit re-bake: deduped replays SHARE one frame map (3000
     *  villagers → one map), so invalidateEntity on a shared one would free the
     *  bake every other replay still reads (→ corrupted crowd + armyBakeFrames
     *  going negative). unbind only drops THIS entity's mapping; the shared map
     *  stays intact for the others. The entity then has no frames → re-binds to
     *  a fresh signature group (de-merge) and re-bakes alone. */
    public static synchronized void unbind(IEntity entity)
    {
        CACHE.remove(entity);
        BBSPerfConfig.crowdBakeComplete = false; /* a replay's bake was dropped → re-verify completeness */
    }

    /** Number of distinct ticks cached for an entity (cache-progress bar).
     *  Dedup-independent — counts this entity's tick references. */
    public static synchronized int cachedCount(IEntity entity)
    {
        Map<Integer, Content> frames = CACHE.get(entity);
        return frames == null ? 0 : frames.size();
    }

    /** First tick in [0, expected) NOT cached for this entity, or -1 if every
     *  tick in the range is cached. Background baker uses this to find the next
     *  frame to record (progressive fill). */
    public static synchronized int firstUncachedTick(IEntity entity, int expected)
    {
        if (expected <= 0) return -1;
        Map<Integer, Content> frames = CACHE.get(entity);
        if (frames == null) return 0;
        if (frames.size() >= expected) return -1;
        for (int t = 0; t < expected; t++)
        {
            if (!frames.containsKey(t)) return t;
        }
        return -1;
    }

    /* ------------------------------------------------------------------
     * Per-replay progress publish (legacy; the list cache bar now reads the
     * cache LIVE via the controller, but kept for any other consumer).
     * ------------------------------------------------------------------ */

    private static final Map<String, int[]> PROGRESS = new HashMap<>();

    public static synchronized void publishProgress(String uuid, int cached, int expected)
    {
        if (uuid == null) return;
        int[] p = PROGRESS.get(uuid);
        if (p == null) { p = new int[2]; PROGRESS.put(uuid, p); }
        p[0] = cached;
        p[1] = expected;
    }

    public static synchronized int[] getProgress(String uuid)
    {
        if (uuid == null) return null;
        int[] p = PROGRESS.get(uuid);
        return p == null ? null : new int[] { p[0], p[1] };
    }

    public static synchronized void clear()
    {
        CACHE.clear();
        POOL.clear();
        LRU.clear();
        PROGRESS.clear();
        SIG_MAPS.clear();
        CACHED_BYTES = 0L;
        BBSPerfConfig.armyBakeFrames = 0L;
        BBSPerfConfig.armyBakeBytes = 0L;
        BBSPerfConfig.armyBakeUnique = 0L;
        BBSPerfConfig.crowdBakeComplete = false; /* whole bake cleared → not complete */
    }

    /** Evict oldest (entity, tick) refs until under the byte cap. A pooled
     *  array is freed only when its last referrer is evicted (decRef). */
    private static void evictIfOverCap()
    {
        long cap = BBSPerfConfig.ARMY_BAKE_MAX_BYTES;
        while (CACHED_BYTES > cap && !LRU.isEmpty())
        {
            Key k = LRU.pollFirst();
            Map<Integer, Content> frames = CACHE.get(k.entity);
            if (frames == null) continue;
            Content removed = frames.remove(k.tick);
            if (removed != null)
            {
                decRef(removed);
                BBSPerfConfig.armyBakeFrames--;
                BBSPerfConfig.armyBakeEvictions++;
                if (frames.isEmpty()) CACHE.remove(k.entity);
            }
        }
        BBSPerfConfig.armyBakeBytes = CACHED_BYTES;
    }

    /** Flatten a CubicMatrixRenderer-style bone list into column-major floats. */
    public static float[] flatten(java.util.List<Matrix4f> matrices)
    {
        int n = matrices.size();
        float[] flat = new float[n * 16];
        float[] tmp = new float[16];
        for (int i = 0; i < n; i++)
        {
            matrices.get(i).get(tmp);
            System.arraycopy(tmp, 0, flat, i * 16, 16);
        }
        return flat;
    }

    public static String snapshot()
    {
        long hits = BBSPerfConfig.armyBakeHits;
        long miss = BBSPerfConfig.armyBakeMisses;
        long total = hits + miss;
        double hitRate = total == 0L ? 0.0 : (hits * 100.0) / total;
        long frames = BBSPerfConfig.armyBakeFrames;
        long unique = BBSPerfConfig.armyBakeUnique;
        double dedup = unique == 0L ? 1.0 : (double) frames / (double) unique;
        return String.format(
            "enabled=%s frames=%d unique=%d dedup=%.1fx bytes=%.1fMB hits=%d misses=%d hitRate=%.1f%% evictions=%d capMB=%d",
            BBSPerfConfig.ENABLE_ARMY_BAKE,
            frames, unique, dedup,
            BBSPerfConfig.armyBakeBytes / (1024.0 * 1024.0),
            hits, miss, hitRate, BBSPerfConfig.armyBakeEvictions,
            BBSPerfConfig.ARMY_BAKE_MAX_BYTES / (1024 * 1024));
    }

    public static void resetCounters()
    {
        BBSPerfConfig.armyBakeHits = 0L;
        BBSPerfConfig.armyBakeMisses = 0L;
        BBSPerfConfig.armyBakeEvictions = 0L;
        /* frames/bytes/unique reflect live cache contents — not reset here. */
    }

}
