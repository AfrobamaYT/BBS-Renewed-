package com.afrobama.bbstools.client.fracture;

import mchorse.bbs_mod.forms.entities.IEntity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bookkeeping for active Phase B fracture state per actor. Tracks:
 * <ul>
 *   <li>The {@link BonePhysicsBody} list (so rewind cleanup can mark
 *       them dead in {@link BonePhysicsSimulator})</li>
 *   <li>The "last tick seen" by the polling hook (for rewind detection)</li>
 *   <li>Lifetime cap in ticks</li>
 * </ul>
 *
 * <p>Rewind detection: every client tick, the polling hook reads the
 * current film tick of each known controller. If the new tick is less
 * than the cached "last seen" tick, OR the controller has stopped, we
 * cleanup the fracture: mark all BonePhysicsBodies dead so the
 * simulator drops them, and the bone matrices return to BBS's normal
 * setupMatrices output (so the model renders whole again).</p>
 *
 * <p>Loop reset: when a film loops, controller tick jumps from
 * {@code looping_length - 1} back to {@code 0}. That regression
 * triggers cleanup naturally — the fracture clip will re-fire on next
 * loop and re-spawn fresh bodies.</p>
 */
public final class FractureRegistry
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/fracture/registry");

    public static final class FractureEntry
    {
        public final IEntity actor;
        public final List<BonePhysicsBody> bodies;
        public final int spawnTick;
        public final int lifetimeTicks;
        public int lastTickSeen;

        public FractureEntry(IEntity actor, List<BonePhysicsBody> bodies, int spawnTick, int lifetimeTicks)
        {
            this.actor = actor;
            this.bodies = bodies;
            this.spawnTick = spawnTick;
            this.lifetimeTicks = lifetimeTicks;
            this.lastTickSeen = spawnTick;
        }
    }

    private static final Map<IEntity, FractureEntry> ENTRIES = new java.util.WeakHashMap<>();

    private FractureRegistry()
    {
    }

    public static synchronized void register(IEntity actor, List<BonePhysicsBody> bodies,
                                             int spawnTick, int lifetimeTicks)
    {
        if (actor == null) return;

        /* Append to existing entry if any (e.g. collision watcher
         *  spawning more bodies as bones progressively hit the wall),
         *  otherwise create a fresh entry. */
        FractureEntry existing = ENTRIES.get(actor);
        if (existing != null)
        {
            existing.bodies.addAll(bodies);
        }
        else
        {
            ENTRIES.put(actor, new FractureEntry(actor, new ArrayList<>(bodies), spawnTick, lifetimeTicks));
        }
    }

    public static synchronized void onPlaybackTick(IEntity actor, int currentTick)
    {
        FractureEntry entry = ENTRIES.get(actor);
        if (entry == null) return;

        boolean rewound = currentTick < entry.lastTickSeen;
        boolean lifetimeExceeded = entry.lifetimeTicks > 0
                                && (currentTick - entry.spawnTick) >= entry.lifetimeTicks;

        entry.lastTickSeen = currentTick;

        if (rewound || lifetimeExceeded)
        {
            String reason = rewound ? "rewind" : "lifetime";
            LOG.info("Fracture cleanup ({}): killing {} bone-bodies for actor",
                reason, entry.bodies.size());
            cleanup(entry);
            ENTRIES.remove(actor);

            /* On rewind, also restore any blocks destroyed during this
             *  take. (Lifetime cleanup leaves blocks destroyed — that's
             *  the "permanent destruction" cinematic intent.) Users
             *  who want to keep destruction can simply not rewind. */
            if (rewound)
            {
                BlockSnapshotRegistry.restoreAll();
            }
        }
    }

    public static synchronized void clearActor(IEntity actor)
    {
        FractureEntry entry = ENTRIES.remove(actor);
        if (entry != null) cleanup(entry);
    }

    public static synchronized void clearAll()
    {
        List<FractureEntry> snapshot = new ArrayList<>(ENTRIES.values());
        ENTRIES.clear();
        for (FractureEntry entry : snapshot) cleanup(entry);
        BonePhysicsSimulator.clearAll();
        /* Restore any blocks that were removed during this take so the
         *  world goes back to its pre-fracture state for the next
         *  recording. */
        BlockSnapshotRegistry.restoreAll();
        LOG.info("Cleared {} fractures on world unload", snapshot.size());
    }

    public static synchronized int activeFractureCount()
    {
        return ENTRIES.size();
    }

    private static void cleanup(FractureEntry entry)
    {
        for (BonePhysicsBody b : entry.bodies) b.dead = true;
        BonePhysicsSimulator.clearActor(entry.actor);
    }

}
