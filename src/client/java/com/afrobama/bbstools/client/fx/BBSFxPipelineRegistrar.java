// Coded by Afrobama/Claude — register + enable our post-FX pipelines
// with Veil's PostProcessingManager. The pipeline JSON files in
// assets/bbstools/pinwheel/post/ are auto-loaded by Veil at resource-
// reload time, but they're NOT active until something calls
// PostProcessingManager.add(id). This registrar does that on boot
// AND when the user toggles via /bbstools fx enable / disable.
package com.afrobama.bbstools.client.fx;

import com.afrobama.bbstools.diag.BBSDiag;

import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.dynamicbuffer.DynamicBufferType;
import foundry.veil.api.client.render.post.PostProcessingManager;
import foundry.veil.api.event.VeilRenderLevelStageEvent;
import foundry.veil.platform.VeilEventPlatform;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BBSFxPipelineRegistrar
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/fx/registrar");

    public static final ResourceLocation SHADOWS_PIPELINE = new ResourceLocation("bbstools", "bbs_shadows");
    public static final ResourceLocation BLOOM_PIPELINE   = new ResourceLocation("bbstools", "bbs_bloom");
    public static final ResourceLocation FINAL_PIPELINE   = new ResourceLocation("bbstools", "bbs_final");
    /** Nuke pipeline — added alongside the others. The shader has a
     *  smokeRiseTimer > 0 guard so when no nuke is active it falls
     *  through to a single sampler copy (near-zero GPU cost). */
    public static final ResourceLocation NUKE_PIPELINE    = new ResourceLocation("bbstools", "bbs_nuke");
    /** REALITY GLITCH pipeline — original effect (not in destroying-mc).
     *  Shader has a glitchIntensity > 0 guard so idle = pass-through. */
    public static final ResourceLocation GLITCH_PIPELINE  = new ResourceLocation("bbstools", "bbs_glitch");
    /** ANTIMATTER IMPLOSION pipeline — original effect (not in
     *  destroying-mc). Shader has antimatterIntensity > 0 guard. */
    public static final ResourceLocation ANTIMATTER_PIPELINE = new ResourceLocation("bbstools", "bbs_antimatter");
    /** CRYSTALLIZATION WAVE pipeline — third original effect. Shader
     *  has crystalIntensity > 0 guard so idle = passthrough. */
    public static final ResourceLocation CRYSTAL_PIPELINE    = new ResourceLocation("bbstools", "bbs_crystal");

    /** ResourceLocation used to register our deferred-buffer enable request
     *  with Veil. Veil keeps these per-mod so multiple mods asking for
     *  the same buffer don't conflict. */
    private static final ResourceLocation BUFFERS_ID = new ResourceLocation("bbstools", "fx_deferred");

    /** Master enable flag — toggled via /bbstools fx enable/disable.
     *  When true, the registrar adds our pipelines to Veil's active
     *  list each tick if not already present. When false, they get
     *  removed. Default OFF — we don't auto-take-over the user's
     *  vanilla look until they opt in. */
    public static volatile boolean ENABLED = false;

    private static boolean registered;
    private static boolean lastEnabledState;
    /** One-shot flag — destroying-mc pattern. enableBuffers() must be
     *  called LAZY on the first VeilRenderLevelStageEvent fire, not
     *  eagerly on renderer-available (that fires too early and Veil
     *  returns ok=false). */
    private static boolean buffersEnabled;
    /** Tracks whether the user's Iris shaderpack was active on the
     *  last sync. State change triggers a pipeline re-eval. */
    private static boolean lastShaderpackState;

    /** Reflectively probe Iris for an active shaderpack. We don't
     *  compile-depend on Iris, so reflection + try-catch. Returns
     *  false on any error (treat absence as "no shaderpack"). */
    public static boolean isIrisShaderpackActive()
    {
        try
        {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object inst = api.getMethod("getInstance").invoke(null);
            Object res = api.getMethod("isShaderPackInUse").invoke(inst);
            return res instanceof Boolean b && b;
        }
        catch (Throwable ignored)
        {
            return false;
        }
    }

    /** True iff our FX pipelines should currently be added to Veil's
     *  PostProcessingManager. We back off when Iris has an active
     *  shaderpack so the pack can render undisturbed (user goal:
     *  "echte iris shader anbekommen, post-process aus dem weg"). */
    private static boolean shouldBeActive()
    {
        return ENABLED && !isIrisShaderpackActive();
    }

    private BBSFxPipelineRegistrar() {}

    public static synchronized void register()
    {
        if (registered) return;
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(mc -> sync());

        /* CRITICAL: enable Veil's dynamic G-buffers (ALBEDO, NORMAL,
         *  LIGHT_UV, LIGHT_COLOR, DEBUG). Without this call, the
         *  veil:dynamic_main framebuffer never gets populated by the
         *  world render → shadows.fsh's AlbedoSampler reads black →
         *  the entire FX chain pumps black → user sees black screen.
         *
         *  destroying-mc pattern: enable LAZY on the first
         *  VeilRenderLevelStageEvent fire, NOT eagerly on
         *  renderer-available. The latter fires before Veil's
         *  framebuffers are fully initialized so the call returns
         *  ok=false (which is exactly what iter 54 hit — confirmed
         *  in logs as `event=enable-buffers ok=false`). On the first
         *  render-stage callback, the renderer is fully alive and
         *  the call succeeds.
         *
         *  Auto-enable our pipelines on the same first-stage fire so
         *  the user doesn't have to run /bbstools fx enable before
         *  the world loads — the pipelines + buffers are live BEFORE
         *  the first world frame the user sees. */
        try
        {
            VeilEventPlatform.INSTANCE.onVeilRenderLevelStage(
                (stage, levelRenderer, bufferSource, matrixStack, frustumMatrix,
                 projectionMatrix, renderTick, deltaTracker, camera, frustum) ->
                {
                    if (buffersEnabled) return;
                    buffersEnabled = true;
                    try
                    {
                        /* (1) Populate the deferred G-buffer. */
                        boolean buffersOk = VeilRenderSystem.renderer()
                            .enableBuffers(BUFFERS_ID, DynamicBufferType.values());
                        BBSDiag.feature("fx-registrar").event("enable-buffers")
                            .field("ok", buffersOk)
                            .field("types", DynamicBufferType.values().length)
                            .emit();

                        /* (2) Auto-add our FX pipelines so they're live
                         *  from the FIRST world frame the user sees —
                         *  per user mandate "die shader pipeline schon
                         *  applied ist bevor ich in die welt lade".
                         *  destroying-mc applies its post-FX pipelines
                         *  similarly at first level-render. The user
                         *  can still /bbstools fx disable to revert. */
                        PostProcessingManager mgr = VeilRenderSystem.renderer()
                            .getPostProcessingManager();
                        if (mgr != null)
                        {
                            /* iter 68: auto-enable removed per user
                             *  request ("mach bitte dieses veil post
                             *  procces beim welt start aus das nervt
                             *  gerade"). Now opt-in only: user runs
                             *  /bbstools fx enable when they actually
                             *  want the post-FX pipeline. First-stage
                             *  callback still records shaderpack state
                             *  for sync() but does NOT add pipelines. */
                            lastShaderpackState = isIrisShaderpackActive();
                            BBSDiag.feature("fx-registrar").event("ready-opt-in")
                                .field("note", "use /bbstools fx enable to activate")
                                .field("shaderpack", lastShaderpackState)
                                .emit();
                        }
                        else
                        {
                            BBSDiag.feature("fx-registrar").sticky("auto-enable-no-mgr")
                                .field("err", "PostProcessingManager null on first stage").asWarn().emit();
                        }
                    }
                    catch (Throwable t)
                    {
                        BBSDiag.feature("fx-registrar").sticky("enable-buffers-err")
                            .field("err", t.toString()).asWarn().emit();
                    }
                }
            );
        }
        catch (Throwable t)
        {
            BBSDiag.feature("fx-registrar").sticky("event-register-err")
                .field("err", t.toString()).asWarn().emit();
        }

        BBSDiag.feature("fx-registrar").event("register").emit();
    }

    /** Per-tick reconcile — match the PostProcessingManager state to
     *  shouldBeActive() (ENABLED && no-iris-pack). Re-evaluates when
     *  the user flips Iris shaderpack on/off live. */
    private static void sync()
    {
        boolean want = shouldBeActive();
        boolean packNow = isIrisShaderpackActive();

        /* Fast-path no-op when nothing changed. */
        if (want == lastEnabledState && packNow == lastShaderpackState) return;
        lastEnabledState = want;
        lastShaderpackState = packNow;

        try
        {
            PostProcessingManager mgr = VeilRenderSystem.renderer().getPostProcessingManager();
            if (mgr == null)
            {
                BBSDiag.feature("fx-registrar").sticky("no-mgr")
                    .field("err", "PostProcessingManager null at sync").asWarn().emit();
                return;
            }
            if (want)
            {
                addIfMissing(mgr, SHADOWS_PIPELINE);
                addIfMissing(mgr, NUKE_PIPELINE);
                addIfMissing(mgr, GLITCH_PIPELINE);
                addIfMissing(mgr, ANTIMATTER_PIPELINE);
                addIfMissing(mgr, CRYSTAL_PIPELINE);
                addIfMissing(mgr, BLOOM_PIPELINE);
                addIfMissing(mgr, FINAL_PIPELINE);
                BBSDiag.feature("fx-registrar").event("enabled").emit();
            }
            else
            {
                mgr.remove(SHADOWS_PIPELINE);
                mgr.remove(NUKE_PIPELINE);
                mgr.remove(GLITCH_PIPELINE);
                mgr.remove(ANTIMATTER_PIPELINE);
                mgr.remove(CRYSTAL_PIPELINE);
                mgr.remove(BLOOM_PIPELINE);
                mgr.remove(FINAL_PIPELINE);
                BBSDiag.feature("fx-registrar").event("disabled")
                    .field("reason", packNow ? "iris-shaderpack-active" : "user-disabled")
                    .emit();
            }
        }
        catch (Throwable t)
        {
            BBSDiag.feature("fx-registrar").sticky("sync-error")
                .field("err", t.toString()).asWarn().emit();
        }
    }

    private static void addIfMissing(PostProcessingManager mgr, ResourceLocation id)
    {
        if (!mgr.isActive(id))
        {
            boolean ok = mgr.add(id);
            BBSDiag.feature("fx-registrar").event("add")
                .field("id", id.toString()).field("ok", ok).emit();
        }
    }

    public static void setEnabled(boolean enabled)
    {
        ENABLED = enabled;
    }

}
