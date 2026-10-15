package com.afrobama.bbstools.client.ragdoll;

import mchorse.bbs_mod.forms.entities.IEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityType; /* EntityTypes */
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Manages proxy entities (invisible Villagers) used to create
 * PhysicsMod ragdolls for BBS BOBJ-modelled actors. Lifecycle:
 *
 * <ol>
 *   <li>{@link #spawnFor(IEntity)} — spawns an invisible Villager at
 *       the BBS actor's world position, kills it next tick. The
 *       Villager is marked via an entity → BBS-actor map so when
 *       PhysicsMod's ragdoll-creation hook fires we can recognise
 *       it as ours.</li>
 *   <li>{@link BBSPhysicsModBridge}'s reflection-proxy hook calls
 *       {@link #onRagdollCreated(Object, Object)} when PhysicsMod
 *       creates a ragdoll for one of our proxy entities. We store
 *       the ragdoll handle keyed by BBS actor.</li>
 *   <li>Each render tick, the BBSArmature bone-matrix override mixin
 *       calls {@link #applyOverrides(IEntity, Object)} which reads
 *       PhysicsEntity transforms out of the captured ragdoll and
 *       writes them onto the actor's bone matrices via name mapping
 *       (head→head, body→body, etc.).</li>
 *   <li>{@link #clearActor(IEntity)} — release the ragdoll and remove
 *       any leftover proxy entities. Called on rewind / clip-end /
 *       world-unload.</li>
 * </ol>
 *
 * <h2>Multiplayer</h2>
 * <p>Spawning entities client-side only works in singleplayer (via
 * the integrated server) or with operator permissions on a real
 * server. Otherwise the proxy spawn fails silently and the BBS
 * actor remains intact. No side effects.</p>
 */
public final class ActorRagdollProxy
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/ragdoll/proxy");

    /** Proxy MC entity → BBS actor that requested it. Used by the
     *  RagdollHook to know which BBS actor to bind the captured
     *  ragdoll to. IdentityHashMap because Entity.equals is
     *  reference-only and we want exact matches. */
    private static final Map<Object, IEntity> PROXY_TO_ACTOR = new IdentityHashMap<>();

    /** BBS actor → captured Ragdoll instance (PhysicsMod's
     *  net.diebuddies.physics.ragdoll.Ragdoll). Stored as Object so
     *  this class loads even without PhysicsMod present. */
    private static final Map<IEntity, Object> ACTOR_TO_RAGDOLL = new HashMap<>();

    private ActorRagdollProxy()
    {
    }

    /**
     * Spawn an invisible Villager at the BBS actor's position and
     * schedule its death so PhysicsMod's death-renderer creates a
     * ragdoll. Returns true on success, false if PhysicsMod isn't
     * loaded or the spawn failed.
     */
    public static boolean spawnFor(IEntity bbsActor)
    {
        if (bbsActor == null) return false;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) return false;

        try
        {
            Level world = mc.level;
            Villager proxy = new Villager(EntityType/*s*/.VILLAGER, world);
            Vec3 pos = new Vec3(bbsActor.getX(), bbsActor.getY(), bbsActor.getZ());
            proxy.setPos(pos.x, pos.y, pos.z);
            proxy.setInvisible(true);
            proxy.setSilent(true);

            /* Mark proxy → actor BEFORE spawning so the RagdollHook
             *  can identify it as soon as PhysicsMod creates the
             *  ragdoll. */
            synchronized (PROXY_TO_ACTOR) { PROXY_TO_ACTOR.put(proxy, bbsActor); }

            /* Try the integrated-server spawn path; if we're a
             *  client-only world this fails silently. */
            world.addFreshEntity(proxy);

            /* Kill scheduled to next tick — PhysicsMod's death
             *  renderer fires on the death animation tick which
             *  triggers RagdollMapper.map() which fires our hook.
             *
             * ⚠️ 26.2: kill() verlangt jetzt eine ServerLevel. Damit wird sichtbar, was der
             * Klassendoc oben ohnehin sagt: dieser Weg traegt nur im Einzelspieler. Der Proxy
             * haengt im CLIENT-Level, und die Schadensverarbeitung lief auch vorher rein
             * serverseitig — das parameterlose kill() der 1.21.1 verpuffte auf dem Client
             * genauso, nur ohne es zuzugeben. Ohne eingebauten Server gibt es keine
             * ServerLevel und damit keinen Kill; das wird gemeldet statt still uebersprungen. */
            mc.execute(() -> {
                try
                {
                    MinecraftServer server = mc.getSingleplayerServer();
                    ServerLevel serverLevel = server == null ? null : server.getLevel(world.dimension());

                    if (serverLevel == null)
                    {
                        LOG.debug("proxy kill skipped: no integrated server (client-only world)");

                        return;
                    }

                    proxy.kill(serverLevel);
                }
                catch (Throwable t)
                {
                    LOG.debug("proxy kill failed: {}", t.toString());
                }
            });

            LOG.info("[ragdoll-proxy] spawned + scheduled kill of villager at ({}, {}, {}) for actor {}",
                pos.x, pos.y, pos.z, bbsActor.getClass().getSimpleName());
            return true;
        }
        catch (Throwable t)
        {
            LOG.warn("Proxy villager spawn failed: {}", t.toString());
            return false;
        }
    }

    /**
     * Called by {@link BBSPhysicsModBridge}'s hook when PhysicsMod's
     * RagdollMapper.map() fires. If the entity is one of our marked
     * proxies, we capture the ragdoll for later read-back.
     */
    public static void onRagdollCreated(Object ragdoll, Object entity)
    {
        IEntity bbsActor;
        synchronized (PROXY_TO_ACTOR) { bbsActor = PROXY_TO_ACTOR.get(entity); }
        if (bbsActor == null) return;

        synchronized (ACTOR_TO_RAGDOLL) { ACTOR_TO_RAGDOLL.put(bbsActor, ragdoll); }

        /* Phase 3: hide the villager-ragdoll's visible render so only
         *  the BBS actor (with overridden bone matrices) is visible.
         *  Each PhysicsEntity has a `models` list that PhysicsMod's
         *  renderer iterates; clearing it leaves the physics intact
         *  (PhysX rigid bodies are separate) while removing the
         *  visual. */
        try
        {
            java.util.List<?> bodies = getRagdollBodies(ragdoll);
            for (Object body : bodies)
            {
                if (body == null) continue;
                try
                {
                    java.lang.reflect.Field modelsField = body.getClass().getField("models");
                    Object models = modelsField.get(body);
                    if (models instanceof java.util.List<?> list)
                    {
                        list.clear();
                    }
                }
                catch (Throwable ignored) { /* per-body failure shouldn't break others */ }
            }
            LOG.info("[ragdoll-proxy] cleared models on {} bodies — villager render hidden", bodies.size());
        }
        catch (Throwable t)
        {
            LOG.debug("[ragdoll-proxy] failed to clear ragdoll body models: {}", t.toString());
        }

        LOG.info("[ragdoll-proxy] captured ragdoll for actor {} (entity: {})",
            bbsActor.getClass().getSimpleName(),
            entity == null ? "null" : entity.getClass().getSimpleName());
    }

    /** Returns the captured Ragdoll for the given actor, or null. */
    public static Object getRagdoll(IEntity bbsActor)
    {
        synchronized (ACTOR_TO_RAGDOLL) { return ACTOR_TO_RAGDOLL.get(bbsActor); }
    }

    /** Read PhysicsMod's Ragdoll.bodies via reflection — returns
     *  List of PhysicsEntity, each with getTransformation() and
     *  getRotation(). Exposed for the override mixin / debug. */
    public static java.util.List<?> getRagdollBodies(Object ragdoll)
    {
        if (ragdoll == null) return java.util.Collections.emptyList();
        try
        {
            java.lang.reflect.Field bodiesField = ragdoll.getClass().getField("bodies");
            Object bodies = bodiesField.get(ragdoll);
            if (bodies instanceof java.util.List<?> list) return list;
        }
        catch (Throwable t)
        {
            LOG.debug("getRagdollBodies failed: {}", t.toString());
        }
        return java.util.Collections.emptyList();
    }

    public static void clearActor(IEntity bbsActor)
    {
        if (bbsActor == null) return;
        synchronized (ACTOR_TO_RAGDOLL) { ACTOR_TO_RAGDOLL.remove(bbsActor); }
        synchronized (PROXY_TO_ACTOR)
        {
            PROXY_TO_ACTOR.entrySet().removeIf(e -> e.getValue() == bbsActor);
        }
    }

    public static void clearAll()
    {
        synchronized (ACTOR_TO_RAGDOLL) { ACTOR_TO_RAGDOLL.clear(); }
        synchronized (PROXY_TO_ACTOR) { PROXY_TO_ACTOR.clear(); }
    }

    public static int liveProxyCount()
    {
        synchronized (PROXY_TO_ACTOR) { return PROXY_TO_ACTOR.size(); }
    }

    public static int capturedRagdollCount()
    {
        synchronized (ACTOR_TO_RAGDOLL) { return ACTOR_TO_RAGDOLL.size(); }
    }

    /**
     * Default villager-cuboid → BBS-bone name mapping. PhysicsMod's
     * villager ragdoll typically populates Ragdoll.bodies in this
     * order: [head, body, rightArm, leftArm, rightLeg, leftLeg].
     * Common BBS Steve-like rigs name bones similarly.
     *
     * <p>If the user's BOBJ uses different names, they can override
     * this via a future per-actor config. Names are tested
     * case-insensitively with simple aliases.</p>
     */
    private static final String[] DEFAULT_BONE_MAP = {
        "head", "body", "arm_right", "arm_left", "leg_right", "leg_left"
    };

    private static final String[][] BONE_ALIASES = {
        { "head" },
        { "body", "torso", "chest" },
        { "arm_right", "right_arm", "rightarm", "armR" },
        { "arm_left", "left_arm", "leftarm", "armL" },
        { "leg_right", "right_leg", "rightleg", "legR" },
        { "leg_left", "left_leg", "leftleg", "legL" }
    };

    /**
     * Per render frame: read each captured Ragdoll's PhysicsEntity
     * transforms and apply them as bone-matrix overrides on the BBS
     * actor's BOBJArmature. Called by the
     * BOBJArmatureFractureOverrideMixin after the existing
     * BonePhysicsSimulator pass.
     *
     * @param actor     the BBS actor being rendered (matched against
     *                  the captured ragdoll registry)
     * @param armature  BBS armature with .matrices[] to override
     */
    public static void applyOverrides(mchorse.bbs_mod.forms.entities.IEntity actor,
                                      mchorse.bbs_mod.bobj.BOBJArmature armature)
    {
        if (actor == null || armature == null || armature.matrices == null) return;

        Object ragdoll = getRagdoll(actor);
        if (ragdoll == null) return;

        java.util.List<?> bodies = getRagdollBodies(ragdoll);
        if (bodies.isEmpty()) return;

        /* Camera-relative origin so the override matrices produce
         *  small floats stable for GPU. */
        double actorX = actor.getX();
        double actorY = actor.getY();
        double actorZ = actor.getZ();

        int n = Math.min(bodies.size(), DEFAULT_BONE_MAP.length);
        for (int i = 0; i < n; i++)
        {
            Object body = bodies.get(i);
            if (body == null) continue;

            int boneIdx = findBoneIndex(armature, BONE_ALIASES[i]);
            if (boneIdx < 0 || boneIdx >= armature.matrices.length) continue;

            org.joml.Matrix4f override = readBodyTransform(body, actorX, actorY, actorZ);
            if (override != null)
            {
                armature.matrices[boneIdx] = override;
            }
        }
    }

    /** Find the bone index in the armature whose name matches any
     *  alias (case-insensitive). Returns -1 if none match. */
    private static int findBoneIndex(mchorse.bbs_mod.bobj.BOBJArmature armature, String[] aliases)
    {
        if (armature.orderedBones == null) return -1;
        for (mchorse.bbs_mod.bobj.BOBJBone bone : armature.orderedBones)
        {
            if (bone == null || bone.name == null) continue;
            for (String alias : aliases)
            {
                if (bone.name.equalsIgnoreCase(alias)) return bone.index;
            }
        }
        return -1;
    }

    /** Read PhysicsEntity.getTransformation() (Matrix4d) via
     *  reflection and convert to a BBS-shaped Matrix4f in actor-
     *  relative coords. Returns null if reflection fails. */
    private static org.joml.Matrix4f readBodyTransform(Object body, double actorX, double actorY, double actorZ)
    {
        try
        {
            java.lang.reflect.Method getTrans = body.getClass().getMethod("getTransformation");
            Object m = getTrans.invoke(body);
            if (!(m instanceof org.joml.Matrix4d md)) return null;

            /* Strip world translation, replace with actor-relative
             *  translation so BBS's downstream T(actor) * M cancels
             *  out and the body appears at its world position. */
            org.joml.Matrix4f mf = new org.joml.Matrix4f();
            mf.set((float) md.m00(), (float) md.m01(), (float) md.m02(), (float) md.m03(),
                   (float) md.m10(), (float) md.m11(), (float) md.m12(), (float) md.m13(),
                   (float) md.m20(), (float) md.m21(), (float) md.m22(), (float) md.m23(),
                   (float) (md.m30() - actorX),
                   (float) (md.m31() - actorY),
                   (float) (md.m32() - actorZ),
                   (float) md.m33());
            return mf;
        }
        catch (Throwable t)
        {
            return null;
        }
    }

}
