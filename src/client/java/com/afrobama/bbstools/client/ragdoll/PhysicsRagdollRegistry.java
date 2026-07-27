package com.afrobama.bbstools.client.ragdoll;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Tracks every PhysicsMod ragdoll instance that's been captured via
 * the BBSPhysicsModBridge hook — including ragdolls created from
 * vanilla entity deaths (killed villager, zombie, etc.) NOT just
 * our BBS-actor proxies.
 *
 * <p>Used by the gravity-gun's pickup raycast: in addition to
 * scanning {@link com.afrobama.bbstools.client.fracture.BonePhysicsSimulator}'s
 * fracture bodies, the gun also walks every registered ragdoll's
 * {@code bodies} list and tries each PhysicsEntity's AABB.</p>
 *
 * <p>Stale entries (ragdolls that PhysicsMod has despawned) are
 * detected lazily: callers should null-check / skip dead ragdolls
 * during iteration. We don't track removal explicitly because
 * PhysicsMod doesn't expose a remove-event hook.</p>
 */
public final class PhysicsRagdollRegistry
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/ragdoll/registry");

    /** All known ragdolls. List for iteration; we accept O(N)
     *  duplicate-check on register since N is small (<100). */
    private static final List<Object> RAGDOLLS = new ArrayList<>();

    private PhysicsRagdollRegistry()
    {
    }

    public static synchronized void register(Object ragdoll)
    {
        if (ragdoll == null) return;
        for (Object r : RAGDOLLS) if (r == ragdoll) return; /* already known */
        RAGDOLLS.add(ragdoll);
        LOG.debug("[ragdoll-registry] registered ragdoll {} (total: {})",
            System.identityHashCode(ragdoll), RAGDOLLS.size());
    }

    public static synchronized List<Object> snapshot()
    {
        return new ArrayList<>(RAGDOLLS);
    }

    public static synchronized void unregister(Object ragdoll)
    {
        Iterator<Object> it = RAGDOLLS.iterator();
        while (it.hasNext())
        {
            if (it.next() == ragdoll) { it.remove(); break; }
        }
    }

    public static synchronized void clearAll()
    {
        RAGDOLLS.clear();
    }

    public static synchronized int size()
    {
        return RAGDOLLS.size();
    }

}
