package com.afrobama.bbstools.client.fracture;

import mchorse.bbs_mod.forms.entities.IEntity;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Per-actor set of bone indices that have been "fractured" — i.e.
 * detached from the actor and now exist as independent PhysicsMod
 * rigidbodies. The actor's renderer (via {@code BOBJModelVAOMixin})
 * checks this registry every draw call and skips triangles whose
 * dominant bone index is in the actor's fractured set.
 *
 * <p>Keyed by {@link IEntity} reference. We rely on reference identity
 * because {@code IEntity} has no UUID accessor and BBS keeps the same
 * wrapper instance for the actor's lifetime in a render context.
 * {@link WeakHashMap} ensures we don't leak entries when an actor is
 * destroyed: if every other reference to the IEntity is dropped, our
 * entry is GC'd automatically.</p>
 *
 * <p>Bone indices are stored (not names) because that's the format the
 * BOBJ renderer's per-triangle filter wants — see
 * {@code BOBJModelVAO.dominantBonePerTriangle[]} which holds the bone
 * index per triangle. The lookup happens once per draw call, so a
 * HashSet of integers is fast enough for typical model bone counts (~20).</p>
 *
 * <p>Thread safety: render thread reads, action-clip / collision-watcher
 * writes. Both run on the client thread, but we synchronise accessors
 * to be safe and to make the API defensively thread-safe in case any
 * future async path appears.</p>
 */
public final class FracturedBoneRegistry
{
    private static final java.util.Map<IEntity, Set<Integer>> FRACTURED = new WeakHashMap<>();

    private FracturedBoneRegistry()
    {
    }

    /** Mark a bone (by index in {@code BOBJArmature.orderedBones}) as
     *  fractured for the given actor. Idempotent. */
    public static synchronized void markFractured(IEntity actor, int boneIndex)
    {
        if (actor == null || boneIndex < 0) return;
        FRACTURED.computeIfAbsent(actor, (k) -> new HashSet<>()).add(boneIndex);
    }

    /** Mark every bone in the iterable as fractured. Used by
     *  whole-model "manual" fracture mode. */
    public static synchronized void markFracturedAll(IEntity actor, Iterable<Integer> boneIndices)
    {
        if (actor == null || boneIndices == null) return;
        Set<Integer> set = FRACTURED.computeIfAbsent(actor, (k) -> new HashSet<>());
        for (Integer i : boneIndices)
        {
            if (i != null && i >= 0) set.add(i);
        }
    }

    /** Restore an actor — all bones rendered again. Called on rewind
     *  or film stop via {@link FractureRegistry}. */
    public static synchronized void clear(IEntity actor)
    {
        if (actor == null) return;
        FRACTURED.remove(actor);
    }

    /** Clear EVERY actor's fractured-bone set. Called on world unload
     *  to avoid stale entries on next world load. */
    public static synchronized void clearAll()
    {
        FRACTURED.clear();
    }

    public static synchronized boolean isFractured(IEntity actor, int boneIndex)
    {
        if (actor == null || boneIndex < 0) return false;
        Set<Integer> set = FRACTURED.get(actor);
        return set != null && set.contains(boneIndex);
    }

    /** Snapshot of an actor's fractured bone indices. Returns an empty,
     *  unmodifiable set when the actor has no fractures. The snapshot
     *  is safe to iterate without external synchronisation. */
    public static synchronized Set<Integer> snapshot(IEntity actor)
    {
        if (actor == null) return Collections.emptySet();
        Set<Integer> set = FRACTURED.get(actor);
        if (set == null || set.isEmpty()) return Collections.emptySet();
        return Collections.unmodifiableSet(new HashSet<>(set));
    }

    public static synchronized int activeActorCount()
    {
        return FRACTURED.size();
    }

}
