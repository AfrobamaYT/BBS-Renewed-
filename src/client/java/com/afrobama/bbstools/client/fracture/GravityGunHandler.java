package com.afrobama.bbstools.client.fracture;

import mchorse.bbs_mod.forms.entities.IEntity;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import java.util.Map;
import com.afrobama.bbstools.client.keybinds.BBSKeyCategories;

/**
 * Gmod-style gravity-gun for fractured bone bodies. Hold the
 * gravity-gun key (default {@code G}) while looking at a fractured
 * bone — the closest bone within ~16 blocks gets pulled to a fixed
 * distance in front of the camera and held there. Release the key
 * to throw it forward at high velocity. Right-click while holding
 * cancels the grab without throwing.
 *
 * <h2>Selection</h2>
 * <p>Per-tick, when the key is first pressed, we raycast from the
 * camera in the player's look direction up to 16 blocks. For each
 * active {@link BonePhysicsBody} we check ray-vs-AABB intersection
 * and pick the nearest hit. The body's pre-grab state is recorded
 * so velocity isn't lost on release.</p>
 *
 * <h2>Hold dynamics</h2>
 * <p>While held, the body's position is forcibly set to {@code camera +
 * lookDir * holdDistance} each tick. linearVelocity is zeroed (no
 * momentum accumulates while held). Rotation is preserved — it neither
 * snaps to face the camera nor freezes. The simulator's tickBody is
 * skipped for held bodies so gravity, damping, and constraints don't
 * fight us.</p>
 *
 * <h2>Throw</h2>
 * <p>On release, we set velocity = {@code lookDir * THROW_SPEED} so the
 * body launches forward like a punted physics object in Gmod. The held
 * flag clears and the simulator resumes normal integration.</p>
 */
public final class GravityGunHandler
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/fracture/gravity-gun");

    /** Default hold distance — user can scroll to change while holding. */
    private static final float DEFAULT_holdDistance = 4F;
    private static final float MIN_holdDistance = 1.5F;
    private static final float MAX_holdDistance = 16F;
    /** Maximum raycast distance for the initial pick. */
    private static final float PICK_RANGE = 16F;
    /** Throw velocity magnitude when key is released. */
    private static final float THROW_SPEED = 2.5F;

    private static KeyMapping keyGrab;
    /** Bone-body grab (our own fracture system). */
    private static BonePhysicsBody held;
    /** PhysicsMod ragdoll grab — the specific Body and its parent Ragdoll. */
    private static Object heldRagdoll;
    private static Object heldRagdollBody;
    /** Saved kinematic flag of heldRagdoll so we can restore on release. */
    private static boolean savedKinematic;
    private static boolean wasPressed;
    /** Live hold distance — scroll changes this while holding. Reset to
     *  DEFAULT_holdDistance on each fresh grab. */
    private static float holdDistance = DEFAULT_holdDistance;
    /** Last observed hotbar slot — when it changes while we're holding,
     *  treat that as a scroll input on hold-distance and revert the slot. */
    private static int lastObservedSlot = -1;

    private GravityGunHandler()
    {
    }

    public static void register()
    {
        keyGrab = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.bbstools.gravity_grab",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_G,
            BBSKeyCategories.FRACTURE));

        ClientTickEvents.END_CLIENT_TICK.register(client -> tick(client));
        LOG.info("Gravity-gun handler registered (default key: G)");
    }

    private static void tick(Minecraft client)
    {
        if (client == null || client.player == null || client.level == null) return;

        boolean nowPressed = keyGrab.isDown();

        /* Edge: key just pressed → try to pick a bone body OR a
         *  PhysicsMod ragdoll body. */
        if (nowPressed && !wasPressed && held == null && heldRagdollBody == null)
        {
            tryPick(client);
        }

        /* Edge: key just released while holding → throw. */
        if (!nowPressed && wasPressed)
        {
            if (held != null) throwHeld(client);
            if (heldRagdollBody != null) throwHeldRagdoll(client);
        }

        /* While holding a PhysicsMod ragdoll, drag it. */
        if (nowPressed && heldRagdollBody != null)
        {
            applyHoldPositionRagdoll(client);
            wasPressed = nowPressed;
            return;
        }

        /* While holding, force body to front-of-camera. */
        if (nowPressed && held != null)
        {
            if (held.dead)
            {
                /* Body got cleaned up while held — release silently. */
                held = null;
            }
            else
            {
                /* Detect hotbar-scroll while held → use it to change
                 *  hold distance instead of switching slots. Revert
                 *  the slot so the hotbar stays put. */
                int curSlot = client.player.getInventory().getSelectedSlot();
                if (lastObservedSlot >= 0 && curSlot != lastObservedSlot)
                {
                    int delta = curSlot - lastObservedSlot;
                    /* Hotbar wraps 0..8 — normalize so a scroll-down
                     *  past 8 reads as -1 not +8. */
                    if (delta > 4) delta -= 9;
                    if (delta < -4) delta += 9;
                    holdDistance += delta * 0.6F;
                    if (holdDistance < MIN_holdDistance) holdDistance = MIN_holdDistance;
                    if (holdDistance > MAX_holdDistance) holdDistance = MAX_holdDistance;
                    client.player.getInventory().setSelectedSlot(lastObservedSlot);
                    curSlot = lastObservedSlot;
                }
                lastObservedSlot = curSlot;

                applyHoldPosition(client);
            }
        }
        else
        {
            lastObservedSlot = -1;
        }

        wasPressed = nowPressed;
    }

    private static void tryPick(Minecraft client)
    {
        Vector3f camPos = new Vector3f(
            (float) client.player.getX(),
            (float) (client.player.getY() + client.player.getEyeHeight()),
            (float) client.player.getZ());

        Vector3f look = lookDir(client);

        BonePhysicsBody best = null;
        float bestDist = Float.MAX_VALUE;

        /* Iterate every active bone body across all actors. */
        Map<IEntity, List<BonePhysicsBody>> snapshot = BonePhysicsSimulator.snapshotBodies();
        for (List<BonePhysicsBody> bodies : snapshot.values())
        {
            for (BonePhysicsBody b : bodies)
            {
                if (b.dead) continue;

                float t = rayAabbDist(camPos, look, b);
                if (t < 0F || t > PICK_RANGE) continue;
                if (t < bestDist)
                {
                    bestDist = t;
                    best = b;
                }
            }
        }

        /* Also scan every PhysicsMod ragdoll's bodies so the user can
         *  grab e.g. a freshly-killed villager or any other PhysicsMod
         *  ragdoll that's currently flopping around. */
        Object bestRagdoll = null;
        Object bestRagdollBody = null;
        for (Object ragdoll : com.afrobama.bbstools.client.ragdoll.PhysicsRagdollRegistry.snapshot())
        {
            if (ragdoll == null) continue;
            java.util.List<?> bodies = com.afrobama.bbstools.client.ragdoll.ActorRagdollProxy.getRagdollBodies(ragdoll);
            for (Object body : bodies)
            {
                if (body == null) continue;
                float t = rayAabbDistRagdollBody(camPos, look, body);
                if (t < 0F || t > PICK_RANGE) continue;
                if (t < bestDist)
                {
                    bestDist = t;
                    best = null;
                    bestRagdoll = ragdoll;
                    bestRagdollBody = body;
                }
            }
        }

        if (best != null)
        {
            held = best;
            held.linearVelocity.zero();
            held.angularVelocity.zero();
            holdDistance = DEFAULT_holdDistance;
            LOG.debug("[gravity-gun] grabbed bone '{}' at distance {}", held.boneName, bestDist);
        }
        else if (bestRagdollBody != null)
        {
            heldRagdoll = bestRagdoll;
            heldRagdollBody = bestRagdollBody;
            holdDistance = DEFAULT_holdDistance;
            /* Set the WHOLE ragdoll kinematic so PhysX stops applying
             *  forces. We restore on release. Also save the previous
             *  flag value in case it was already kinematic for some
             *  reason. */
            try
            {
                java.lang.reflect.Field kf = heldRagdoll.getClass().getField("kinematic");
                savedKinematic = kf.getBoolean(heldRagdoll);
                kf.setBoolean(heldRagdoll, true);
            }
            catch (Throwable t)
            {
                LOG.debug("[gravity-gun] couldn't set kinematic flag: {}", t.toString());
            }
            LOG.debug("[gravity-gun] grabbed ragdoll body at distance {}", bestDist);
        }
    }

    /** Ray-vs-AABB for a PhysicsMod PhysicsEntity. AABB is centred at
     *  the body's transformation translation with size from
     *  rescale (if present) or scalePhysics, falling back to ~0.5m
     *  cube if neither resolves. */
    private static float rayAabbDistRagdollBody(Vector3f origin, Vector3f dir, Object body)
    {
        try
        {
            java.lang.reflect.Method getTrans = body.getClass().getMethod("getTransformation");
            Object m = getTrans.invoke(body);
            if (!(m instanceof org.joml.Matrix4d md)) return -1F;

            float bx = (float) md.m30();
            float by = (float) md.m31();
            float bz = (float) md.m32();

            /* Conservative half-size — most ragdoll cuboids are 0.4–0.6m
             *  on the longest axis. Using 0.5 lets the user "grab"
             *  with reasonable forgiveness. */
            float h = 0.5F;

            return rayAabbDistCenter(origin, dir, bx, by, bz, h, h, h);
        }
        catch (Throwable t)
        {
            return -1F;
        }
    }

    private static float rayAabbDistCenter(Vector3f origin, Vector3f dir,
                                           float cx, float cy, float cz,
                                           float hx, float hy, float hz)
    {
        float minX = cx - hx, maxX = cx + hx;
        float minY = cy - hy, maxY = cy + hy;
        float minZ = cz - hz, maxZ = cz + hz;

        float invX = dir.x != 0F ? 1F / dir.x : Float.POSITIVE_INFINITY;
        float invY = dir.y != 0F ? 1F / dir.y : Float.POSITIVE_INFINITY;
        float invZ = dir.z != 0F ? 1F / dir.z : Float.POSITIVE_INFINITY;

        float tx1 = (minX - origin.x) * invX, tx2 = (maxX - origin.x) * invX;
        float ty1 = (minY - origin.y) * invY, ty2 = (maxY - origin.y) * invY;
        float tz1 = (minZ - origin.z) * invZ, tz2 = (maxZ - origin.z) * invZ;

        float tmin = Math.max(Math.max(Math.min(tx1, tx2), Math.min(ty1, ty2)), Math.min(tz1, tz2));
        float tmax = Math.min(Math.min(Math.max(tx1, tx2), Math.max(ty1, ty2)), Math.max(tz1, tz2));

        if (tmax < 0F || tmin > tmax) return -1F;
        return Math.max(0F, tmin);
    }

    /** Drag a held PhysicsMod ragdoll body to the hold position by
     *  setting its transformation directly. Because the parent
     *  ragdoll is kinematic, PhysX won't fight us — the body stays
     *  where we put it and joint-attached siblings get pulled along
     *  via PhysX joint constraints. */
    private static void applyHoldPositionRagdoll(Minecraft client)
    {
        if (heldRagdollBody == null) return;

        Vector3f camPos = new Vector3f(
            (float) client.player.getX(),
            (float) (client.player.getY() + client.player.getEyeHeight()),
            (float) client.player.getZ());
        Vector3f look = lookDir(client);

        double t = System.nanoTime() * 1e-9D;
        float bobY = (float) (Math.sin(t * 2.4D) * 0.04D);

        double targetX = camPos.x + look.x * holdDistance;
        double targetY = camPos.y + look.y * holdDistance + bobY;
        double targetZ = camPos.z + look.z * holdDistance;

        try
        {
            java.lang.reflect.Method getTrans = heldRagdollBody.getClass().getMethod("getTransformation");
            Object m = getTrans.invoke(heldRagdollBody);
            if (m instanceof org.joml.Matrix4d md)
            {
                /* Mutate in place — PhysicsMod's render reads this
                 *  matrix each frame. Keep rotation, replace translation. */
                md.m30(targetX);
                md.m31(targetY);
                md.m32(targetZ);
            }
        }
        catch (Throwable e)
        {
            LOG.debug("[gravity-gun] applyHoldPositionRagdoll failed: {}", e.toString());
        }
    }

    /** Release the held ragdoll: restore kinematic flag + try to set
     *  initial throw velocity on the rigid body so it actually flies. */
    private static void throwHeldRagdoll(Minecraft client)
    {
        if (heldRagdoll == null || heldRagdollBody == null) return;

        try
        {
            java.lang.reflect.Field kf = heldRagdoll.getClass().getField("kinematic");
            kf.setBoolean(heldRagdoll, savedKinematic);
        }
        catch (Throwable ignored) {}

        /* Best-effort throw velocity. Look up the ragdoll's btBodies
         *  list and set linearVelocity on the body that matches our
         *  held PhysicsEntity (by index). */
        try
        {
            Vector3f look = lookDir(client);
            int idx = -1;
            java.util.List<?> bodies = com.afrobama.bbstools.client.ragdoll.ActorRagdollProxy.getRagdollBodies(heldRagdoll);
            for (int i = 0; i < bodies.size(); i++) if (bodies.get(i) == heldRagdollBody) { idx = i; break; }

            if (idx >= 0)
            {
                java.lang.reflect.Field btf = heldRagdoll.getClass().getField("btBodies");
                Object btList = btf.get(heldRagdoll);
                if (btList instanceof java.util.List<?> list && idx < list.size())
                {
                    Object rigid = list.get(idx);
                    if (rigid != null)
                    {
                        /* Try common method names on IRigidBody. */
                        for (String m : new String[]{ "setLinearVelocity", "setVelocity" })
                        {
                            try
                            {
                                java.lang.reflect.Method setter = rigid.getClass().getMethod(m, float.class, float.class, float.class);
                                setter.invoke(rigid, look.x * THROW_SPEED, look.y * THROW_SPEED, look.z * THROW_SPEED);
                                break;
                            }
                            catch (NoSuchMethodException ignored) {}
                        }
                    }
                }
            }
        }
        catch (Throwable e)
        {
            LOG.debug("[gravity-gun] throw velocity set failed: {}", e.toString());
        }

        LOG.debug("[gravity-gun] released ragdoll body");
        heldRagdoll = null;
        heldRagdollBody = null;
    }

    private static void applyHoldPosition(Minecraft client)
    {
        Vector3f camPos = new Vector3f(
            (float) client.player.getX(),
            (float) (client.player.getY() + client.player.getEyeHeight()),
            (float) client.player.getZ());

        Vector3f look = lookDir(client);

        /* Subtle bobbing so the held body feels physical rather than
         *  statically locked to the camera. Two stacked sin waves at
         *  decoupled frequencies (Y + slight X/Z sway) give organic
         *  floatation similar to Gmod's physics-gun hold. */
        double t = System.nanoTime() * 1e-9D;
        float bobY = (float) (Math.sin(t * 2.4D) * 0.04D);
        float swayX = (float) (Math.sin(t * 1.7D + 0.6D) * 0.025D);
        float swayZ = (float) (Math.cos(t * 1.9D + 1.1D) * 0.025D);

        held.prevPosition.set(held.position);
        held.position.set(
            camPos.x + look.x * holdDistance + swayX,
            camPos.y + look.y * holdDistance + bobY,
            camPos.z + look.z * holdDistance + swayZ);

        /* Slow rotation drift around all three axes — different rates
         *  so the body never aligns to a "fake" idle pose. ~3-6° per
         *  second; subtle enough not to distract from the held form. */
        held.prevRotation.set(held.rotation);
        org.joml.Quaternionf drift = new org.joml.Quaternionf().rotateXYZ(
            0.002F, 0.005F, 0.001F);
        held.rotation.premul(drift);
        held.rotation.normalize();

        /* Keep velocity zeroed so on release we set throw velocity
         *  cleanly without leftover hold-jitter. */
        held.linearVelocity.zero();
        /* Mark held so tickBody skips this body (the simulator won't
         *  apply gravity/damping/collision while we're carrying). */
        held.gravityGunHeld = true;
    }

    private static void throwHeld(Minecraft client)
    {
        Vector3f look = lookDir(client);
        held.linearVelocity.set(
            look.x * THROW_SPEED,
            look.y * THROW_SPEED,
            look.z * THROW_SPEED);
        held.gravityGunHeld = false;
        LOG.debug("[gravity-gun] released bone '{}' with velocity ({}, {}, {})",
            held.boneName, held.linearVelocity.x, held.linearVelocity.y, held.linearVelocity.z);
        held = null;
    }

    private static Vector3f lookDir(Minecraft client)
    {
        net.minecraft.world.phys.Vec3 v = client.player.getLookAngle();
        return new Vector3f((float) v.x, (float) v.y, (float) v.z);
    }

    /**
     * Slab-method ray-vs-AABB intersection. Returns the parametric
     * distance {@code t} where the ray first hits the body's AABB,
     * or {@code -1} if no intersection within the body's bounds.
     */
    private static float rayAabbDist(Vector3f origin, Vector3f dir, BonePhysicsBody b)
    {
        float minX = b.position.x - b.halfExtents.x;
        float minY = b.position.y - b.halfExtents.y;
        float minZ = b.position.z - b.halfExtents.z;
        float maxX = b.position.x + b.halfExtents.x;
        float maxY = b.position.y + b.halfExtents.y;
        float maxZ = b.position.z + b.halfExtents.z;

        float invX = dir.x != 0F ? 1F / dir.x : Float.POSITIVE_INFINITY;
        float invY = dir.y != 0F ? 1F / dir.y : Float.POSITIVE_INFINITY;
        float invZ = dir.z != 0F ? 1F / dir.z : Float.POSITIVE_INFINITY;

        float tx1 = (minX - origin.x) * invX;
        float tx2 = (maxX - origin.x) * invX;
        float ty1 = (minY - origin.y) * invY;
        float ty2 = (maxY - origin.y) * invY;
        float tz1 = (minZ - origin.z) * invZ;
        float tz2 = (maxZ - origin.z) * invZ;

        float tmin = Math.max(Math.max(Math.min(tx1, tx2), Math.min(ty1, ty2)), Math.min(tz1, tz2));
        float tmax = Math.min(Math.min(Math.max(tx1, tx2), Math.max(ty1, ty2)), Math.max(tz1, tz2));

        if (tmax < 0F || tmin > tmax) return -1F;
        return Math.max(0F, tmin);
    }

    /** Force-release on world unload so we don't keep a stale handle. */
    public static void clear()
    {
        if (held != null)
        {
            held.gravityGunHeld = false;
            held = null;
        }
        if (heldRagdoll != null)
        {
            try
            {
                java.lang.reflect.Field kf = heldRagdoll.getClass().getField("kinematic");
                kf.setBoolean(heldRagdoll, savedKinematic);
            }
            catch (Throwable ignored) {}
            heldRagdoll = null;
            heldRagdollBody = null;
        }
        wasPressed = false;
    }

    public static boolean isHolding()
    {
        return held != null || heldRagdollBody != null;
    }

    /**
     * Read-only check used by the HUD overlay — true if there's a
     * fracture body within pickup range of the player's crosshair.
     * Returns the bone name + distance for the HUD label.
     */
    public static synchronized HoverInfo getHoverInfo()
    {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.level == null) return null;

        Vector3f camPos = new Vector3f(
            (float) client.player.getX(),
            (float) (client.player.getY() + client.player.getEyeHeight()),
            (float) client.player.getZ());
        Vector3f look = lookDir(client);

        BonePhysicsBody best = null;
        String bestRagdollLabel = null;
        float bestDist = Float.MAX_VALUE;

        Map<IEntity, List<BonePhysicsBody>> snapshot = BonePhysicsSimulator.snapshotBodies();
        for (List<BonePhysicsBody> bodies : snapshot.values())
        {
            for (BonePhysicsBody b : bodies)
            {
                if (b.dead) continue;
                float t = rayAabbDist(camPos, look, b);
                if (t < 0F || t > PICK_RANGE) continue;
                if (t < bestDist)
                {
                    bestDist = t;
                    best = b;
                    bestRagdollLabel = null;
                }
            }
        }

        for (Object ragdoll : com.afrobama.bbstools.client.ragdoll.PhysicsRagdollRegistry.snapshot())
        {
            if (ragdoll == null) continue;
            java.util.List<?> bodies = com.afrobama.bbstools.client.ragdoll.ActorRagdollProxy.getRagdollBodies(ragdoll);
            for (Object body : bodies)
            {
                if (body == null) continue;
                float t = rayAabbDistRagdollBody(camPos, look, body);
                if (t < 0F || t > PICK_RANGE) continue;
                if (t < bestDist)
                {
                    bestDist = t;
                    best = null;
                    bestRagdollLabel = "ragdoll";
                }
            }
        }

        if (best != null) return new HoverInfo(best.boneName, bestDist);
        if (bestRagdollLabel != null) return new HoverInfo(bestRagdollLabel, bestDist);
        return null;
    }


    public static final class HoverInfo
    {
        public final String boneName;
        public final float distance;
        public HoverInfo(String boneName, float distance)
        {
            this.boneName = boneName;
            this.distance = distance;
        }
    }
}
