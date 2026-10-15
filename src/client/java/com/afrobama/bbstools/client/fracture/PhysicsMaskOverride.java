// Coded by Afrobama/Claude
package com.afrobama.bbstools.client.fracture;

/**
 * Thread-local PhysicsMod {@code physicsMask} override holder.
 *
 * <p>PhysicsMod's body-creation path (e.g. {@code ConvexRigidBody.create})
 * reads {@code PhysicsEntity.physicsMask} at construction to set up the
 * underlying PhysX collision filter. After construction the field is
 * effectively frozen — the PhysX actor's filter doesn't re-read it.</p>
 *
 * <p>So our destruction clip's {@code noInterCollision} toggle can't
 * work via post-spawn writes (the body has already been filter-baked).
 * Instead we set this thread-local before invoking BBS's
 * {@code spawnPhysicsModFracture}, our companion mixin
 * ({@code ConvexRigidBodyMaskMixin} + {@code BoxRigidBodyMaskMixin})
 * peeks at the value at {@code create()} HEAD and overrides
 * {@code entity.physicsMask} BEFORE the PhysX actor reads it.</p>
 *
 * <p>Use the {@link #with} helper for try/finally safety.</p>
 */
public final class PhysicsMaskOverride
{
    /** Sentinel: null = no override. Box wrapper so we can distinguish
     *  "no override" from "override = 0". */
    private static final ThreadLocal<Byte> CURRENT = new ThreadLocal<>();

    private PhysicsMaskOverride() {}

    /** Returns the current override mask byte, or null if no override
     *  is active on this thread. Read by the body-creation mixins. */
    public static Byte get()
    {
        return CURRENT.get();
    }

    public static void set(Byte mask)
    {
        if (mask == null) CURRENT.remove();
        else CURRENT.set(mask);
    }

    /** Convenience helper: set the override, run the action, restore.
     *  Caller-supplied action may throw — override is always cleared. */
    public static void with(byte mask, Runnable action)
    {
        Byte prev = CURRENT.get();
        CURRENT.set(mask);
        try { action.run(); }
        finally
        {
            if (prev == null) CURRENT.remove();
            else CURRENT.set(prev);
        }
    }

}
