// Coded by Afrobama/Claude — first Quasar-particle feature. Spawns a
// pre-registered Quasar emitter at a world position. Used as a smoke
// test from /mas quasar test and as the foundation for future BBS-
// clip-driven particle effects.
package com.afrobama.bbstools.client.veil;

import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.quasar.particle.ParticleEmitter;
import foundry.veil.api.quasar.particle.ParticleSystemManager;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Spawns Quasar particle emitters at world positions. The emitter
 * definition JSONs live in {@code assets/bbstools/quasar/emitters/}
 * and reference particle_settings + particle_data + shape modules
 * under the same namespace.
 *
 * <p>The smoke_test emitter is our smoke-test (pun): rising white-
 * to-grey smoke cloud. Once verified, this same class spawns any
 * registered emitter by ID — sparks, fire, magic-runes, glass-
 * shatter — as we add more JSON definitions.</p>
 */
public final class MASQuasarFeature
{
    private static final Logger LOG = LoggerFactory.getLogger("mas/quasar");

    public static final ResourceLocation SMOKE_TEST = ResourceLocation.fromNamespaceAndPath("bbstools", "smoke_test");
    public static final ResourceLocation VOLCANO    = ResourceLocation.fromNamespaceAndPath("bbstools", "volcano");

    private MASQuasarFeature() {}

    /**
     * Spawn an emitter by registered ID at the given world coordinate.
     * @return the spawned emitter, or null on failure
     */
    public static ParticleEmitter spawnAt(ResourceLocation emitterId, double x, double y, double z)
    {
        if (!VeilFeatureGate.isVeilLoaded())
        {
            LOG.info("spawnAt() ignored — Veil not loaded");
            return null;
        }
        try
        {
            ParticleSystemManager mgr = VeilRenderSystem.renderer().getParticleManager();
            if (mgr == null)
            {
                LOG.warn("spawnAt() — Quasar ParticleSystemManager is null");
                return null;
            }
            /* Veil sets manager.level from the client world events; if
             *  it's not set yet (e.g. main menu), the emitter would
             *  spawn into a null world. Guard. */
            if (mgr.getLevel() == null)
            {
                /* Best-effort: set it from the current client world. */
                Minecraft mc = Minecraft.getInstance();
                if (mc != null && mc.level != null)
                {
                    mgr.setLevel(mc.level);
                }
                else
                {
                    LOG.warn("spawnAt() — no client world available");
                    return null;
                }
            }

            ParticleEmitter emitter = mgr.createEmitter(emitterId);
            if (emitter == null)
            {
                LOG.warn("spawnAt() — createEmitter({}) returned null. JSON missing or invalid?", emitterId);
                return null;
            }
            emitter.setPosition(x, y, z);
            mgr.addParticleSystem(emitter);
            LOG.info("Spawned Quasar emitter {} at ({}, {}, {}); active={} totalEmitters={}",
                emitterId, x, y, z, !emitter.isRemoved(), mgr.getEmitterCount());
            return emitter;
        }
        catch (Throwable t)
        {
            LOG.warn("spawnAt() threw for {}: {}", emitterId, t.toString(), t);
            return null;
        }
    }

    /** Convenience: spawn the smoke_test at the player's current position. */
    public static ParticleEmitter spawnSmokeAtPlayer()
    {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null)
        {
            LOG.info("spawnSmokeAtPlayer() — no player");
            return null;
        }
        return spawnAt(SMOKE_TEST, mc.player.getX(), mc.player.getY() + 1.0, mc.player.getZ());
    }

    /**
     * Spawn a volcano eruption emitter at the player's look-target — 6
     * blocks ahead horizontally at the player's feet level. The player
     * runs the command facing where they want the eruption to appear
     * and can then walk around it to view from any angle. The emitter
     * loops for ~10 seconds before auto-removing.
     */
    public static ParticleEmitter spawnVolcanoAtTarget()
    {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null)
        {
            LOG.info("spawnVolcanoAtTarget() — no player");
            return null;
        }
        /* Horizontal-only look direction so the volcano lands at ground
         * level regardless of pitch. */
        net.minecraft.world.phys.Vec3 look = mc.player.getLookAngle();
        net.minecraft.world.phys.Vec3 horiz = new net.minecraft.world.phys.Vec3(look.x, 0.0, look.z).normalize();
        double tx = mc.player.getX() + horiz.x * 6.0;
        double ty = mc.player.getY();
        double tz = mc.player.getZ() + horiz.z * 6.0;
        LOG.info("Spawning volcano at player-look-target ({}, {}, {})", tx, ty, tz);
        return spawnAt(VOLCANO, tx, ty, tz);
    }

    /** Diagnostic counts for /bbstools debug-everything. */
    public static int getEmitterCount()
    {
        if (!VeilFeatureGate.isVeilLoaded()) return -1;
        try
        {
            ParticleSystemManager mgr = VeilRenderSystem.renderer().getParticleManager();
            return mgr == null ? -1 : mgr.getEmitterCount();
        }
        catch (Throwable t) { return -1; }
    }

    public static int getParticleCount()
    {
        if (!VeilFeatureGate.isVeilLoaded()) return -1;
        try
        {
            ParticleSystemManager mgr = VeilRenderSystem.renderer().getParticleManager();
            return mgr == null ? -1 : mgr.getParticleCount();
        }
        catch (Throwable t) { return -1; }
    }

}
