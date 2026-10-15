package com.afrobama.bbstools.ragdoll;

import org.joml.Vector3f;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks per-actor {@link ActorRagdoll} instances and runs verlet physics
 * each server tick. Single-instance (static) registry — Photopea-equivalent
 * to a global "list of objects in motion".
 *
 * <p>Lifecycle: {@link #start} captures the actor's current bone positions
 * and creates an {@link ActorRagdoll}; {@link #tick} runs physics on all
 * active ragdolls; {@link #stop} removes one. {@link #has} lets the render
 * pipeline check whether to apply ragdoll positions instead of normal pose.</p>
 *
 * <p>Pose override during render is delegated to a separate render hook
 * that consults this runtime via {@link #has} and {@link #get}.</p>
 */
public final class ActorRagdollRuntime
{
    private static final Map<UUID, ActorRagdoll> ACTIVE = new ConcurrentHashMap<>();

    private ActorRagdollRuntime()
    {
    }

    public static boolean has(UUID actorUuid)
    {
        if (actorUuid == null) return false;
        ActorRagdoll r = ACTIVE.get(actorUuid);
        return r != null && r.active;
    }

    public static ActorRagdoll get(UUID actorUuid)
    {
        if (actorUuid == null) return null;
        return ACTIVE.get(actorUuid);
    }

    public static void register(UUID actorUuid, ActorRagdoll ragdoll)
    {
        if (actorUuid == null || ragdoll == null) return;
        ACTIVE.put(actorUuid, ragdoll);
    }

    public static void stop(UUID actorUuid)
    {
        if (actorUuid == null) return;
        ACTIVE.remove(actorUuid);
    }

    public static void clear()
    {
        ACTIVE.clear();
    }

    public static int activeCount()
    {
        return ACTIVE.size();
    }

    /**
     * Photopea-style "instant ragdoll" entry point. Captures the actor's
     * world position + spawns a 6-bone humanoid verlet skeleton (body /
     * head / left_arm / right_arm / left_leg / right_leg). Initial
     * positions are read directly off the actor's current world transform
     * — so any active animation pose is preserved at the moment of
     * capture (Photopea-equivalent for animations).
     *
     * <p>This is the MVP capture. Phase 4 will replace the template with
     * a per-actor walk of the BOBJ armature, so per-form ragdolls match
     * the actual bone geometry of custom models.</p>
     */
    public static void startTemplate(java.util.UUID actorUuid,
        float worldX, float worldY, float worldZ, float yaw,
        org.joml.Vector3f initialVelocity)
    {
        if (actorUuid == null) return;

        ActorRagdoll ragdoll = new ActorRagdoll();
        ragdoll.groundY = worldY;

        /* Yaw-rotated lateral offsets — the actor's "left" is offset along
         *  the rotated X axis. */
        double yawRad = Math.toRadians(yaw);
        float cosY = (float) Math.cos(yawRad);
        float sinY = (float) Math.sin(yawRad);

        /* Bone offsets in body-local space (Y up, X right, Z forward). */
        float[][] template =
        {
            /* name, parent, dx, dy, dz */
        };

        /* Build template with arrays. */
        addBone(ragdoll, "body",      null,   0.0F, 1.00F,  0.0F, worldX, worldY, worldZ, cosY, sinY);
        addBone(ragdoll, "head",      "body", 0.0F, 1.70F,  0.0F, worldX, worldY, worldZ, cosY, sinY);
        addBone(ragdoll, "left_arm",  "body",-0.30F, 1.40F, 0.0F, worldX, worldY, worldZ, cosY, sinY);
        addBone(ragdoll, "right_arm", "body", 0.30F, 1.40F, 0.0F, worldX, worldY, worldZ, cosY, sinY);
        addBone(ragdoll, "left_leg",  "body",-0.15F, 0.50F, 0.0F, worldX, worldY, worldZ, cosY, sinY);
        addBone(ragdoll, "right_leg", "body", 0.15F, 0.50F, 0.0F, worldX, worldY, worldZ, cosY, sinY);

        /* Inject initial velocity by offsetting prevPosition backwards. */
        if (initialVelocity != null)
        {
            for (ActorRagdoll.BoneNode b : ragdoll.bones.values())
            {
                b.prevPosition.x -= initialVelocity.x;
                b.prevPosition.y -= initialVelocity.y;
                b.prevPosition.z -= initialVelocity.z;
            }
        }

        /* Break symmetry with TINY random per-bone kicks. Earlier
         *  magnitudes (0.06 lateral, 0.08 head) caused limbs to fly out
         *  of the constraint envelope on the cml_ragdoll rig and the
         *  result looked exploded. Smaller kicks keep the rig coherent
         *  while still producing visible per-limb drift through the
         *  ModelGroup translates. */
        java.util.Random rng = new java.util.Random();
        for (ActorRagdoll.BoneNode b : ragdoll.bones.values())
        {
            float kickX = (rng.nextFloat() - 0.5F) * 0.02F;
            float kickY = rng.nextFloat() * 0.015F;
            float kickZ = (rng.nextFloat() - 0.5F) * 0.02F;
            b.prevPosition.x -= kickX;
            b.prevPosition.y -= kickY;
            b.prevPosition.z -= kickZ;
        }
        ActorRagdoll.BoneNode head = ragdoll.get("head");
        if (head != null)
        {
            float kickAngle = (float) (Math.random() * Math.PI * 2D);
            float kickMag = 0.025F;
            head.prevPosition.x -= (float) Math.cos(kickAngle) * kickMag;
            head.prevPosition.z -= (float) Math.sin(kickAngle) * kickMag;
        }

        register(actorUuid, ragdoll);
    }

    private static void addBone(ActorRagdoll ragdoll, String name, String parent,
        float lx, float ly, float lz,
        float originX, float originY, float originZ, float cosY, float sinY)
    {
        /* Yaw-rotate (lx, lz) → world dx, dz. */
        float wx = originX + (lx * cosY + lz * sinY);
        float wy = originY + ly;
        float wz = originZ + (-lx * sinY + lz * cosY);

        org.joml.Vector3f pos = new org.joml.Vector3f(wx, wy, wz);
        float restLen = 0F;

        if (parent != null)
        {
            ActorRagdoll.BoneNode parentNode = ragdoll.get(parent);
            if (parentNode != null)
            {
                restLen = parentNode.position.distance(pos);
            }
        }

        ragdoll.add(name, parent, pos, restLen);
    }

    /**
     * Run one physics tick on every active ragdoll. Verlet integrate each
     * bone, then relax parent-child distance constraints, then ground
     * collision. Auto-cancels ragdolls whose ticksRemaining hits zero.
     */
    public static void tick()
    {
        if (ACTIVE.isEmpty()) return;

        Iterator<Map.Entry<UUID, ActorRagdoll>> it = ACTIVE.entrySet().iterator();

        while (it.hasNext())
        {
            Map.Entry<UUID, ActorRagdoll> entry = it.next();
            ActorRagdoll ragdoll = entry.getValue();

            if (!ragdoll.active)
            {
                it.remove();
                continue;
            }

            stepPhysics(ragdoll);

            /* First-tick diagnostic so user can confirm physics is running. */
            if (!ragdoll.loggedTick)
            {
                ragdoll.loggedTick = true;
                ActorRagdoll.BoneNode body = ragdoll.get("body");
                if (body != null)
                {
                    org.slf4j.LoggerFactory.getLogger("bbs/ragdoll").info(
                        "[ragdoll] tick #1 uuid={} body Δ=({}, {}, {}) groundY={} bones={}",
                        entry.getKey(),
                        String.format("%.4f", body.position.x - body.initialPosition.x),
                        String.format("%.4f", body.position.y - body.initialPosition.y),
                        String.format("%.4f", body.position.z - body.initialPosition.z),
                        ragdoll.groundY,
                        ragdoll.bones.keySet());
                }
            }

            if (ragdoll.ticksRemaining > 0)
            {
                ragdoll.ticksRemaining--;

                if (ragdoll.ticksRemaining == 0)
                {
                    ragdoll.active = false;
                }
            }
        }
    }

    /**
     * Photopea-style render override. Called from {@link mchorse.bbs_mod.cubic.animation.Animator}
     * after pose-tracks + phys-bones + bone-chains + IK have all been applied.
     * Walks the BOBJ armature; for each bone whose name matches a ragdoll bone
     * (currently the 6-bone humanoid template), shifts the bone's transform
     * by the verlet-position delta from the actor's world position.
     *
     * <p>Translation only for MVP — bone rotation overrides come in a follow-up
     * iteration. This produces a "drifting" actor where each ragdoll bone
     * lags / falls under physics while still rendering with its full
     * pose-track animation.</p>
     */
    public static void applyToModel(mchorse.bbs_mod.forms.entities.IEntity entity,
        mchorse.bbs_mod.cubic.IModel model)
    {
        if (entity == null || model == null) return;

        java.util.UUID uuid = null;
        if (entity instanceof mchorse.bbs_mod.forms.entities.MCEntity mc)
        {
            net.minecraft.world.entity.Entity raw = mc.getMcEntity();
            if (raw != null) uuid = raw.getUUID();
        }
        if (uuid == null) return;

        ActorRagdoll ragdoll = ACTIVE.get(uuid);
        if (ragdoll == null || !ragdoll.active) return;

        /* Entry-point diagnostic: fires once per ragdoll so we can see
         *  whether Animator.apply() actually invokes us for this actor.
         *  Logs both BOBJ bones (.bobj rigs) and ModelGroups (.bbs.json
         *  rigs like Grox_villager) so we can see which path is in play. */
        if (!ragdoll.loggedApply)
        {
            ragdoll.loggedApply = true;
            java.util.List<String> bobjBoneNames = new java.util.ArrayList<>();
            for (mchorse.bbs_mod.bobj.BOBJBone b : model.getAllBOBJBones())
            {
                if (b != null && b.name != null) bobjBoneNames.add(b.name);
            }
            java.util.List<String> groupNames = new java.util.ArrayList<>();
            for (mchorse.bbs_mod.cubic.data.model.ModelGroup g : model.getAllGroups())
            {
                if (g != null && g.id != null) groupNames.add(g.id);
            }
            org.slf4j.LoggerFactory.getLogger("bbs/ragdoll").info(
                "[ragdoll] applyToModel ENTRY uuid={} bobj-bones={} groups={} ragdoll-bones={}",
                uuid, bobjBoneNames, groupNames, ragdoll.bones.keySet());
        }

        float ax = (float) entity.getX();
        float ay = (float) entity.getY();
        float az = (float) entity.getZ();

        int matched = 0;

        /* Path A — BOBJ rigs (.bobj). Shift bone.transform.translate. */
        for (mchorse.bbs_mod.bobj.BOBJBone bone : model.getAllBOBJBones())
        {
            if (bone == null || bone.name == null) continue;

            /* Try direct match first, then case-insensitive variants —
             *  custom rigs (e.g. Grox Villager) use Capitalized bone names
             *  like "Body", "Left_arm" while the template stores
             *  lowercase. */
            ActorRagdoll.BoneNode node = ragdoll.get(bone.name);
            if (node == null) node = findBoneCaseInsensitive(ragdoll, bone.name);
            if (node == null) continue;

            /* DELTA from rest, not absolute. transform.translate is
             *  bone-local relative-to-rest-pose; using absolute world coords
             *  here would teleport bones into outer space. */
            float dx = (node.position.x - node.initialPosition.x) * 16F;
            float dy = (node.position.y - node.initialPosition.y) * 16F;
            float dz = (node.position.z - node.initialPosition.z) * 16F;

            bone.transform.translate.set(dx, dy, dz);
            matched++;
        }

        /* Path B — BBS native cubic rigs (.bbs.json) like Grox_villager.
         *  Two effects per matched group:
         *    1. TRANSLATE relative to body's drift, so the body bone
         *       drives the whole-actor matrix-translate in the renderer
         *       and limbs only carry their RESIDUAL drift away from body.
         *       Earlier double-apply caused the cml_ragdoll explosion.
         *    2. ROTATE per-limb based on the rest→current direction
         *       change relative to parent. Without rotation the bones
         *       only translate and the actor looks like sliding cubes,
         *       not a falling-over body. With rotation each limb visibly
         *       swings/tips around its joint as the verlet constraint
         *       drags it from rest. The body bone uses body→head as
         *       proxy direction since it has no parent in the template. */
        ActorRagdoll.BoneNode bodyNode = ragdoll.get("body");
        float bodyDx = bodyNode == null ? 0F : (bodyNode.position.x - bodyNode.initialPosition.x);
        float bodyDy = bodyNode == null ? 0F : (bodyNode.position.y - bodyNode.initialPosition.y);
        float bodyDz = bodyNode == null ? 0F : (bodyNode.position.z - bodyNode.initialPosition.z);
        ActorRagdoll.BoneNode headNode = ragdoll.get("head");

        org.joml.Vector3f restDir = new org.joml.Vector3f();
        org.joml.Vector3f curDir = new org.joml.Vector3f();
        org.joml.Quaternionf q = new org.joml.Quaternionf();
        org.joml.Vector3f euler = new org.joml.Vector3f();

        for (mchorse.bbs_mod.cubic.data.model.ModelGroup group : model.getAllGroups())
        {
            if (group == null || group.id == null) continue;

            ActorRagdoll.BoneNode node = ragdoll.get(group.id);
            if (node == null) node = findBoneCaseInsensitive(ragdoll, group.id);
            if (node == null) continue;

            /* Translate. */
            float dx = ((node.position.x - node.initialPosition.x) - bodyDx) * 16F;
            float dy = ((node.position.y - node.initialPosition.y) - bodyDy) * 16F;
            float dz = ((node.position.z - node.initialPosition.z) - bodyDz) * 16F;

            group.current.translate.set(
                group.initial.translate.x + dx,
                group.initial.translate.y + dy,
                group.initial.translate.z + dz);

            /* Rotate. Pick the parent for direction sampling: real ragdoll
             *  parent for limbs, body→head proxy for body bone. */
            ActorRagdoll.BoneNode pivotParent = null;
            ActorRagdoll.BoneNode pivotChild = null;

            if (node.parent != null)
            {
                ActorRagdoll.BoneNode p = ragdoll.get(node.parent);
                if (p != null)
                {
                    pivotParent = p;
                    pivotChild = node;
                }
            }
            else if (node == bodyNode && headNode != null)
            {
                /* Body bone uses body→head deviation as torso tilt. */
                pivotParent = bodyNode;
                pivotChild = headNode;
            }

            if (pivotParent != null && pivotChild != null)
            {
                restDir.set(
                    pivotChild.initialPosition.x - pivotParent.initialPosition.x,
                    pivotChild.initialPosition.y - pivotParent.initialPosition.y,
                    pivotChild.initialPosition.z - pivotParent.initialPosition.z);
                curDir.set(
                    pivotChild.position.x - pivotParent.position.x,
                    pivotChild.position.y - pivotParent.position.y,
                    pivotChild.position.z - pivotParent.position.z);

                if (restDir.lengthSquared() > 1e-6F && curDir.lengthSquared() > 1e-6F)
                {
                    restDir.normalize();
                    curDir.normalize();
                    q.identity().rotationTo(restDir, curDir);
                    q.getEulerAnglesXYZ(euler);

                    /* Joint limits: scale rotation down (real ragdoll
                     *  limbs don't snap to extremes) AND clamp per-axis
                     *  to ±45° so the rig doesn't dismember itself. The
                     *  raw rotationTo can produce 180° flips for nearly-
                     *  opposite vectors, which look like gore. */
                    float scale = 0.5F;
                    float maxDeg = 45F;

                    float rx = (float) Math.toDegrees(euler.x) * scale;
                    float ry = (float) Math.toDegrees(euler.y) * scale;
                    float rz = (float) Math.toDegrees(euler.z) * scale;

                    if (rx > maxDeg) rx = maxDeg; else if (rx < -maxDeg) rx = -maxDeg;
                    if (ry > maxDeg) ry = maxDeg; else if (ry < -maxDeg) ry = -maxDeg;
                    if (rz > maxDeg) rz = maxDeg; else if (rz < -maxDeg) rz = -maxDeg;

                    /* ICubicRenderer applies rotate.z, rotate.y, rotate.x in
                     *  that order with MathUtils.toRad — values are degrees. */
                    group.current.rotate.set(
                        group.initial.rotate.x + rx,
                        group.initial.rotate.y + ry,
                        group.initial.rotate.z + rz);
                }
            }

            matched++;
        }

        /* Log first frame so user can see what matched. */
        if (matched > 0 && !ragdoll.loggedMatch)
        {
            ragdoll.loggedMatch = true;
            org.slf4j.LoggerFactory.getLogger("bbs/ragdoll").info(
                "[/ragdoll] applying physics override to {} bones on actor {}", matched, uuid);
        }
    }

    private static ActorRagdoll.BoneNode findBoneCaseInsensitive(ActorRagdoll ragdoll, String boneName)
    {
        String lower = boneName.toLowerCase(java.util.Locale.ROOT);
        for (java.util.Map.Entry<String, ActorRagdoll.BoneNode> e : ragdoll.bones.entrySet())
        {
            if (e.getKey().equalsIgnoreCase(boneName)) return e.getValue();
        }
        /* Try aliases — Photopea-equivalent fallback: map common BBS bone names. */
        switch (lower)
        {
            case "body":          return any(ragdoll, "Body", "body", "torso", "Torso", "chest", "Chest");
            case "head":          return any(ragdoll, "Head", "head");
            case "left_arm":
            case "leftarm":       return any(ragdoll, "Left_arm", "left_arm", "LeftArm", "leftArm", "arm_l", "L_arm");
            case "right_arm":
            case "rightarm":      return any(ragdoll, "Right_arm", "right_arm", "RightArm", "rightArm", "arm_r", "R_arm");
            case "left_leg":
            case "leftleg":       return any(ragdoll, "Left_leg", "left_leg", "LeftLeg", "leftLeg", "leg_l", "L_leg");
            case "right_leg":
            case "rightleg":      return any(ragdoll, "Right_leg", "right_leg", "RightLeg", "rightLeg", "leg_r", "R_leg");
        }
        return null;
    }

    private static ActorRagdoll.BoneNode any(ActorRagdoll ragdoll, String... names)
    {
        for (String n : names)
        {
            ActorRagdoll.BoneNode b = ragdoll.bones.get(n);
            if (b != null) return b;
        }
        return null;
    }

    private static final Vector3f tmpDelta = new Vector3f();

    private static void stepPhysics(ActorRagdoll ragdoll)
    {
        /* Verlet integrate. */
        for (ActorRagdoll.BoneNode bone : ragdoll.bones.values())
        {
            float vx = (bone.position.x - bone.prevPosition.x) * ragdoll.damping;
            float vy = (bone.position.y - bone.prevPosition.y) * ragdoll.damping;
            float vz = (bone.position.z - bone.prevPosition.z) * ragdoll.damping;

            bone.prevPosition.set(bone.position);

            bone.position.x += vx + ragdoll.gravity.x;
            bone.position.y += vy + ragdoll.gravity.y;
            bone.position.z += vz + ragdoll.gravity.z;
        }

        /* Constraint relaxation: keep parent-child rest distances. Multiple
         *  iterations converge to stiffer chains. */
        for (int iter = 0; iter < ragdoll.constraintIterations; iter++)
        {
            for (ActorRagdoll.BoneNode bone : ragdoll.bones.values())
            {
                if (bone.parent == null) continue;
                ActorRagdoll.BoneNode parent = ragdoll.get(bone.parent);
                if (parent == null) continue;

                tmpDelta.set(bone.position).sub(parent.position);
                float len = tmpDelta.length();
                if (len < 1e-5F) continue;

                float diff = (len - bone.restLength) / len;
                /* Move both halfway towards rest length — equal-mass assumption. */
                tmpDelta.mul(diff * 0.5F);

                bone.position.sub(tmpDelta);
                parent.position.add(tmpDelta);
            }
        }

        /* Ground clamp at the actor's feet level. Letting bones below
         *  their REST y (the previous "anti-noclip" rule) blocked the
         *  fall entirely — body's restY = worldY + 1.0 sat above ground,
         *  so any gravity step was reverted on the same tick and Δ
         *  stayed 0. Bones may dip slightly below groundY between gravity
         *  step and clamp; that's fine for visible motion. Per-bone
         *  collision heights remain a follow-up. */
        for (ActorRagdoll.BoneNode bone : ragdoll.bones.values())
        {
            if (bone.position.y < ragdoll.groundY)
            {
                bone.position.y = ragdoll.groundY;
                bone.prevPosition.y = bone.position.y + 0.02F;
            }
        }
    }

}
