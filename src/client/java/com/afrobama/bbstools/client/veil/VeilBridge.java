// Coded by Afrobama/Claude — central facade for direct Veil API
// access. Every Veil-dependent feature in MAS goes through here so we
// have ONE place that knows how to talk to Veil and ONE place that
// catches API-shape changes when Veil bumps a major version.
package com.afrobama.bbstools.client.veil;

import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.post.PostProcessingManager;
import foundry.veil.api.client.render.shader.program.ShaderProgram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

/**
 * Compile-time-bound facade for Veil's runtime systems. Loaded lazily —
 * touching this class triggers JVM classloading of the
 * {@code foundry.veil.*} imports, which would NoClassDefFoundError
 * if Veil isn't on the classpath. So every entry point gates on
 * {@link VeilFeatureGate#isVeilLoaded()} BEFORE the JVM loads
 * VeilBridge for the first time.
 *
 * <p>The probe methods that hit Veil's runtime state can fail if
 * called too early (before Veil's own init). They log + return
 * defaults rather than throw — the {@code /bbsdebug veil-status}
 * command should always succeed.</p>
 */
public final class VeilBridge
{
    private static final Logger LOG = LoggerFactory.getLogger("mas/veil-bridge");

    private VeilBridge()
    {
    }

    /**
     * Number of post-processing chains currently registered with Veil's
     * PostProcessingManager. A non-zero count is evidence that mods
     * (or Veil itself) have wired their pipelines in.
     */
    public static int countPostProcessingPipelines()
    {
        try
        {
            PostProcessingManager mgr = VeilRenderSystem.renderer().getPostProcessingManager();
            if (mgr == null) return -1;
            return mgr.getPipelines().size();
        }
        catch (Throwable t)
        {
            LOG.debug("countPostProcessingPipelines failed: {}", t.toString());
            return -1;
        }
    }

    /**
     * Number of custom shader programs currently registered. Useful as
     * a smoke-test signal: if Veil successfully loaded its own
     * shaders, this should be a healthy number.
     */
    public static int countShaderPrograms()
    {
        try
        {
            Collection<ShaderProgram> programs = VeilRenderSystem.renderer().getShaderManager().getShaders().values();
            return programs.size();
        }
        catch (Throwable t)
        {
            LOG.debug("countShaderPrograms failed: {}", t.toString());
            return -1;
        }
    }

    /**
     * Veil's own render-system version string, pulled from runtime.
     * Distinct from {@link VeilFeatureGate#getVeilVersion()} which
     * comes from FabricLoader's mod metadata — this one tells us what
     * version Veil thinks it's actually running.
     */
    public static String getRuntimeRendererInfo()
    {
        try
        {
            return VeilRenderSystem.renderer().getClass().getSimpleName();
        }
        catch (Throwable t)
        {
            return "<not-ready>";
        }
    }

}
