// Coded by Afrobama/Claude — runtime toggle of PhysicsMod world-feature flags
// for a CLEAN A/B of PhysicsMod's render-thread cost WITHOUT removing the jar.
//
// WHY this exists: disabling features in PhysicsMod's in-game GUI does NOT unload
// its per-frame WorldRenderer hooks (renderMain / renderLiquid still fire), and
// fully removing the jar breaks BBSTools' ragdoll/fracture bridge (and the user's
// last A/B silently kept the jar loaded → "fps gleich" was PhysicsMod-on vs on,
// invalid). This reflectively flips net.diebuddies.config.ConfigClient's public
// static boolean WORLD flags off, saving prior values, and restores them exactly.
//
// PhysicsMod render path (decompiled v172j):
//   MixinLevelRenderer.renderMain   -> MainRenderer.renderAll      (PhysX step +
//       PhysicsUpdater.updatePhysics, gated by ConfigClient.areOceanPhysicsEnabled
//       / areSnowPhysicsEnabled, plus a per-frame blockPhysicsRange world scan)
//   MixinLevelRenderer.renderLiquid -> MainRenderer.renderLiquid   -> LiquidRenderer
//       (iterates getLiquids() + ocean meshes — the dynamic water re-uploaded every
//       frame, the class_291/class_285 vertex-buffer churn in the profile)
// Flipping ocean/liquid/block/snow off stops the sim step + scan next frame; stale
// ocean/liquid meshes clear shortly after, so the A/B reading settles within ~1-2s.
//
// Reflection (not a compile-time ref) because PhysicsMod is OPTIONAL — a hard ref
// would NoClassDefFound for users without it (see BBSPhysicsModMixinPlugin). No-op
// when physicsmod is absent.
package com.afrobama.bbstools.client.perf;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PhysicsModControl
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/physics-suppress");

    private static final String CONFIG_CLASS = "net.diebuddies.config.ConfigClient";

    /** ConfigClient public-static boolean flags gating PhysicsMod's per-frame WORLD
     *  rendering + simulation (the render-thread hotspots renderMain / renderLiquid).
     *  Entity-attached flags (mob/cape/item/leash/banner/fishingRod) are intentionally
     *  left alone — they cost ~0 with no physics entities present and toggling them
     *  would visibly disrupt the player's own cloth/cape. */
    private static final String[] FLAGS = {
        "oceanPhysics",
        "liquidPhysics",
        "blockPhysics",
        "snowPhysics",
        "smokePhysics",
        "windPhysics",
        "weatherParticles",
    };

    private static boolean resolved;
    private static boolean available;
    private static final Map<String, Field> FIELDS = new LinkedHashMap<>();
    private static final Map<String, Boolean> SAVED = new LinkedHashMap<>();

    private static boolean suppressed;

    private PhysicsModControl()
    {
    }

    public static boolean isAvailable()
    {
        resolve();

        return available;
    }

    public static boolean isSuppressed()
    {
        return suppressed;
    }

    public static synchronized void setSuppressed(boolean on)
    {
        if (on == suppressed)
        {
            return;
        }

        resolve();

        if (!available)
        {
            LOG.warn("[physics-suppress] PhysicsMod ConfigClient unavailable — toggle ignored");

            return;
        }

        if (on)
        {
            suppress();
        }
        else
        {
            restore();
        }

        suppressed = on;
    }

    private static void suppress()
    {
        SAVED.clear();

        int n = 0;

        for (Map.Entry<String, Field> e : FIELDS.entrySet())
        {
            try
            {
                boolean prev = e.getValue().getBoolean(null);

                SAVED.put(e.getKey(), prev);
                e.getValue().setBoolean(null, false);
                n++;
            }
            catch (Throwable t)
            {
                LOG.warn("[physics-suppress] failed to disable {}: {}", e.getKey(), t.toString());
            }
        }

        LOG.info("[physics-suppress] DISABLED {} PhysicsMod world flags (saved {}) — watch render-thread renderLiquid/renderMain drop + fps rise", n, SAVED);
    }

    private static void restore()
    {
        int n = 0;

        for (Map.Entry<String, Boolean> e : SAVED.entrySet())
        {
            Field f = FIELDS.get(e.getKey());

            if (f == null)
            {
                continue;
            }

            try
            {
                f.setBoolean(null, e.getValue());
                n++;
            }
            catch (Throwable t)
            {
                LOG.warn("[physics-suppress] failed to restore {}: {}", e.getKey(), t.toString());
            }
        }

        LOG.info("[physics-suppress] RESTORED {} PhysicsMod world flags {}", n, SAVED);
        SAVED.clear();
    }

    private static synchronized void resolve()
    {
        if (resolved)
        {
            return;
        }

        resolved = true;

        try
        {
            if (!FabricLoader.getInstance().isModLoaded("physicsmod"))
            {
                LOG.info("[physics-suppress] physicsmod not loaded — control disabled (no-op)");
                available = false;

                return;
            }

            Class<?> cls = Class.forName(CONFIG_CLASS);

            for (String name : FLAGS)
            {
                try
                {
                    Field f = cls.getField(name);

                    f.setAccessible(true);
                    FIELDS.put(name, f);
                }
                catch (NoSuchFieldException nsf)
                {
                    LOG.warn("[physics-suppress] ConfigClient has no field '{}' (version drift?) — skipping", name);
                }
            }

            available = !FIELDS.isEmpty();
            LOG.info("[physics-suppress] resolved {} ConfigClient flags: {}", FIELDS.size(), FIELDS.keySet());
        }
        catch (Throwable t)
        {
            available = false;
            LOG.warn("[physics-suppress] resolve failed: {}", t.toString());
        }
    }

    /** Live snapshot of the current flag values for status dumps. */
    public static String snapshot()
    {
        resolve();

        if (!available)
        {
            return "unavailable";
        }

        StringBuilder sb = new StringBuilder();

        for (Map.Entry<String, Field> e : FIELDS.entrySet())
        {
            try
            {
                sb.append(e.getKey()).append('=').append(e.getValue().getBoolean(null)).append(' ');
            }
            catch (Throwable ignored)
            {
            }
        }

        return sb.toString().trim();
    }

}
