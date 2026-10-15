// Coded by Afrobama/Claude — runtime detection + boot diagnostics for the
// Veil integration. Logs verbosely so compat issues are visible immediately
// in bbstools-console.log without needing to add temp prints later.
package com.afrobama.bbstools.client.veil;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Central feature-gate for the Veil integration. Every Veil-dependent
 * feature in BBSTools/MAS checks {@link #isVeilLoaded()} before doing
 * anything that touches the Veil API.
 *
 * <p>Per project policy "log-first": this class logs an extensive boot
 * banner showing every mod relevant to the rendering pipeline, so we
 * can diagnose conflicts the moment the game starts without re-running.
 * Once a feature is verified working we can dial logging down — for now
 * everything stays INFO-level for fast triage.</p>
 *
 * <h3>Why eager detection at boot:</h3>
 * <p>Lazy detection at first-use means crashes can happen far from the
 * root cause. Eager detection + a single banner log lets us read the
 * sandbox log once and immediately know if Veil/Iris/Sodium/PhysicsMod
 * are all present + their versions — critical for the Phase 0 compat
 * validation step of the plan.</p>
 */
public final class VeilFeatureGate
{
    private static final Logger LOG = LoggerFactory.getLogger("mas/veil-gate");

    /** Mods we explicitly watch for compat-relevant interaction. */
    private static final String[] WATCHED_MODS = {
        "veil",
        "iris",
        "sodium",
        "embeddium",
        "physicsmod",
        "indium",
        "bbs",
        "bbstools"
    };

    private static boolean initialized;
    private static boolean veilLoaded;
    private static String veilVersion = "<absent>";
    private static final Map<String, String> WATCHED_VERSIONS = new LinkedHashMap<>();

    private VeilFeatureGate()
    {
    }

    /**
     * Run once at client init. Detect Veil + watched mods, log boot
     * banner. Safe to call multiple times (idempotent).
     */
    public static void init()
    {
        if (initialized) return;
        initialized = true;

        FabricLoader loader = FabricLoader.getInstance();

        for (String id : WATCHED_MODS)
        {
            Optional<ModContainer> mod = loader.getModContainer(id);
            String version = mod.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("<absent>");
            WATCHED_VERSIONS.put(id, version);
        }

        veilLoaded = loader.isModLoaded("veil");
        if (veilLoaded)
        {
            veilVersion = WATCHED_VERSIONS.get("veil");
        }

        logBootBanner();
        runSanityProbes();
        registerDebugDumper();
    }

    /**
     * Hook the central debug registry so /bbstools debug-everything
     * includes a "Veil status" section without needing to remember to
     * edit BBSDebugEverything. Late-binding into the registry to avoid
     * order-of-init issues.
     */
    private static void registerDebugDumper()
    {
        try
        {
            com.afrobama.bbstools.client.debug.BBSDebugRegistry.register("Veil status", out ->
            {
                out.append("Veil loaded: ").append(veilLoaded).append('\n');
                out.append("Veil version: ").append(veilVersion).append('\n');

                if (veilLoaded)
                {
                    /* Late-bound runtime probes via VeilBridge — fail-soft if
                     *  Veil's renderer hasn't initialised yet. */
                    out.append("Runtime renderer: ")
                       .append(com.afrobama.bbstools.client.veil.VeilBridge.getRuntimeRendererInfo())
                       .append('\n');
                    out.append("Post-FX pipelines registered: ")
                       .append(com.afrobama.bbstools.client.veil.VeilBridge.countPostProcessingPipelines())
                       .append('\n');
                    out.append("Shader programs registered: ")
                       .append(com.afrobama.bbstools.client.veil.VeilBridge.countShaderPrograms())
                       .append('\n');
                }

                out.append("Compat-relevant mods:\n");
                for (Map.Entry<String, String> e : WATCHED_VERSIONS.entrySet())
                {
                    out.append("  ").append(e.getKey()).append(" = ").append(e.getValue()).append('\n');
                }
            });
            LOG.info("Registered 'Veil status' section into BBSDebugRegistry");
        }
        catch (Throwable t)
        {
            LOG.warn("Failed to register Veil debug dumper: {}", t.toString());
        }
    }

    private static void logBootBanner()
    {
        LOG.info("===== MAS/Veil feature gate boot banner =====");
        LOG.info("Veil loaded: {} (version: {})", veilLoaded, veilVersion);
        LOG.info("Render-pipeline mods detected:");

        for (Map.Entry<String, String> e : WATCHED_VERSIONS.entrySet())
        {
            boolean present = !"<absent>".equals(e.getValue());
            LOG.info("  [{}] {} = {}", present ? "X" : " ", e.getKey(), e.getValue());
        }

        if (!veilLoaded)
        {
            LOG.warn("Veil not loaded — every Veil-dependent feature in MAS will gate to OFF. " +
                     "Drop foundry's veil-fabric-1.21.1-*.jar into mods/ to enable.");
        }
        else
        {
            LOG.info("Veil is active. Veil-dependent features may now register.");
        }

        boolean irisHere = !"<absent>".equals(WATCHED_VERSIONS.get("iris"));
        boolean sodiumHere = !"<absent>".equals(WATCHED_VERSIONS.get("sodium")) ||
                             !"<absent>".equals(WATCHED_VERSIONS.get("embeddium"));
        boolean physicsHere = !"<absent>".equals(WATCHED_VERSIONS.get("physicsmod"));

        if (veilLoaded && irisHere)
        {
            LOG.info("Iris + Veil both present. Veil bundles iris compat mixins (PipelineManagerMixin) — expecting coexistence.");
        }
        if (veilLoaded && sodiumHere)
        {
            LOG.info("Sodium/Embeddium + Veil both present. Veil bundles sodium compat mixins (OcclusionCuller, RenderSection, SodiumWorldRenderer) — expecting coexistence.");
        }
        if (veilLoaded && physicsHere)
        {
            LOG.info("PhysicsMod + Veil both present. No official compat — both touch LivingEntityRenderer.render. Watch for double-hook anomalies during ragdoll generation.");
        }

        LOG.info("===== end MAS/Veil feature gate boot banner =====");
    }

    /**
     * Probe Veil's API surface at boot to detect classloader/refmap
     * issues early. We don't actually CALL anything yet — just verify
     * the classes load. If a probe throws, the corresponding feature
     * is unsafe to register.
     */
    private static void runSanityProbes()
    {
        if (!veilLoaded) return;

        probeClass("foundry.veil.api.client.render.post.PostProcessingManager",
            "post-processing manager (Bloom/DOF/Color-Grade base)");
        probeClass("foundry.veil.api.client.render.shader.program.ShaderProgram",
            "custom shader programs (Shader-Clip base)");
        probeClass("foundry.veil.api.client.render.framebuffer.AdvancedFbo",
            "advanced framebuffer (Multi-Layer-Compositing base)");
        probeClass("foundry.veil.api.client.render.light.renderer.LightRenderer",
            "dynamic light renderer (Actor-Lights base)");
        probeClass("foundry.veil.api.client.necromancer.Skeleton",
            "Necromancer skeleton (IK-Actor base)");
    }

    private static void probeClass(String fqcn, String featureLabel)
    {
        try
        {
            Class<?> c = Class.forName(fqcn);
            LOG.info("  probe OK : {} ({}) -> {}", featureLabel, fqcn, c.getSimpleName());
        }
        catch (Throwable t)
        {
            LOG.warn("  probe FAIL: {} ({}) — classloader issue: {}", featureLabel, fqcn, t.toString());
        }
    }

    /** True iff Veil's mod ID is loaded. Cheap; cached after init(). */
    public static boolean isVeilLoaded()
    {
        return veilLoaded;
    }

    /** Friendly Veil version string, or {@code "<absent>"}. */
    public static String getVeilVersion()
    {
        return veilVersion;
    }

    /** True if PhysicsMod is also present — affects ragdoll feature decisions. */
    public static boolean isPhysicsModLoaded()
    {
        return !"<absent>".equals(WATCHED_VERSIONS.get("physicsmod"));
    }

    /** True if Iris is present — affects shader pipeline decisions. */
    public static boolean isIrisLoaded()
    {
        return !"<absent>".equals(WATCHED_VERSIONS.get("iris"));
    }

}
