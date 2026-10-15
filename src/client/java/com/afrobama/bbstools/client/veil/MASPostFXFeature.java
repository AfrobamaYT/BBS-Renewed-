// Coded by Afrobama/Claude — generic toggleable Post-FX feature.
// Wraps any Veil PostProcessingChain (built-in or our own) behind
// enable/disable/toggle. Replaces having a dedicated class per
// effect. /mas <effect-id> on|off|toggle commands hang off these.
package com.afrobama.bbstools.client.veil;

import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.post.PostProcessingManager;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generic Veil Post-FX chain toggle. Instantiate one per effect
 * (bloom, cinematic, grade, dof, grain, flare, vhs, etc.) with the
 * pipeline's Veil ResourceLocation. Then call enable/disable/toggle.
 *
 * <p>Every operation gates on {@link VeilFeatureGate#isVeilLoaded()}
 * and logs verbosely so failures surface in
 * {@code bbstools-console.log} without requiring re-runs.</p>
 */
public class MASPostFXFeature
{
    private final Logger log;
    private final ResourceLocation pipelineId;
    private final String displayName;

    /**
     * @param namespace usually "bbstools" for our own pipelines, or
     *                  "veil" for Veil's built-ins
     * @param path      pipeline path, e.g. "cinematic" or "core/bloom"
     * @param displayName human-readable label for log lines + chat
     */
    public MASPostFXFeature(String namespace, String path, String displayName)
    {
        this.pipelineId = new ResourceLocation(namespace, path);
        this.displayName = displayName;
        this.log = LoggerFactory.getLogger("mas/postfx/" + path.replace('/', '_'));
    }

    public ResourceLocation id() { return this.pipelineId; }
    public String displayName() { return this.displayName; }

    public boolean isActive()
    {
        if (!VeilFeatureGate.isVeilLoaded()) return false;
        try
        {
            PostProcessingManager mgr = VeilRenderSystem.renderer().getPostProcessingManager();
            return mgr != null && mgr.isActive(this.pipelineId);
        }
        catch (Throwable t)
        {
            this.log.debug("isActive probe failed: {}", t.toString());
            return false;
        }
    }

    public boolean enable()
    {
        if (!VeilFeatureGate.isVeilLoaded())
        {
            this.log.info("[{}] enable() ignored — Veil not loaded", this.displayName);
            return false;
        }
        try
        {
            PostProcessingManager mgr = VeilRenderSystem.renderer().getPostProcessingManager();
            if (mgr == null)
            {
                this.log.warn("[{}] enable() — PostProcessingManager is null", this.displayName);
                return false;
            }
            boolean added = mgr.add(this.pipelineId);
            this.log.info("[{}] enable() -> added={} isActive={}", this.displayName, added, mgr.isActive(this.pipelineId));
            return added;
        }
        catch (Throwable t)
        {
            this.log.warn("[{}] enable() threw: {}", this.displayName, t.toString(), t);
            return false;
        }
    }

    public boolean disable()
    {
        if (!VeilFeatureGate.isVeilLoaded())
        {
            this.log.info("[{}] disable() ignored — Veil not loaded", this.displayName);
            return false;
        }
        try
        {
            PostProcessingManager mgr = VeilRenderSystem.renderer().getPostProcessingManager();
            if (mgr == null) return false;
            boolean removed = mgr.remove(this.pipelineId);
            this.log.info("[{}] disable() -> removed={} isActive={}", this.displayName, removed, mgr.isActive(this.pipelineId));
            return removed;
        }
        catch (Throwable t)
        {
            this.log.warn("[{}] disable() threw: {}", this.displayName, t.toString(), t);
            return false;
        }
    }

    public boolean toggle()
    {
        if (this.isActive())
        {
            this.disable();
            return false;
        }
        this.enable();
        return true;
    }

}
