package com.afrobama.bbstools.client.fracture;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reflection-only bridge for spawning PhysicsMod-Pro physics bodies
 * from BBSTools. Uses PhysicsMod's high-level
 * {@code PhysicsWorld.addBlockParticle(List&lt;Mesh&gt;, PhysicsEntity,
 * List&lt;IRigidBody&gt;)} API which handles convex hull cooking
 * internally — much fewer moving parts than reaching into PhysX
 * directly.
 *
 * <h2>Spawn flow</h2>
 * <ol>
 *   <li>Get the {@code PhysicsWorld} from {@code PhysicsMod.instances}
 *       (a static {@code Map&lt;MC World, PhysicsMod&gt;}). One
 *       PhysicsWorld per MC world.</li>
 *   <li>Build a {@code Mesh} object per bone with vertex positions
 *       set on its public {@code positions} field.</li>
 *   <li>Construct a {@code PhysicsEntity(Type.BLOCK, world)} as the
 *       carrier object that ties the meshes together.</li>
 *   <li>Call {@code physicsWorld.addBlockParticle(meshes, entity,
 *       output)} — PhysicsMod cooks the convex hulls + spawns one
 *       {@code IRigidBody} per mesh and populates the output list.</li>
 * </ol>
 *
 * <h2>Despawn</h2>
 * {@code PhysicsWorld.removeBody(IRigidBody)} per spawned body.
 * Idempotent — safe to call multiple times.
 *
 * <h2>Soft-dep</h2>
 * If the {@code physicsmod} mod isn't loaded, init logs and stays
 * disabled. All spawn calls return null. No runtime crash.
 */
public final class FracturePhysicsBridge
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/fracture/physicsbridge");

    private static volatile boolean initialized;
    private static volatile boolean available;

    /* === Reflective handles populated by init() === */
    private static Field physicsModInstancesField;          /* static Map<World, PhysicsMod> */
    private static Field physicsModWorldField;              /* PhysicsMod.physicsWorld */

    private static Class<?> meshCls;
    private static Constructor<?> meshCtor;
    private static Field meshPositionsField;
    private static Field meshIndicesField;

    private static Class<?> physicsEntityCls;
    private static Constructor<?> physicsEntityCtor;
    private static Object physicsEntityTypeBlock;

    private static Method physicsWorldAddBlockParticle;     /* (List<Mesh>, PhysicsEntity, List<IRigidBody>) */
    private static Method physicsWorldAddBlockParticleBox;  /* (PhysicsEntity) — requires entity.models pre-built */
    private static Method physicsWorldRemoveBody;           /* (IRigidBody) */
    private static Method particleSpawnerServerBlock;       /* (BlockState, World, double[6]) — simplest high-level */

    /* Smoke API: PhysicsMod's volumetric realistic-looking smoke is a
     *  domain on PhysicsWorld. We expose spawnParticle so the simulator
     *  can replace the vanilla SMOKE particle in its trail + impact-
     *  dust paths with proper smoke domain particles. */
    private static Method physicsWorldGetSmokeDomain;       /* () → SmokeDomain */
    private static Method smokeDomainSpawnParticle;         /* (double, double, double, float density) */
    private static volatile boolean smokeAvailable;

    /** Live bodies tracked for cleanup. */
    private static final Map<Object, Boolean> LIVE_BODIES = new IdentityHashMap<>();

    private FracturePhysicsBridge()
    {
    }

    public static synchronized void init()
    {
        if (initialized) return;
        initialized = true;

        if (!FabricLoader.getInstance().isModLoaded("physicsmod"))
        {
            LOG.info("PhysicsMod not loaded — fracture spawning disabled");
            return;
        }

        try
        {
            resolveHandles();
            available = true;
            LOG.info("FracturePhysicsBridge initialised — addBlockParticle path active");
        }
        catch (Throwable t)
        {
            available = false;
            LOG.warn("FracturePhysicsBridge init failed — fracture spawning disabled. Reason: {}", t.toString());
        }
    }

    private static void resolveHandles() throws Exception
    {
        Class<?> physicsModCls = Class.forName("net.diebuddies.physics.PhysicsMod");
        physicsModInstancesField = physicsModCls.getField("instances");
        physicsModWorldField = physicsModCls.getField("physicsWorld");

        meshCls = Class.forName("net.diebuddies.physics.Mesh");
        meshCtor = meshCls.getDeclaredConstructor();
        meshCtor.setAccessible(true);
        meshPositionsField = meshCls.getField("positions");
        meshIndicesField = meshCls.getField("indices");

        physicsEntityCls = Class.forName("net.diebuddies.physics.PhysicsEntity");
        Class<?> physicsEntityTypeCls = Class.forName("net.diebuddies.physics.PhysicsEntity$Type");
        physicsEntityTypeBlock = Enum.valueOf((Class) physicsEntityTypeCls, "BLOCK");

        /* PhysicsEntity ctor: (Type, Object) — second arg is context;
         *  we pass the World instance which is what BlockPhysics does. */
        physicsEntityCtor = physicsEntityCls.getConstructor(physicsEntityTypeCls, Object.class);

        Class<?> physicsWorldCls = Class.forName("net.diebuddies.physics.PhysicsWorld");

        /* Find addBlockParticle(List, PhysicsEntity, List) — 3-arg
         *  version. Other overloads have List/Mesh args of different
         *  arity; we want the simple 3-arg for our use case. */
        for (Method m : physicsWorldCls.getDeclaredMethods())
        {
            if (!"addBlockParticle".equals(m.getName())) continue;
            Class<?>[] params = m.getParameterTypes();
            if (params.length == 3
                && params[0] == List.class
                && params[1] == physicsEntityCls
                && params[2] == List.class)
            {
                physicsWorldAddBlockParticle = m;
                physicsWorldAddBlockParticle.setAccessible(true);
                break;
            }
        }
        if (physicsWorldAddBlockParticle == null)
        {
            throw new NoSuchMethodException("PhysicsWorld.addBlockParticle(List, PhysicsEntity, List)");
        }

        /* addBlockParticleBox(PhysicsEntity) — kept for future use but
         *  REQUIRES entity.models pre-populated (NPE on .mesh.offset
         *  otherwise). Not used by spawnSimpleBox anymore. */
        for (Method m : physicsWorldCls.getDeclaredMethods())
        {
            if (!"addBlockParticleBox".equals(m.getName())) continue;
            if (m.getParameterCount() != 1) continue;
            if (m.getParameterTypes()[0] != physicsEntityCls) continue;
            physicsWorldAddBlockParticleBox = m;
            physicsWorldAddBlockParticleBox.setAccessible(true);
            break;
        }

        /* ParticleSpawner.spawnServerBlockPhysicsParticle — high-level
         *  helper that internally builds Model+Mesh+Texture from a
         *  BlockState. The "Server" prefix is misleading — this runs
         *  client-side too (PhysicsMod's particle is purely client visual). */
        try
        {
            Class<?> particleSpawnerCls = Class.forName("net.diebuddies.minecraft.ParticleSpawner");
            for (Method m : particleSpawnerCls.getDeclaredMethods())
            {
                if (!"spawnServerBlockPhysicsParticle".equals(m.getName())) continue;
                if (m.getParameterCount() != 8) continue;
                particleSpawnerServerBlock = m;
                particleSpawnerServerBlock.setAccessible(true);
                break;
            }
        }
        catch (Throwable t)
        {
            LOG.debug("ParticleSpawner not found: {}", t.toString());
        }

        /* removeBody(IRigidBody) — IRigidBody is an interface inside
         *  net.diebuddies.physics; we resolve by name+arity. */
        for (Method m : physicsWorldCls.getDeclaredMethods())
        {
            if (!"removeBody".equals(m.getName())) continue;
            if (m.getParameterCount() != 1) continue;
            physicsWorldRemoveBody = m;
            physicsWorldRemoveBody.setAccessible(true);
            break;
        }
        if (physicsWorldRemoveBody == null)
        {
            throw new NoSuchMethodException("PhysicsWorld.removeBody(IRigidBody)");
        }

        /* Optional: SmokeDomain spawnParticle. Resolved best-effort —
         *  if any handle fails (older PhysicsMod build, refactor) the
         *  bridge falls back silently to vanilla SMOKE particles. */
        try
        {
            Class<?> smokeDomainCls = Class.forName("net.diebuddies.physics.smoke.SmokeDomain");
            physicsWorldGetSmokeDomain = physicsWorldCls.getMethod("getSmokeDomain");
            physicsWorldGetSmokeDomain.setAccessible(true);
            smokeDomainSpawnParticle = smokeDomainCls.getMethod("spawnParticle",
                double.class, double.class, double.class, float.class);
            smokeDomainSpawnParticle.setAccessible(true);
            smokeAvailable = true;
            LOG.info("PhysicsMod SmokeDomain resolved — realistic smoke active");
        }
        catch (Throwable t)
        {
            smokeAvailable = false;
            LOG.info("PhysicsMod SmokeDomain not available ({}). Falling back to vanilla SMOKE particles.",
                t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage());
        }

        LOG.debug("All PhysicsMod fracture reflection handles resolved");
    }

    public static boolean isAvailable()
    {
        return available;
    }

    /**
     * Spawn one physics body from a single bone's vertices at the
     * given world position. Returns an opaque handle for later
     * despawn, or null if the spawn failed.
     */
    public static Object spawnConvex(Vector3f[] verticesLocal,
                                     double worldX, double worldY, double worldZ,
                                     Quaternionf rotation, Vector3f velocity)
    {
        if (!available || verticesLocal == null || verticesLocal.length < 4) return null;

        try
        {
            Level mcWorld = Minecraft.getInstance().level;
            if (mcWorld == null) return null;

            Object physicsWorld = getPhysicsWorld(mcWorld);
            if (physicsWorld == null)
            {
                LOG.debug("No PhysicsWorld for current MC world (PhysicsMod not active here)");
                return null;
            }

            Object mesh = buildMesh(verticesLocal);
            Object entity = buildPhysicsEntity(mcWorld, worldX, worldY, worldZ, rotation, velocity);

            List<Object> output = new ArrayList<>();
            physicsWorldAddBlockParticle.invoke(physicsWorld,
                Collections.singletonList(mesh),
                entity,
                output);

            if (output.isEmpty()) return null;

            Object body = output.get(0);
            synchronized (LIVE_BODIES) { LIVE_BODIES.put(body, Boolean.TRUE); }
            return body;
        }
        catch (Throwable t)
        {
            /* Unwrap InvocationTargetException so the real cause shows
             *  in the log. Most reflection failures come back wrapped
             *  and the wrapper hides the actually-useful message. */
            Throwable cause = t;
            while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
            LOG.warn("spawnConvex failed: [{}] {}", cause.getClass().getSimpleName(), cause.getMessage());
            LOG.warn("Stack trace:", cause);
            return null;
        }
    }

    /**
     * Spawn a simple unit-cube physics body at the given world
     * position via PhysicsMod's {@code addBlockParticleBox} which
     * auto-builds the visual mesh + texture from a default
     * {@code stone} BlockState. Used as a sanity-check spawn that
     * ISOLATES the rendering pipeline from custom mesh data —
     * if this is visible but {@link #spawnConvex} isn't, we know the
     * issue is missing UV/normal/colour data on our custom mesh.
     */
    public static Object spawnSimpleBox(double worldX, double worldY, double worldZ)
    {
        if (!available || particleSpawnerServerBlock == null)
        {
            LOG.debug("spawnSimpleBox unavailable — ParticleSpawner not resolved");
            return null;
        }

        try
        {
            Level mcWorld = Minecraft.getInstance().level;
            if (mcWorld == null) return null;

            net.minecraft.world.level.block.state.BlockState stone = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();

            /* spawnServerBlockPhysicsParticle(BlockState, World,
             *  posX, posY, posZ, velX, velY, velZ). PhysicsMod auto-
             *  builds visual + physics from the block's baked model. */
            particleSpawnerServerBlock.invoke(null,
                stone, mcWorld,
                worldX, worldY, worldZ,
                0D, 0.3D, 0D); /* small upward velocity */

            /* This entry point doesn't return a handle. We track it
             *  via a sentinel object so liveBodyCount reflects activity. */
            Object sentinel = new Object();
            synchronized (LIVE_BODIES) { LIVE_BODIES.put(sentinel, Boolean.TRUE); }
            return sentinel;
        }
        catch (Throwable t)
        {
            Throwable cause = t;
            while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
            LOG.warn("spawnSimpleBox failed: [{}] {}", cause.getClass().getSimpleName(), cause.getMessage());
            LOG.warn("Stack trace:", cause);
            return null;
        }
    }

    /**
     * Spawn a single PhysicsMod block-particle debris chunk at the
     * given world position with the given initial velocity. Returns
     * a sentinel handle so callers can count active debris.
     *
     * <p>Used by the piece-count subdivision feature — a fracture clip
     * with {@code pieceCount &gt; 1} spawns this many chunks PER bone
     * alongside the main bone-body, giving the cinematic "many small
     * pieces flying everywhere" effect. Block type is randomised
     * across a small cinematic-friendly debris set (stone, cobble,
     * dirt, oak log, iron, gravel) so the debris looks like mixed
     * destruction material rather than uniform cubes.</p>
     *
     * <p>Returns null if PhysicsMod isn't loaded or the spawn failed.</p>
     */
    public static Object spawnDebrisChunk(double worldX, double worldY, double worldZ,
                                          double velX, double velY, double velZ,
                                          int debrisIndex)
    {
        if (!available || particleSpawnerServerBlock == null) return null;

        try
        {
            Level mcWorld = Minecraft.getInstance().level;
            if (mcWorld == null) return null;

            net.minecraft.world.level.block.state.BlockState debrisState = pickDebrisState(debrisIndex);

            particleSpawnerServerBlock.invoke(null,
                debrisState, mcWorld,
                worldX, worldY, worldZ,
                velX, velY, velZ);

            Object sentinel = new Object();
            synchronized (LIVE_BODIES) { LIVE_BODIES.put(sentinel, Boolean.TRUE); }
            return sentinel;
        }
        catch (Throwable t)
        {
            Throwable cause = t;
            while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
            LOG.debug("spawnDebrisChunk failed: [{}] {}",
                cause.getClass().getSimpleName(), cause.getMessage());
            return null;
        }
    }

    /**
     * Spawn one debris chunk using a SPECIFIC BlockState — used by the
     * block-shatter feature where the block being shattered determines
     * the debris appearance (stone wall throws stone chunks, oak beam
     * throws oak chunks, etc.). Returns null if the spawn failed or
     * PhysicsMod isn't loaded.
     *
     * <p>Optional blockMode parameter overrides PhysicsMod's global
     * {@code BlockPhysicsType} for the duration of THIS spawn call:
     * "blocky" / "fractured" / "fractured_voxel" / "particles" force
     * those visual styles regardless of user PhysicsMod settings.
     * "default" or null = no override. Original setting is restored
     * via try/finally so a crash mid-spawn doesn't permanently change
     * the user's PhysicsMod config.</p>
     */
    public static Object spawnDebrisChunkWithState(double worldX, double worldY, double worldZ,
                                                   double velX, double velY, double velZ,
                                                   net.minecraft.world.level.block.state.BlockState state,
                                                   String blockMode)
    {
        if (!available || particleSpawnerServerBlock == null || state == null) return null;

        Object savedType = null;
        Object parentSetting = null;
        java.lang.reflect.Field typeField = null;
        boolean overrode = false;

        if (blockMode != null && !"default".equals(blockMode))
        {
            try
            {
                Class<?> typeCls = Class.forName("net.diebuddies.physics.settings.blocks.BlockPhysicsType");
                Object newType = Enum.valueOf((Class) typeCls, blockMode.toUpperCase());

                Class<?> configBlocksCls = Class.forName("net.diebuddies.config.ConfigBlocks");
                java.lang.reflect.Field parentField = configBlocksCls.getField("PARENT_BLOCK_SETTING");
                parentSetting = parentField.get(null);
                if (parentSetting != null)
                {
                    typeField = parentSetting.getClass().getField("type");
                    savedType = typeField.get(parentSetting);
                    typeField.set(parentSetting, newType);
                    overrode = true;
                }
            }
            catch (Throwable t)
            {
                /* If override fails (PhysicsMod API changed, invalid
                 *  enum name, etc.) fall through and spawn with the
                 *  user's normal setting — better than dropping the
                 *  spawn entirely. */
                LOG.debug("blockMode override '{}' failed: {}", blockMode, t.toString());
            }
        }

        try
        {
            Level mcWorld = Minecraft.getInstance().level;
            if (mcWorld == null) return null;

            particleSpawnerServerBlock.invoke(null,
                state, mcWorld,
                worldX, worldY, worldZ,
                velX, velY, velZ);

            Object sentinel = new Object();
            synchronized (LIVE_BODIES) { LIVE_BODIES.put(sentinel, Boolean.TRUE); }
            return sentinel;
        }
        catch (Throwable t)
        {
            Throwable cause = t;
            while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
            LOG.debug("spawnDebrisChunkWithState failed: [{}] {}",
                cause.getClass().getSimpleName(), cause.getMessage());
            return null;
        }
        finally
        {
            /* Always restore the original PhysicsMod block-physics-type
             *  so we don't permanently mutate the user's settings. */
            if (overrode && typeField != null && parentSetting != null)
            {
                try { typeField.set(parentSetting, savedType); }
                catch (Throwable ignored) {}
            }
        }
    }

    /** Backwards-compat overload — no block-mode override. */
    public static Object spawnDebrisChunkWithState(double worldX, double worldY, double worldZ,
                                                   double velX, double velY, double velZ,
                                                   net.minecraft.world.level.block.state.BlockState state)
    {
        return spawnDebrisChunkWithState(worldX, worldY, worldZ, velX, velY, velZ, state, null);
    }

    /* === BBS PhysicsBlockFractureEmitter spawn helpers (reflective) ===
     *
     *  BBS internally has spawnPhysicsModFracture / spawnPhysicsModShards
     *  / spawnPhysicsModParticles which use the PROPER PhysicsMod
     *  spawn pipeline with full position+velocity control and per-call
     *  block-physics-mode. Our previous spawn route (ParticleSpawner.
     *  spawnServerBlockPhysicsParticle) was hardcoded to PARTICLE.
     *  These BBS helpers honor the mode parameter so blocky/fractured/
     *  voxel/particles all work AND respect our directionForce vector. */
    private static volatile boolean bbsEmitterChecked;
    private static volatile java.lang.reflect.Method bbsSpawnFracture;
    private static volatile java.lang.reflect.Method bbsSpawnShards;
    private static volatile java.lang.reflect.Method bbsSpawnParticles;

    private static void resolveBbsEmitterHelpers()
    {
        if (bbsEmitterChecked) return;
        bbsEmitterChecked = true;
        try
        {
            Class<?> cls = Class.forName("mchorse.bbs_mod.forms.renderers.PhysicsBlockFractureEmitterRenderer");
            for (Method m : cls.getDeclaredMethods())
            {
                String n = m.getName();
                if ("spawnPhysicsModFracture".equals(n))  { m.setAccessible(true); bbsSpawnFracture = m; }
                else if ("spawnPhysicsModShards".equals(n))    { m.setAccessible(true); bbsSpawnShards = m; }
                else if ("spawnPhysicsModParticles".equals(n)) { m.setAccessible(true); bbsSpawnParticles = m; }
            }

            /* CRITICAL: trigger the renderer's lazy initPhysicsMod()
             *  static-init path so its private static reflection
             *  handles (physicsModGetInstance, physicsWorldAddBlockParticle,
             *  rigidBodyApplyRandomSpawnForces, etc.) are populated.
             *  Without this the spawn helpers fall back to vanilla
             *  FallingBlockEntity which is INVISIBLE in cinematic use
             *  because it has no block-model rendering. The renderer
             *  normally calls initPhysicsMod() on its first tick when
             *  attached to an emitter form — we don't have that, so
             *  we kick it off ourselves. */
            try
            {
                Method initMethod = cls.getDeclaredMethod("initPhysicsMod");
                initMethod.setAccessible(true);
                initMethod.invoke(null);
                LOG.info("BBS emitter helpers resolved + initPhysicsMod fired");
            }
            catch (Throwable initEx)
            {
                LOG.warn("initPhysicsMod kick failed — block-mode debris may be invisible: {}", initEx.toString());
            }

            if (bbsSpawnFracture == null)
            {
                LOG.info("BBS PhysicsBlockFractureEmitter spawn helpers not available — block-mode override falls back to legacy path");
            }
        }
        catch (Throwable t)
        {
            LOG.info("BBS emitter helpers resolution failed: {}", t.toString());
        }
    }

    /**
     * Spawn a single block debris using BBS's emitter spawn helpers
     * — gives us position + velocity + mode control, unlike the
     * particle-only ParticleSpawner.spawnServerBlockPhysicsParticle.
     *
     * <p>Mode mapping:</p>
     * <ul>
     *   <li>0 / "blocky" → BLOCKY (cube debris)</li>
     *   <li>1 / "fractured" → FRACTURED shards</li>
     *   <li>2 / "fractured_voxel" → FRACTURED voxel-shaped chunks</li>
     *   <li>3 / "particles" → small sparkly PARTICLES</li>
     * </ul>
     *
     * <p>Returns true if the spawn was attempted via BBS's helpers,
     * false if BBS isn't loaded / helpers couldn't resolve, in which
     * case the caller should fall back to the legacy particle-spawn
     * path.</p>
     */
    public static boolean spawnViaBbsEmitter(double worldX, double worldY, double worldZ,
                                             double velX, double velY, double velZ,
                                             net.minecraft.world.level.block.state.BlockState state,
                                             String mode, float physicsScale, int lifetimeTicks,
                                             boolean noInterCollision)
    {
        resolveBbsEmitterHelpers();
        if (bbsSpawnFracture == null) return false;

        try
        {
            Level mcWorld = Minecraft.getInstance().level;
            if (mcWorld == null) return false;

            net.minecraft.world.phys.Vec3 pos = new net.minecraft.world.phys.Vec3(worldX, worldY, worldZ);
            net.minecraft.world.phys.Vec3 vel = new net.minecraft.world.phys.Vec3(velX, velY, velZ);

            /* If noInterCollision: install a thread-local mask override
             *  so PhysicsMod's body-creation mixins
             *  (ConvexRigidBodyMaskMixin / BoxRigidBodyMaskMixin) write
             *  the no-dynamic-collision mask onto entity.physicsMask
             *  BEFORE the PhysX actor's filter is baked. Post-spawn
             *  applyNoCollisionMask retained as a safety net for
             *  legacy paths but the mixin path is the actual fix. */
            byte noDynamicMask = noInterCollision ? resolveNoDynamicMask() : 0;
            boolean needSnapshot = noInterCollision
                || "fractured".equals(mode) || "fractured_voxel".equals(mode);

            boolean spawned = false;
            int bodiesBefore = -1;

            /* try/finally wraps the WHOLE override-set + snapshot + spawn
             *  sequence so a throw anywhere between set() and the spawn
             *  invocation can't leak the thread-local. */
            try
            {
                if (noInterCollision && noDynamicMask != 0)
                {
                    com.afrobama.bbstools.client.fracture.PhysicsMaskOverride.set(noDynamicMask);
                }
                bodiesBefore = needSnapshot ? snapshotBodyCount(mcWorld) : -1;

                /* IMPORTANT: spawn helpers' last param is lifetimeTicks,
                 *  NOT mode. Earlier I passed 0/1 thinking it was a mode
                 *  index; the debris despawned on the first tick →
                 *  invisible. */
                switch (mode == null ? "blocky" : mode)
                {
                    case "particles":
                        if (bbsSpawnParticles != null)
                        {
                            bbsSpawnParticles.invoke(null, mcWorld, state, pos);
                            spawned = true;
                        }
                        break;
                    case "fractured":
                        if (bbsSpawnShards != null)
                        {
                            bbsSpawnShards.invoke(null, mcWorld, state, pos, vel, false, physicsScale, lifetimeTicks);
                            spawned = true;
                        }
                        break;
                    case "fractured_voxel":
                        if (bbsSpawnShards != null)
                        {
                            bbsSpawnShards.invoke(null, mcWorld, state, pos, vel, true, physicsScale, lifetimeTicks);
                            spawned = true;
                        }
                        break;
                    case "blocky":
                    default:
                        bbsSpawnFracture.invoke(null, mcWorld, state, pos, vel, physicsScale, lifetimeTicks);
                        spawned = true;
                        break;
                }
            }
            finally
            {
                if (noInterCollision)
                {
                    com.afrobama.bbstools.client.fracture.PhysicsMaskOverride.set(null);
                }
            }

            if (spawned && noInterCollision && bodiesBefore >= 0)
            {
                /* Safety-net: also write the mask on the freshly-spawned
                 *  PhysicsEntity wrappers. The PhysX filter is already
                 *  baked from the mixin path, but this keeps the
                 *  PhysicsEntity.physicsMask field consistent for any
                 *  later code that re-reads it. */
                applyNoCollisionMask(mcWorld, bodiesBefore);
            }

            /* For FRACTURED / FRACTURED_VOXEL only — BBS's
             *  spawnPhysicsModShards uses applyVelocityToNewShards which
             *  silently SKIPS non-PxRigidDynamic bodies (they fail the
             *  instanceof check). Some shards in PhysicsMod's fracture
             *  pattern are kinematic anchors → they stay stuck at the
             *  spawn point with zero velocity (= user-reported "Partikel
             *  bleiben am startpunkt stecken bei den anderen block modes").
             *
             *  BLOCKY mode's spawnPhysicsModFracture has a fallback at
             *  bytecode line 225 that calls rigidBodyApplyRandomSpawnForces
             *  when applyLinearVelocity returns false. FRACTURED has no
             *  such fallback. Add it here for symmetry: kick every new
             *  body with applyRandomSpawnForces so even kinematic shards
             *  get a non-zero velocity. */
            if (spawned && bodiesBefore >= 0
                && ("fractured".equals(mode) || "fractured_voxel".equals(mode)))
            {
                kickStuckShards(mcWorld, bodiesBefore);
            }
            return spawned;
        }
        catch (Throwable t)
        {
            Throwable cause = t;
            while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
            LOG.warn("spawnViaBbsEmitter('{}') failed: [{}] {}", mode,
                cause.getClass().getSimpleName(), cause.getMessage());
        }
        return false;
    }

    /** Backwards-compat overload — collisions enabled (default behavior). */
    public static boolean spawnViaBbsEmitter(double worldX, double worldY, double worldZ,
                                             double velX, double velY, double velZ,
                                             net.minecraft.world.level.block.state.BlockState state,
                                             String mode, float physicsScale, int lifetimeTicks)
    {
        return spawnViaBbsEmitter(worldX, worldY, worldZ, velX, velY, velZ,
            state, mode, physicsScale, lifetimeTicks, false);
    }

    /** Resolve PhysicsWorld.COLLIDE_ALL_MINUS_DYANMIC_OBJECTS (with the
     *  PhysicsMod typo) at first-call, cache for subsequent calls.
     *  Falls back to the corrected spelling on builds that fix it. */
    private static volatile Byte cachedNoDynamicMask;
    private static byte resolveNoDynamicMask()
    {
        if (cachedNoDynamicMask != null) return cachedNoDynamicMask;
        try
        {
            Class<?> pwCls = Class.forName("net.diebuddies.physics.PhysicsWorld");
            try
            {
                java.lang.reflect.Field f = pwCls.getField("COLLIDE_ALL_MINUS_DYANMIC_OBJECTS");
                cachedNoDynamicMask = f.getByte(null);
                return cachedNoDynamicMask;
            }
            catch (NoSuchFieldException ex)
            {
                java.lang.reflect.Field f = pwCls.getField("COLLIDE_ALL_MINUS_DYNAMIC_OBJECTS");
                cachedNoDynamicMask = f.getByte(null);
                return cachedNoDynamicMask;
            }
        }
        catch (Throwable t)
        {
            LOG.debug("resolveNoDynamicMask failed: {}", t.toString());
            return 0;
        }
    }

    /** Reflectively count current PhysX bodies in the world's physics
     *  scene. Used to know how many bodies were just added by a spawn.
     *  Public so callers (e.g. BlockShatterPropagator's "default" blockMode
     *  path) can wrap their own legacy spawn batches with the same
     *  pre/post snapshot for noInterCollision support. */
    public static int snapshotBodyCount(Level mcWorld)
    {
        try
        {
            Object physicsWorld = getPhysicsWorld(mcWorld);
            if (physicsWorld == null) return -1;
            Method getBodies = physicsWorld.getClass().getMethod("getBodies");
            Object bodies = getBodies.invoke(physicsWorld);
            if (bodies instanceof java.util.Collection<?> c) return c.size();
            if (bodies instanceof java.util.List<?> l) return l.size();
        }
        catch (Throwable ignored) {}
        return -1;
    }

    /** For each body added since {@code bodiesBefore}, call
     *  {@code IRigidBody.applyRandomSpawnForces(strength)} to give it a
     *  non-zero velocity. Mirror of BLOCKY mode's bytecode-level fallback.
     *  Used for FRACTURED / FRACTURED_VOXEL where some shards are
     *  kinematic and don't accept setLinearVelocity. */
    private static volatile java.lang.reflect.Method cachedApplyRandomSpawnForces;
    private static void kickStuckShards(Level mcWorld, int bodiesBefore)
    {
        /* Defense in depth: caller already guards on bodiesBefore >= 0
         *  but if any future caller forgets, the for loop's i=bodiesBefore
         *  start would throw IndexOutOfBoundsException on list.get(-1). */
        if (bodiesBefore < 0) return;
        try
        {
            Object physicsWorld = getPhysicsWorld(mcWorld);
            if (physicsWorld == null) return;

            Method getBodies = physicsWorld.getClass().getMethod("getBodies");
            Object bodiesObj = getBodies.invoke(physicsWorld);
            java.util.List<?> list;
            if (bodiesObj instanceof java.util.List<?> l) list = l;
            else if (bodiesObj instanceof java.util.Collection<?> c) list = new java.util.ArrayList<>(c);
            else return;

            int n = list.size();
            for (int i = bodiesBefore; i < n; i++)
            {
                Object rb = list.get(i);
                if (rb == null) continue;
                try
                {
                    if (cachedApplyRandomSpawnForces == null)
                    {
                        /* Prefer the float-arg overload for tunable strength. */
                        try
                        {
                            cachedApplyRandomSpawnForces = rb.getClass().getMethod("applyRandomSpawnForces", float.class);
                        }
                        catch (NoSuchMethodException nsm)
                        {
                            cachedApplyRandomSpawnForces = rb.getClass().getMethod("applyRandomSpawnForces");
                        }
                    }

                    if (cachedApplyRandomSpawnForces.getParameterCount() == 1)
                    {
                        cachedApplyRandomSpawnForces.invoke(rb, 1.0F);
                    }
                    else
                    {
                        cachedApplyRandomSpawnForces.invoke(rb);
                    }
                }
                catch (Throwable ignored) {}
            }
        }
        catch (Throwable t)
        {
            LOG.debug("kickStuckShards failed: {}", t.toString());
        }
    }

    /** For every body added since {@code bodiesBefore}, set its
     *  PhysicsEntity.physicsMask = COLLIDE_ALL_MINUS_DYANMIC_OBJECTS so
     *  it interacts with terrain but not other debris.
     *  Public — see {@link #snapshotBodyCount} for the pairing rationale. */
    public static void applyNoCollisionMask(Level mcWorld, int bodiesBefore)
    {
        /* Defensive: see kickStuckShards for the same rationale. */
        if (bodiesBefore < 0) return;
        try
        {
            Object physicsWorld = getPhysicsWorld(mcWorld);
            if (physicsWorld == null) return;

            /* Get the no-dynamic-objects mask byte from PhysicsWorld's
             *  static constants. */
            Class<?> pwCls = physicsWorld.getClass();
            byte mask;
            try
            {
                java.lang.reflect.Field f = pwCls.getField("COLLIDE_ALL_MINUS_DYANMIC_OBJECTS");
                mask = f.getByte(null);
            }
            catch (NoSuchFieldException ex)
            {
                /* Some builds use the correctly-spelled constant. */
                java.lang.reflect.Field f = pwCls.getField("COLLIDE_ALL_MINUS_DYNAMIC_OBJECTS");
                mask = f.getByte(null);
            }

            Method getBodies = pwCls.getMethod("getBodies");
            Object bodiesObj = getBodies.invoke(physicsWorld);
            java.util.List<?> list;
            if (bodiesObj instanceof java.util.List<?> l) list = l;
            else if (bodiesObj instanceof java.util.Collection<?> c) list = new java.util.ArrayList<>(c);
            else return;

            int n = list.size();
            for (int i = bodiesBefore; i < n; i++)
            {
                Object rb = list.get(i);
                if (rb == null) continue;
                /* IRigidBody.getEntity() → PhysicsEntity. */
                try
                {
                    Method getEntity = rb.getClass().getMethod("getEntity");
                    Object entity = getEntity.invoke(rb);
                    if (entity == null) continue;
                    java.lang.reflect.Field maskField = entity.getClass().getField("physicsMask");
                    maskField.setByte(entity, mask);
                }
                catch (Throwable ignored) {}
            }
        }
        catch (Throwable t)
        {
            LOG.debug("applyNoCollisionMask failed: {}", t.toString());
        }
    }

    /** Cinematic-friendly debris BlockStates. Cycle by index so a
     *  single fracture event produces a visual mix rather than 200
     *  identical stones flying. */
    private static net.minecraft.world.level.block.state.BlockState pickDebrisState(int idx)
    {
        int n = ((idx % 6) + 6) % 6;
        switch (n)
        {
            case 0:  return net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
            case 1:  return net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState();
            case 2:  return net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState();
            case 3:  return net.minecraft.world.level.block.Blocks.OAK_LOG.defaultBlockState();
            case 4:  return net.minecraft.world.level.block.Blocks.IRON_BLOCK.defaultBlockState();
            default: return net.minecraft.world.level.block.Blocks.GRAVEL.defaultBlockState();
        }
    }

    public static void despawn(Object body)
    {
        if (!available || body == null) return;

        synchronized (LIVE_BODIES) { LIVE_BODIES.remove(body); }

        try
        {
            Level mcWorld = Minecraft.getInstance().level;
            if (mcWorld == null) return;
            Object physicsWorld = getPhysicsWorld(mcWorld);
            if (physicsWorld != null)
            {
                physicsWorldRemoveBody.invoke(physicsWorld, body);
            }
        }
        catch (Throwable t)
        {
            LOG.debug("despawn failed (idempotent): {}", t.toString());
        }
    }

    public static void despawnAll()
    {
        List<Object> snapshot;
        synchronized (LIVE_BODIES) { snapshot = new ArrayList<>(LIVE_BODIES.keySet()); LIVE_BODIES.clear(); }
        for (Object body : snapshot) despawn(body);
    }

    public static int liveBodyCount()
    {
        synchronized (LIVE_BODIES) { return LIVE_BODIES.size(); }
    }

    public static boolean isSmokeAvailable()
    {
        return available && smokeAvailable;
    }

    /**
     * Spawn a single PhysicsMod realistic smoke particle at the given
     * world coordinates. Density controls the puff thickness — values
     * between 0.4 and 1.5 are visually useful (lower = wispier, higher
     * = denser cloud).
     *
     * <p>Returns {@code true} if the particle was spawned via PhysicsMod;
     * the simulator's caller falls back to a vanilla SMOKE particle if
     * this returns false.</p>
     */
    public static boolean spawnRealisticSmoke(double x, double y, double z, float density)
    {
        if (!available || !smokeAvailable) return false;

        try
        {
            Level mcWorld = Minecraft.getInstance().level;
            if (mcWorld == null) return false;

            Object physicsWorld = getPhysicsWorld(mcWorld);
            if (physicsWorld == null) return false;

            Object smokeDomain = physicsWorldGetSmokeDomain.invoke(physicsWorld);
            if (smokeDomain == null) return false;

            smokeDomainSpawnParticle.invoke(smokeDomain, x, y, z, density);
            return true;
        }
        catch (Throwable t)
        {
            /* Disable smoke route after first failure so we don't log-
             *  spam each tick if PhysicsMod's API drifts. */
            smokeAvailable = false;
            LOG.warn("spawnRealisticSmoke failed; disabling smoke bridge: {}", t.toString());
            return false;
        }
    }

    /* === Helpers === */

    private static Object getPhysicsWorld(Level mcWorld) throws IllegalAccessException
    {
        Object instancesMap = physicsModInstancesField.get(null);
        if (!(instancesMap instanceof Map<?, ?> map)) return null;
        Object physicsMod = map.get(mcWorld);
        if (physicsMod == null) return null;
        return physicsModWorldField.get(physicsMod);
    }

    private static Object getPhysicsModInstance(Level mcWorld) throws IllegalAccessException
    {
        Object instancesMap = physicsModInstancesField.get(null);
        if (!(instancesMap instanceof Map<?, ?> map)) return null;
        return map.get(mcWorld);
    }

    /**
     * Cancel PhysicsMod's pending BlockUpdate at {@code pos} so its
     * deferred {@code PhysicsUpdater.updatePhysics} loop doesn't
     * spawn its own debris there.
     *
     * <p>Why: when our block-destruction clip calls
     * {@code world.setBlockState(pos, AIR, 3)}, PhysicsMod's
     * {@code MixinLevelChunk.setBlockState} injection adds a
     * {@code BlockUpdate(world, pos, oldState)} to
     * {@code PhysicsMod.updateQueue}. On a later tick, PhysicsMod
     * spawns its OWN debris from that entry — with default zero/random
     * velocity, so those chunks just sit at the block position while
     * OUR explicit chunks fly off with the user's force settings.
     * The visual is "structure breaks apart in place; some flying chunks"
     * which is exactly what the user reported. By removing the entry
     * synchronously after our setBlockState, only OUR chunks (with
     * proper velocity) ever spawn.</p>
     */
    private static int cancelLogCounter;
    private static int cancelTotalQ1Removed;
    private static int cancelTotalQ2Removed;
    private static int cancelTotalCalls;

    public static void cancelPendingPhysicsModUpdate(Level mcWorld, net.minecraft.core.BlockPos pos)
    {
        if (!available || mcWorld == null || pos == null) return;
        cancelTotalCalls++;
        try
        {
            Object physicsMod = getPhysicsModInstance(mcWorld);
            if (physicsMod == null)
            {
                if ((cancelLogCounter++ & 31) == 0)
                {
                    LOG.info("[block-destruction] cancelPendingPhysicsModUpdate: PhysicsMod instance is null for world {}", mcWorld);
                }
                return;
            }

            Class<?> pmCls = physicsMod.getClass();
            int q1Removed = 0;
            int q2Removed = 0;

            /* updateQueue: List<BlockUpdate> — match by BlockUpdate.pos.equals(pos). */
            try
            {
                java.lang.reflect.Field updateQueueField = pmCls.getField("updateQueue");
                Object updateQueueObj = updateQueueField.get(physicsMod);
                if (updateQueueObj instanceof java.util.List<?> list)
                {
                    synchronized (list)
                    {
                        java.util.Iterator<?> it = list.iterator();
                        while (it.hasNext())
                        {
                            Object bu = it.next();
                            if (bu == null) continue;
                            try
                            {
                                java.lang.reflect.Field posField = bu.getClass().getField("pos");
                                Object bp = posField.get(bu);
                                if (pos.equals(bp)) { it.remove(); q1Removed++; }
                            }
                            catch (Throwable ignored) {}
                        }
                    }
                }
                else if (updateQueueObj != null && (cancelLogCounter & 31) == 0)
                {
                    LOG.info("[block-destruction] updateQueue is {} (not List)", updateQueueObj.getClass().getName());
                }
            }
            catch (Throwable t)
            {
                if ((cancelLogCounter & 31) == 0)
                    LOG.info("[block-destruction] updateQueue removal failed: {}", t.toString());
            }

            /* blockUpdates: ConcurrentLinkedQueue<BlockPos>. */
            try
            {
                java.lang.reflect.Field blockUpdatesField = pmCls.getField("blockUpdates");
                Object queueObj = blockUpdatesField.get(physicsMod);
                if (queueObj instanceof java.util.Collection<?> col)
                {
                    java.util.Iterator<?> it = col.iterator();
                    while (it.hasNext())
                    {
                        Object bp = it.next();
                        if (pos.equals(bp))
                        {
                            try { it.remove(); q2Removed++; }
                            catch (Throwable ignored) {}
                        }
                    }
                }
            }
            catch (Throwable t)
            {
                if ((cancelLogCounter & 31) == 0)
                    LOG.info("[block-destruction] blockUpdates removal failed: {}", t.toString());
            }

            cancelTotalQ1Removed += q1Removed;
            cancelTotalQ2Removed += q2Removed;
            cancelLogCounter++;

            /* The "every 32 calls" LOG.info summary that helped during
             *  the 2026-05-10 duplicate-spawn hunt is gone now —
             *  /bbstools debug-everything fracture exposes the totals
             *  on demand. The unconditional logging spammed ~30 lines/sec
             *  during active destruction. Failure-path warns above are
             *  retained for genuine breakage. */
        }
        catch (Throwable t)
        {
            LOG.debug("cancelPendingPhysicsModUpdate failed at {}: {}", pos, t.toString());
        }
    }

    private static Object buildMesh(Vector3f[] verticesLocal) throws Exception
    {
        Object mesh = meshCtor.newInstance();

        /* Mesh.positions is List<Vector3f>. Fill with the bone vertices. */
        List<Vector3f> positions = new ArrayList<>(Arrays.asList(verticesLocal));
        meshPositionsField.set(mesh, positions);

        /* Indices: triangle fan over the vertices. The cooked convex
         *  hull is computed from positions alone, but PhysicsMod's
         *  rendering path (and possibly the volume calculation in
         *  addBlockParticle) needs a non-empty indices list. */
        try
        {
            it.unimi.dsi.fastutil.ints.IntList existing = (it.unimi.dsi.fastutil.ints.IntList) meshIndicesField.get(mesh);
            if (existing != null)
            {
                for (int i = 1; i < verticesLocal.length - 1; i++)
                {
                    existing.add(0);
                    existing.add(i);
                    existing.add(i + 1);
                }
            }
        }
        catch (Throwable ignored) {}

        /* Mesh.offset is null by default. PhysicsMod's addBlockParticle
         *  internally calls Matrix4d.translate(mesh.offset) — null
         *  triggers NullPointerException at PhysicsWorld:959. Fix:
         *  call Mesh.calculateOffset() which populates it from the
         *  positions' centroid; fall back to (0,0,0) if reflection
         *  on calculateOffset fails. */
        try
        {
            Method calcOffset = meshCls.getMethod("calculateOffset");
            calcOffset.invoke(mesh);
        }
        catch (Throwable t)
        {
            /* Fallback: directly set offset to zero. */
            try
            {
                Field offsetField = meshCls.getField("offset");
                offsetField.set(mesh, new Vector3f(0F, 0F, 0F));
            }
            catch (Throwable ignored) {}
        }

        /* Initialise other commonly-iterated fields that PhysicsMod
         *  may dereference. Keep them as empty lists so internal
         *  loops execute zero times rather than NPE. */
        try { ensureListField(mesh, "normals", new ArrayList<>()); } catch (Throwable ignored) {}
        try { ensureListField(mesh, "uvs", new ArrayList<>()); } catch (Throwable ignored) {}
        try { ensureListField(mesh, "tangents", new ArrayList<>()); } catch (Throwable ignored) {}

        return mesh;
    }

    private static void ensureListField(Object target, String fieldName, Object emptyList) throws Exception
    {
        Field f = target.getClass().getField(fieldName);
        if (f.get(target) == null) f.set(target, emptyList);
    }

    private static Object buildPhysicsEntity(Level mcWorld,
                                             double worldX, double worldY, double worldZ,
                                             Quaternionf rotation, Vector3f velocity) throws Exception
    {
        /* PhysicsEntity ctor's second arg is the "info" Object. For
         *  Type.BLOCK PhysicsMod's render path expects a BlockState —
         *  it uses that state's block model + texture for visuals.
         *  Without a valid info, addBlockParticle's internal model
         *  creation has nothing to texture and the rigidbody renders
         *  invisibly even though physics simulation runs. */
        net.minecraft.world.level.block.state.BlockState fallbackState = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
        Object entity = physicsEntityCtor.newInstance(physicsEntityTypeBlock, fallbackState);

        trySetPositionFields(entity, worldX, worldY, worldZ);
        trySetRotation(entity, rotation);
        trySetVelocityFields(entity, velocity);
        trySetLifetime(entity, 600F); /* 30 seconds default ceiling */

        return entity;
    }

    private static void trySetPositionFields(Object entity, double x, double y, double z)
    {
        for (Field f : entity.getClass().getFields())
        {
            String n = f.getName().toLowerCase();
            try
            {
                if ("x".equals(n) || "posx".equals(n)) setNumeric(f, entity, x);
                else if ("y".equals(n) || "posy".equals(n)) setNumeric(f, entity, y);
                else if ("z".equals(n) || "posz".equals(n)) setNumeric(f, entity, z);
                else if ("position".equals(n) && f.getType().getName().endsWith("Vector3f"))
                {
                    Object vec = f.get(entity);
                    if (vec != null)
                    {
                        Method set = vec.getClass().getMethod("set", float.class, float.class, float.class);
                        set.invoke(vec, (float) x, (float) y, (float) z);
                    }
                }
            }
            catch (Throwable ignored) { /* skip non-matching field shapes */ }
        }
    }

    private static void trySetRotation(Object entity, Quaternionf rotation)
    {
        try
        {
            Method setRot = entity.getClass().getMethod("setRotation", Quaternionf.class);
            setRot.invoke(entity, rotation);
        }
        catch (Throwable ignored) { /* method may not exist or require different type */ }
    }

    private static void trySetVelocityFields(Object entity, Vector3f velocity)
    {
        if (velocity == null) return;
        for (Field f : entity.getClass().getFields())
        {
            String n = f.getName().toLowerCase();
            try
            {
                if ("velocity".equals(n) && f.getType().getName().endsWith("Vector3f"))
                {
                    Object vec = f.get(entity);
                    if (vec != null)
                    {
                        Method set = vec.getClass().getMethod("set", float.class, float.class, float.class);
                        set.invoke(vec, velocity.x, velocity.y, velocity.z);
                    }
                }
                else if ("vx".equals(n) || "vy".equals(n) || "vz".equals(n))
                {
                    float v = "vx".equals(n) ? velocity.x : "vy".equals(n) ? velocity.y : velocity.z;
                    setNumeric(f, entity, v);
                }
            }
            catch (Throwable ignored) {}
        }
    }

    private static void trySetLifetime(Object entity, float ticks)
    {
        try
        {
            Field f = entity.getClass().getField("lifetime");
            f.setFloat(entity, ticks);
        }
        catch (Throwable ignored) {}
    }

    private static void setNumeric(Field f, Object target, double value) throws IllegalAccessException
    {
        Class<?> t = f.getType();
        if (t == double.class) f.setDouble(target, value);
        else if (t == float.class) f.setFloat(target, (float) value);
        else if (t == int.class) f.setInt(target, (int) value);
    }

}
