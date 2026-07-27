package com.afrobama.bbstools.client.ragdoll;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import net.fabricmc.loader.api.FabricLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reflection-only bridge to PhysicsMod's
 * {@code net.diebuddies.physics.ragdoll.RagdollHook} interface so
 * BBSTools can register a hook without a compile-time reference to
 * the PhysicsMod jar.
 *
 * <p>Why reflection: the previous fork-version implemented RagdollHook
 * directly via {@code implements RagdollHook}. That ties our jar to
 * PhysicsMod at class-load time — without PhysicsMod installed the
 * BBSPhysicsModBridge class fails to load and BBSTools dies with
 * NoClassDefFoundError. The Proxy-based approach has no static
 * reference; if PhysicsMod is missing we cleanly skip registration.</p>
 *
 * <p>Behaviour: currently a logging-only hook. The original fork-impl's
 * v1-v3 was also logging-only because the actor-ragdoll cube/joint
 * pipeline is non-trivial. Re-introducing real cubes is a separate
 * project (see fork-side BBSPhysicsModBridge.java for design notes).</p>
 */
public final class BBSPhysicsModBridge
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/physicsmod-bridge");
    private static volatile boolean initialized;

    private BBSPhysicsModBridge()
    {
    }

    public static void init()
    {
        if (initialized) return;
        initialized = true;

        if (!FabricLoader.getInstance().isModLoaded("physicsmod"))
        {
            LOG.info("PhysicsMod not loaded — BBS verlet ragdoll fallback remains active");
            return;
        }

        try
        {
            Class<?> hookCls = Class.forName("net.diebuddies.physics.ragdoll.RagdollHook");
            Class<?> mapperCls = Class.forName("net.diebuddies.physics.ragdoll.RagdollMapper");

            Object hook = Proxy.newProxyInstance(
                hookCls.getClassLoader(),
                new Class<?>[]{ hookCls },
                BBSPhysicsModBridge::handleHookCall
            );

            Method addHook = mapperCls.getMethod("addHook", hookCls);
            addHook.invoke(null, hook);

            LOG.info("Registered BBSTools RagdollHook with PhysicsMod via reflection proxy");
        }
        catch (ClassNotFoundException cnfe)
        {
            LOG.info("PhysicsMod classes not on classpath: {}", cnfe.getMessage());
        }
        catch (Throwable t)
        {
            LOG.warn("Failed to register PhysicsMod ragdoll hook: {}", t.toString());
        }
    }

    /**
     * Dispatches RagdollHook method calls. PhysicsMod's interface has
     * two methods (verified via fork-side bytecode inspection):
     * {@code map(Ragdoll, Entity, EntityModel)} and
     * {@code filterCuboidsFromEntities(List, Entity, EntityModel)}.
     * We log + pass-through; v2 will populate real cubes.
     */
    private static Object handleHookCall(Object proxy, Method method, Object[] args)
    {
        String name = method.getName();
        Object entity = args != null && args.length >= 2 ? args[1] : null;

        /* First priority: detect ActorRagdollProxy-spawned villagers.
         *  When PhysicsMod calls map(Ragdoll, Entity, EntityModel) on
         *  one of our proxies, capture the Ragdoll handle for the
         *  associated BBS actor so the override mixin can read body
         *  transforms each render frame. ALSO register every ragdoll
         *  globally so the gravity-gun can grab any of them. */
        if ("map".equals(name) && args != null && args.length >= 2)
        {
            Object ragdoll = args[0];
            ActorRagdollProxy.onRagdollCreated(ragdoll, entity);
            PhysicsRagdollRegistry.register(ragdoll);
        }

        if (!isActorEntity(entity))
        {
            return null;
        }

        if ("map".equals(name))
        {
            LOG.debug("[bbs-hook] map(actor) — pass-through");
        }
        else if ("filterCuboidsFromEntities".equals(name))
        {
            LOG.debug("[bbs-hook] filterCuboidsFromEntities(actor) — pass-through");
        }

        /* Returning null is fine for void methods. PhysicsMod's hook
         *  interface methods both return void. */
        return null;
    }

    private static boolean isActorEntity(Object entity)
    {
        if (entity == null) return false;
        try
        {
            Class<?> actorCls = Class.forName("mchorse.bbs_mod.entity.ActorEntity");
            return actorCls.isInstance(entity);
        }
        catch (ClassNotFoundException ignored)
        {
            return false;
        }
    }

}
