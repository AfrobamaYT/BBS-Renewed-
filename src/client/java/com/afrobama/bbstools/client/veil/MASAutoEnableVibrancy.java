// Coded by Afrobama/Claude — Auto-enable destroying-mc-style vibrancy.
// Per user request 2026-05-14: enter a world -> see vibrant look like
// destroying-mc (sun glows, bright pixels bloom, post-tonemap applied).
//
// Mirrors destroying-mc's ShaderType.CRACKS default which auto-enables:
//   - SUPERNOVA_CLIENT (sky shader for sun render) — we skip this for
//     now since our sky port is gated by supernovaTimer
//   - SHADOWS — skipped (needs Veil deferred G-buffer infra)
//   - BLOOM (mipmap-tree pipeline) — auto-enabled
//   - POST (final tonemap composite) — auto-enabled
//
// Effect: vanilla MC bright pixels (sun, lava, glowstone, torches, light
// sources) glow naturally via bloom. The "round sun" the user described
// = vanilla square sun with bloom softening + glowing it.
package com.afrobama.bbstools.client.veil;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MASAutoEnableVibrancy
{
    private static final Logger LOG = LoggerFactory.getLogger("mas/auto-vibrancy");

    private static final ResourceLocation BLOOM_DM = new ResourceLocation("bbstools", "bloom_dm");
    private static final ResourceLocation POST_DESTROYING = new ResourceLocation("bbstools", "post_destroying");

    private MASAutoEnableVibrancy() {}

    /** Set to true after debugging — user reported pure-red screen 2026-05-14
     *  with both pipelines auto-on. Suspect: bloom_dm pipeline uses
     *  R11F_G11F_B10F intermediate framebuffers but writes back to
     *  minecraft:main (RGBA8), or StartSampler binding to minecraft:main
     *  reads it AFTER pipeline has begun -> wrong data. Disabling auto
     *  for now; user must manually /mas bloom_dm on + /mas post on to test. */
    private static final boolean AUTO_ENABLE_ON_JOIN = false;

    public static void register()
    {
        if (!AUTO_ENABLE_ON_JOIN)
        {
            LOG.info("MASAutoEnableVibrancy DISABLED (debugging red-screen issue). "
                + "Use /mas bloom_dm on / /mas post on manually to test pipelines.");
            return;
        }
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
        {
            if (!VeilFeatureGate.isVeilLoaded())
            {
                LOG.info("Auto-vibrancy skipped — Veil not loaded");
                return;
            }
            try
            {
                foundry.veil.api.client.render.post.PostProcessingManager mgr =
                    foundry.veil.api.client.render.VeilRenderSystem.renderer().getPostProcessingManager();
                if (mgr == null) return;

                /* Enable bloom pipeline (destroying-mc 1:1 port). */
                if (!mgr.isActive(BLOOM_DM))
                {
                    mgr.add(BLOOM_DM);
                    LOG.info("Auto-enabled bloom_dm pipeline on world join");
                }
                /* Enable post pipeline (destroying-mc 1:1 port). */
                if (!mgr.isActive(POST_DESTROYING))
                {
                    mgr.add(POST_DESTROYING);
                    LOG.info("Auto-enabled post_destroying pipeline on world join");
                }
            }
            catch (Throwable t)
            {
                LOG.warn("Auto-enable vibrancy failed: {}", t.toString(), t);
            }
        });

        ClientLifecycleEvents.CLIENT_STARTED.register(client ->
            LOG.info("MASAutoEnableVibrancy registered — bloom_dm + post_destroying will activate on world join"));
    }

}
