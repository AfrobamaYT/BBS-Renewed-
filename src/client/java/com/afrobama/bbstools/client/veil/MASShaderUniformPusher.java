// Coded by Afrobama/Claude — per-frame uniform pusher for MAS shaders.
// Veil 4.0.1 does NOT auto-bind a GameTime uniform; we provide our
// own MAS_GameTime float per-frame so animated catastrophe shaders
// (supernova ring expansion, black-hole disk swirl, grain temporal
// noise, flare rotation, …) actually animate.
//
// Hooks `VeilPostProcessingEvent.Pre`: fires BEFORE each pipeline
// runs. We check the pipeline's identifier against our known set
// and push the time uniform via `Context.getShader(id).getUniform`.
package com.afrobama.bbstools.client.veil;

import foundry.veil.api.client.render.post.PostPipeline;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import foundry.veil.api.client.render.shader.uniform.ShaderUniform;
import foundry.veil.api.event.VeilPostProcessingEvent;
import foundry.veil.platform.VeilEventPlatform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;

/**
 * Registers a Veil PostProcessing pre-event handler that pushes
 * {@code uniform float MAS_GameTime} into specific MAS shaders right
 * before they execute. Shaders that declare the uniform and want
 * animation: just reference {@code MAS_GameTime} (seconds since MC
 * start, monotonic float).
 *
 * <p>Mapping from <i>pipeline ID</i> to <i>shader ID</i> is explicit
 * because a pipeline can have multiple stages with different shaders.
 * We push the uniform only on the catastrophe / animated shaders we
 * actually wrote, not every stage.</p>
 */
public final class MASShaderUniformPusher
{
    private static final Logger LOG = LoggerFactory.getLogger("mas/uniforms");

    /** Anchor for MAS_GameTime in seconds. */
    private static final long START_NANOS = System.nanoTime();

    /** Per-pipeline intensity multiplier, default 1.0. Pushed as
     *  `MAS_Intensity` uniform alongside MAS_GameTime. Shaders that
     *  declare {@code uniform float MAS_Intensity;} multiply their
     *  additive contributions by it so the user can dial effect
     *  strength from 0 (off) to N (over-driven). */
    private static final Map<ResourceLocation, Float> INTENSITIES = new ConcurrentHashMap<>();

    /** Map pipeline-ID → shader-ID(s) that read MAS_GameTime. */
    /* All MAS shaders that receive MAS_GameTime and/or MAS_Intensity
     *  uniforms each frame. Map.ofEntries used because we have more
     *  than 10 pairs (Map.of max). */
    private static final Map<ResourceLocation, ResourceLocation> ANIMATED_SHADERS = Map.ofEntries(
        Map.entry(new ResourceLocation("bbstools", "supernova"),  new ResourceLocation("bbstools", "catastrophe/supernova")),
        Map.entry(new ResourceLocation("bbstools", "blackhole"),  new ResourceLocation("bbstools", "catastrophe/blackhole")),
        Map.entry(new ResourceLocation("bbstools", "nuke"),       new ResourceLocation("bbstools", "catastrophe/nuke")),
        Map.entry(new ResourceLocation("bbstools", "laser"),      new ResourceLocation("bbstools", "catastrophe/laser")),
        Map.entry(new ResourceLocation("bbstools", "planet"),     new ResourceLocation("bbstools", "catastrophe/planet")),
        Map.entry(new ResourceLocation("bbstools", "godrays"),    new ResourceLocation("bbstools", "volumetric/godrays")),
        Map.entry(new ResourceLocation("bbstools", "grain"),      new ResourceLocation("bbstools", "grain/grain")),
        Map.entry(new ResourceLocation("bbstools", "flare"),      new ResourceLocation("bbstools", "flare/flare")),
        Map.entry(new ResourceLocation("bbstools", "vignette"),   new ResourceLocation("bbstools", "vignette/vignette")),
        Map.entry(new ResourceLocation("bbstools", "cinematic"),  new ResourceLocation("bbstools", "cinematic/cinematic")),
        Map.entry(new ResourceLocation("bbstools", "grade"),      new ResourceLocation("bbstools", "grade/grade")),
        Map.entry(new ResourceLocation("bbstools", "dof"),        new ResourceLocation("bbstools", "dof/dof")),
        Map.entry(new ResourceLocation("bbstools", "titlefade"),  new ResourceLocation("bbstools", "cinematic_bars/titlefade")),
        Map.entry(new ResourceLocation("bbstools", "whiteout"),   new ResourceLocation("bbstools", "cinematic_bars/whiteout")),
        Map.entry(new ResourceLocation("bbstools", "crosshatch"), new ResourceLocation("bbstools", "retro/crosshatch")),
        Map.entry(new ResourceLocation("bbstools", "edge"),       new ResourceLocation("bbstools", "retro/edge")),
        Map.entry(new ResourceLocation("bbstools", "halftone"),   new ResourceLocation("bbstools", "retro/halftone")),
        Map.entry(new ResourceLocation("bbstools", "tiltshift"),  new ResourceLocation("bbstools", "dof/tiltshift")),
        Map.entry(new ResourceLocation("bbstools", "oilpaint"),   new ResourceLocation("bbstools", "retro/oilpaint")),
        Map.entry(new ResourceLocation("bbstools", "fisheye"),    new ResourceLocation("bbstools", "lens/fisheye")),
        Map.entry(new ResourceLocation("bbstools", "glitch"),     new ResourceLocation("bbstools", "glitch/glitch")),
        Map.entry(new ResourceLocation("bbstools", "masbloom"),   new ResourceLocation("bbstools", "bloom_simple/bloom_simple"))
    );

    private static boolean registered;

    private MASShaderUniformPusher() {}

    public static void register()
    {
        if (!VeilFeatureGate.isVeilLoaded())
        {
            LOG.info("register() skipped — Veil not loaded");
            return;
        }
        if (registered) return;
        registered = true;

        try
        {
            VeilEventPlatform.INSTANCE.preVeilPostProcessing(
                (pipelineId, pipeline, context) -> pushUniform(pipelineId, context));
            LOG.info("Per-frame MAS_GameTime uniform pusher registered for {} animated shaders.",
                ANIMATED_SHADERS.size());
        }
        catch (Throwable t)
        {
            LOG.warn("Failed to register VeilPostProcessingEvent.Pre handler: {}", t.toString(), t);
        }
    }

    private static void pushUniform(ResourceLocation pipelineId, PostPipeline.Context context)
    {
        /* Special case: nuke pipeline needs destroying-mc-style uniforms
         * (NUKE_POS, smokeRiseTimer, flashTimer, GameTime). Routed
         * through MASNukeEvent state. */
        if ("nuke".equals(pipelineId.getPath()) && "bbstools".equals(pipelineId.getNamespace()))
        {
            pushNukeUniforms(context);
            return;
        }
        if ("blackhole".equals(pipelineId.getPath()) && "bbstools".equals(pipelineId.getNamespace()))
        {
            pushBlackHoleUniforms(context);
            return;
        }
        if ("planet".equals(pipelineId.getPath()) && "bbstools".equals(pipelineId.getNamespace()))
        {
            pushPlanetUniforms(context);
            return;
        }
        if ("supernova".equals(pipelineId.getPath()) && "bbstools".equals(pipelineId.getNamespace()))
        {
            pushSupernovaUniforms(context);
            return;
        }
        if ("laser".equals(pipelineId.getPath()) && "bbstools".equals(pipelineId.getNamespace()))
        {
            pushLaserUniforms(context);
            return;
        }
        if ("bloom_dm".equals(pipelineId.getPath()) && "bbstools".equals(pipelineId.getNamespace()))
        {
            pushBloomDmUniforms(context);
            return;
        }
        if ("post_destroying".equals(pipelineId.getPath()) && "bbstools".equals(pipelineId.getNamespace()))
        {
            pushPostUniforms(context);
            return;
        }
        if ("meteor".equals(pipelineId.getPath()) && "bbstools".equals(pipelineId.getNamespace()))
        {
            pushMeteorUniforms(context);
            return;
        }
        if ("lightsource".equals(pipelineId.getPath()) && "bbstools".equals(pipelineId.getNamespace()))
        {
            pushLightSourceUniforms(context);
            return;
        }
        if ("vibrant_world".equals(pipelineId.getPath()) && "bbstools".equals(pipelineId.getNamespace()))
        {
            pushVibrantWorldUniforms(context);
            return;
        }

        ResourceLocation shaderId = ANIMATED_SHADERS.get(pipelineId);
        if (shaderId == null) return;

        try
        {
            ShaderProgram program = context.getShader(shaderId);
            if (program == null) return;

            float seconds = (System.nanoTime() - START_NANOS) / 1_000_000_000F;
            ShaderUniform timeUniform = program.getUniform("MAS_GameTime");
            if (timeUniform != null && timeUniform.isValid())
            {
                timeUniform.setFloat(seconds);
            }

            float intensity = INTENSITIES.getOrDefault(pipelineId, 1.0F);
            ShaderUniform intensityUniform = program.getUniform("MAS_Intensity");
            if (intensityUniform != null && intensityUniform.isValid())
            {
                intensityUniform.setFloat(intensity);
            }
        }
        catch (Throwable t)
        {
            /* Don't log per-frame to avoid spam. */
        }
    }

    /** Push uniforms specific to the ported destroying-mc nuke shader.
     *  Triggers when /mas nuke pipeline is active and MASNukeEvent has
     *  been triggered. If event not active, smokeRiseTimer stays 0 and
     *  the shader's `if (smokeRiseTimer > 0.0)` check skips the ray-march. */
    private static void pushNukeUniforms(PostPipeline.Context context)
    {
        try
        {
            ShaderProgram program = context.getShader(new ResourceLocation("bbstools", "nuke/nuke"));
            if (program == null) return;

            float gameTime = (System.nanoTime() - START_NANOS) / 1_000_000_000F;
            float smokeRise = MASNukeEvent.getSmokeRiseTimer();
            float flash = MASNukeEvent.getFlashTimer();
            net.minecraft.world.phys.Vec3 pos = MASNukeEvent.getPosition();

            ShaderUniform u;
            u = program.getUniform("NUKE_POS");
            if (u != null && u.isValid()) u.setVector((float)pos.x, (float)pos.y, (float)pos.z);
            u = program.getUniform("smokeRiseTimer");
            if (u != null && u.isValid()) u.setFloat(smokeRise);
            u = program.getUniform("flashTimer");
            if (u != null && u.isValid()) u.setFloat(flash);
            u = program.getUniform("GameTime");
            if (u != null && u.isValid()) u.setFloat(gameTime);
        }
        catch (Throwable t)
        {
            /* Don't log per-frame to avoid spam. */
        }
    }

    /** Push uniforms for all 4 shaders in the bloom_dm pipeline.
     *  blur/horizontal + blur/vertical need xLimit + blurStrength + ScreenSize
     *  or they only write the left-edge column (black screen result).
     *  mipmaptree + bloom need ScreenSize for their sampling math. */
    private static void pushBloomDmUniforms(PostPipeline.Context context)
    {
        try
        {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            float screenW = (mc != null && mc.getWindow() != null) ? mc.getWindow().getWidth() : 1920;
            float screenH = (mc != null && mc.getWindow() != null) ? mc.getWindow().getHeight() : 1080;

            ResourceLocation[] bloomShaders = new ResourceLocation[]
            {
                new ResourceLocation("bbstools", "bloom_dm/mipmaptree"),
                new ResourceLocation("bbstools", "bloom_dm/blur/horizontal"),
                new ResourceLocation("bbstools", "bloom_dm/blur/vertical"),
                new ResourceLocation("bbstools", "bloom_dm/bloom")
            };

            for (ResourceLocation sid : bloomShaders)
            {
                ShaderProgram program = context.getShader(sid);
                if (program == null) continue;
                ShaderUniform u;
                u = program.getUniform("ScreenSize");
                if (u != null && u.isValid()) u.setVector(screenW, screenH);
                u = program.getUniform("xLimit");
                if (u != null && u.isValid()) u.setFloat(1.0F);   /* process whole screen */
                u = program.getUniform("blurStrength");
                if (u != null && u.isValid()) u.setFloat(1.0F);   /* default blur amount */
            }
        }
        catch (Throwable t)
        {
            com.afrobama.bbstools.client.debug.master.jvm.CatchBlockTracker
                .reportSilent("MASShaderUniformPusher.pushBloomDmUniforms", t);
        }
    }

    /** Push uniforms for the placeable Light Source effect.
     *  LIGHT_POS (world), inner/outer radii, intensity, GameTime. */
    private static void pushLightSourceUniforms(PostPipeline.Context context)
    {
        try
        {
            ShaderProgram program = context.getShader(new ResourceLocation("bbstools", "lightsource/lightsource"));
            if (program == null) return;

            float gameTime = (System.nanoTime() - START_NANOS) / 1_000_000_000F;
            net.minecraft.world.phys.Vec3 pos = MASLightSourceEvent.getPosition();

            ShaderUniform u;
            u = program.getUniform("LIGHT_POS");
            if (u != null && u.isValid()) u.setVector((float)pos.x, (float)pos.y, (float)pos.z);
            u = program.getUniform("LIGHT_RADIUS_INNER");
            if (u != null && u.isValid()) u.setFloat(MASLightSourceEvent.getInnerRadius());
            u = program.getUniform("LIGHT_RADIUS_OUTER");
            if (u != null && u.isValid()) u.setFloat(MASLightSourceEvent.getOuterRadius());
            u = program.getUniform("LIGHT_INTENSITY");
            if (u != null && u.isValid()) u.setFloat(MASLightSourceEvent.getIntensity());
            u = program.getUniform("GameTime");
            if (u != null && u.isValid()) u.setFloat(gameTime);
        }
        catch (Throwable t)
        {
            com.afrobama.bbstools.client.debug.master.jvm.CatchBlockTracker
                .reportSilent("MASShaderUniformPusher.pushLightSourceUniforms", t);
        }
    }

    /** Push uniforms for the CUSTOM meteor effect (not destroying-mc port).
     *  Drives METEOR_POS + METEOR_DIR + IMPACT_POS + fallProgress +
     *  afterImpactSec + flashTimer + GameTime from MASMeteorEvent state. */
    private static void pushMeteorUniforms(PostPipeline.Context context)
    {
        try
        {
            ShaderProgram program = context.getShader(new ResourceLocation("bbstools", "meteor_custom/meteor_custom"));
            if (program == null) return;

            /* Trigger camera shake on first frame post-impact. */
            MASMeteorEvent.tickShakeOnImpact();

            float gameTime = (System.nanoTime() - START_NANOS) / 1_000_000_000F;
            net.minecraft.world.phys.Vec3 mpos = MASMeteorEvent.getMeteorPos();
            net.minecraft.world.phys.Vec3 mdir = MASMeteorEvent.getMeteorDir();
            net.minecraft.world.phys.Vec3 ipos = MASMeteorEvent.getImpactPos();
            float fp = MASMeteorEvent.getFallProgress();
            float ai = MASMeteorEvent.getAfterImpactSec();
            float ft = MASMeteorEvent.getFlashTimer();

            ShaderUniform u;
            u = program.getUniform("METEOR_POS");
            if (u != null && u.isValid()) u.setVector((float)mpos.x, (float)mpos.y, (float)mpos.z);
            u = program.getUniform("METEOR_DIR");
            if (u != null && u.isValid()) u.setVector((float)mdir.x, (float)mdir.y, (float)mdir.z);
            u = program.getUniform("IMPACT_POS");
            if (u != null && u.isValid()) u.setVector((float)ipos.x, (float)ipos.y, (float)ipos.z);
            u = program.getUniform("fallProgress");
            if (u != null && u.isValid()) u.setFloat(fp);
            u = program.getUniform("afterImpactSec");
            if (u != null && u.isValid()) u.setFloat(ai);
            u = program.getUniform("flashTimer");
            if (u != null && u.isValid()) u.setFloat(ft);
            u = program.getUniform("GameTime");
            if (u != null && u.isValid()) u.setFloat(gameTime);
        }
        catch (Throwable t)
        {
            com.afrobama.bbstools.client.debug.master.jvm.CatchBlockTracker
                .reportSilent("MASShaderUniformPusher.pushMeteorUniforms", t);
        }
    }

    /** Push uniforms specific to the ported destroying-mc post shader.
     *  MAS_Intensity drives glitchTime. ScreenSize + GameTime auto-set.
     *  DOF + black-screen toggleable via INTENSITIES special values
     *  (>2 = enable DOF, hardcoded for now). */
    private static void pushPostUniforms(PostPipeline.Context context)
    {
        try
        {
            ShaderProgram program = context.getShader(new ResourceLocation("bbstools", "post/post"));
            if (program == null) return;

            float gameTime = (System.nanoTime() - START_NANOS) / 1_000_000_000F;
            float intensity = INTENSITIES.getOrDefault(new ResourceLocation("bbstools", "post_destroying"), 0.0F);

            ShaderUniform u;
            u = program.getUniform("GameTime");
            if (u != null && u.isValid()) u.setFloat(gameTime);
            u = program.getUniform("glitchTime");
            if (u != null && u.isValid()) u.setFloat(intensity);
            /* DOF + black-screen disabled by default; can extend via more intensity slots */
            u = program.getUniform("enabledDepthOfField");
            if (u != null && u.isValid()) u.setInt(0);
            u = program.getUniform("enabledBlackScreen");
            if (u != null && u.isValid()) u.setInt(0);
            u = program.getUniform("centerDepth");
            if (u != null && u.isValid()) u.setFloat(0.5F);
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc != null && mc.getWindow() != null)
            {
                u = program.getUniform("ScreenSize");
                if (u != null && u.isValid()) u.setVector((float)mc.getWindow().getWidth(), (float)mc.getWindow().getHeight());
            }
        }
        catch (Throwable t)
        {
            com.afrobama.bbstools.client.debug.master.jvm.CatchBlockTracker
                .reportSilent("MASShaderUniformPusher.pushPostUniforms", t);
        }
    }

    /** Push uniforms specific to the ported destroying-mc cracks (laser)
     *  shader. Pushes CENTER_POS + laserLength + cracksTime + flashTimer
     *  + GameTime. */
    private static void pushLaserUniforms(PostPipeline.Context context)
    {
        try
        {
            ShaderProgram program = context.getShader(new ResourceLocation("bbstools", "cracks/cracks"));
            if (program == null) return;

            float gameTime = (System.nanoTime() - START_NANOS) / 1_000_000_000F;
            net.minecraft.world.phys.Vec3 pos = MASLaserEvent.getPosition();
            float laserLen = MASLaserEvent.getLaserLength();
            float crackT = MASLaserEvent.getCracksTime();
            float flash = MASLaserEvent.getFlashTimer();

            ShaderUniform u;
            u = program.getUniform("CENTER_POS");
            if (u != null && u.isValid()) u.setVector((float)pos.x, (float)pos.z);
            u = program.getUniform("laserLength");
            if (u != null && u.isValid()) u.setFloat(laserLen);
            u = program.getUniform("cracksTime");
            if (u != null && u.isValid()) u.setFloat(crackT);
            u = program.getUniform("flashTimer");
            if (u != null && u.isValid()) u.setFloat(flash);
            u = program.getUniform("GameTime");
            if (u != null && u.isValid()) u.setFloat(gameTime);
        }
        catch (Throwable t)
        {
            com.afrobama.bbstools.client.debug.master.jvm.CatchBlockTracker
                .reportSilent("MASShaderUniformPusher.pushLaserUniforms", t);
        }
    }

    /** Push uniforms specific to the ported destroying-mc sky shader,
     *  used as our supernova effect. Pushes supernovaTimer +
     *  flashTimer + explosionTimer + GameTime. */
    private static void pushSupernovaUniforms(PostPipeline.Context context)
    {
        try
        {
            ShaderProgram program = context.getShader(new ResourceLocation("bbstools", "sky/sky"));
            if (program == null) return;

            float gameTime = (System.nanoTime() - START_NANOS) / 1_000_000_000F;
            float st = MASSupernovaEvent.getSupernovaTimer();
            float ft = MASSupernovaEvent.getFlashTimer();
            float et = MASSupernovaEvent.getExplosionTimer();

            ShaderUniform u;
            u = program.getUniform("supernovaTimer");
            if (u != null && u.isValid()) u.setFloat(st);
            u = program.getUniform("flashTimer");
            if (u != null && u.isValid()) u.setFloat(ft);
            u = program.getUniform("explosionTimer");
            if (u != null && u.isValid()) u.setFloat(et);
            u = program.getUniform("GameTime");
            if (u != null && u.isValid()) u.setFloat(gameTime);
        }
        catch (Throwable t)
        {
            com.afrobama.bbstools.client.debug.master.jvm.CatchBlockTracker
                .reportSilent("MASShaderUniformPusher.pushSupernovaUniforms", t);
        }
    }

    /** Push uniforms specific to the ported destroying-mc planet shader.
     *  Pushes planetFallTimer + flashTimer + GameTime. Planet has no
     *  POS uniform — its position is computed in-shader from
     *  VeilCamera.CameraPosition + (20000, varies, 0). */
    private static void pushPlanetUniforms(PostPipeline.Context context)
    {
        try
        {
            ShaderProgram program = context.getShader(new ResourceLocation("bbstools", "planet/planet"));
            if (program == null) return;

            float gameTime = (System.nanoTime() - START_NANOS) / 1_000_000_000F;
            float fall = MASPlanetEvent.getPlanetFallTimer();
            float flash = MASPlanetEvent.getFlashTimer();

            ShaderUniform u;
            u = program.getUniform("planetFallTimer");
            if (u != null && u.isValid()) u.setFloat(fall);
            u = program.getUniform("flashTimer");
            if (u != null && u.isValid()) u.setFloat(flash);
            u = program.getUniform("GameTime");
            if (u != null && u.isValid()) u.setFloat(gameTime);
        }
        catch (Throwable t)
        {
            com.afrobama.bbstools.client.debug.master.jvm.CatchBlockTracker
                .reportSilent("MASShaderUniformPusher.pushPlanetUniforms", t);
        }
    }

    /** Push uniforms specific to the ported destroying-mc blackhole
     *  shader. Pushes BH_POS + GameTime when MASBlackHoleEvent is active.
     *  If event not active, BH_POS stays at last set value but the
     *  pipeline is normally disabled too. */
    private static void pushBlackHoleUniforms(PostPipeline.Context context)
    {
        try
        {
            ShaderProgram program = context.getShader(new ResourceLocation("bbstools", "blackhole/black_hole"));
            if (program == null) return;

            float gameTime = (System.nanoTime() - START_NANOS) / 1_000_000_000F;
            net.minecraft.world.phys.Vec3 pos = MASBlackHoleEvent.getPosition();

            ShaderUniform u;
            u = program.getUniform("BH_POS");
            if (u != null && u.isValid()) u.setVector((float)pos.x, (float)pos.y, (float)pos.z);
            u = program.getUniform("GameTime");
            if (u != null && u.isValid()) u.setFloat(gameTime);
        }
        catch (Throwable t)
        {
            com.afrobama.bbstools.client.debug.master.jvm.CatchBlockTracker
                .reportSilent("MASShaderUniformPusher.pushBlackHoleUniforms", t);
        }
    }

    /** Push uniforms for vibrant_world: SunDir (from world.getSkyAngle),
     *  SunIntensity (from MAS_Intensity slot), SkySat (saturation
     *  multiplier — uses intensity*1.25 so /mas intensity vibrant 0
     *  fully disables tint while default 1.0 gives a moderate boost). */
    private static void pushVibrantWorldUniforms(PostPipeline.Context context)
    {
        try
        {
            ShaderProgram program = context.getShader(new ResourceLocation("bbstools", "vibrant_world/vibrant_world"));
            if (program == null) return;

            /* Compute sun direction from MC world time. MC sun orbits
             * around the +X-axis. skyAngle ∈ [0,1] where 0.0=sunrise,
             * 0.25=noon, 0.5=sunset, 0.75=midnight. Map to (x,y,0).
             * At noon: sun is at +Y. At sunrise: +X horizon. At sunset:
             * -X horizon. */
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            float sunX = 1.0F, sunY = 0.5F, sunZ = 0.3F;
            if (mc != null && mc.level != null)
            {
                float skyAngle = mc.level.getTimeOfDay(mc.getTimer().getGameTimeDeltaPartialTick(false));
                double rad = (skyAngle - 0.25) * 2.0 * Math.PI;
                sunX = (float) Math.sin(rad);
                sunY = (float) Math.cos(rad);
                sunZ = 0.0F;
            }

            float intensity = INTENSITIES.getOrDefault(new ResourceLocation("bbstools", "vibrant_world"), 1.0F);

            ShaderUniform u;
            u = program.getUniform("SunDir");
            if (u != null && u.isValid()) u.setVector(sunX, sunY, sunZ);
            u = program.getUniform("SunIntensity");
            if (u != null && u.isValid()) u.setFloat(intensity);
            u = program.getUniform("SkySat");
            if (u != null && u.isValid()) u.setFloat(1.0F + 0.25F * intensity);
        }
        catch (Throwable t)
        {
            com.afrobama.bbstools.client.debug.master.jvm.CatchBlockTracker
                .reportSilent("MASShaderUniformPusher.pushVibrantWorldUniforms", t);
        }
    }

    /** Set the intensity multiplier for a pipeline. Values clamped to
     *  [0.0, 3.0] — beyond 3 produces wild over-bright output. */
    public static void setIntensity(ResourceLocation pipelineId, float value)
    {
        INTENSITIES.put(pipelineId, Math.max(0.0F, Math.min(3.0F, value)));
        LOG.info("Set intensity for {} -> {}", pipelineId, value);
        /* Forward to event state for effects that consume intensity directly. */
        if ("lightsource".equals(pipelineId.getPath()) && "bbstools".equals(pipelineId.getNamespace()))
        {
            MASLightSourceEvent.setIntensity(value);
        }
    }

    public static float getIntensity(ResourceLocation pipelineId)
    {
        return INTENSITIES.getOrDefault(pipelineId, 1.0F);
    }

}
