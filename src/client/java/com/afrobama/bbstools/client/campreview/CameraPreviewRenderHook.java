// Coded by Afrobama/Claude — BBS Renewed Camera Preview Phase 2
package com.afrobama.bbstools.client.campreview;

import com.afrobama.bbstools.client.mixin.CameraInvokerMixin;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;

import com.mojang.blaze3d.systems.RenderSystem;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Real second-pass world render for each {@link CameraPreview}.
 *
 * <p>Runs on {@code WorldRenderEvents.LAST}, after the main framebuffer
 * is populated. For each enabled preview:
 * <ol>
 *   <li>Save current Camera pose</li>
 *   <li>Mutate the singleton Camera to the preview's pose
 *       (via {@code CameraInvokerMixin})</li>
 *   <li>Bind preview's framebuffer</li>
 *   <li>Build a projection matrix matching the framebuffer aspect</li>
 *   <li>Call {@code mc.worldRenderer.render(...)} directly — bypasses
 *       the {@code WorldRenderEvents} re-entry that {@code gameRenderer.renderWorld}
 *       would have triggered</li>
 *   <li>Restore main framebuffer + Camera state</li>
 * </ol></p>
 *
 * <p>Risk areas (Phase 2 known limitations):</p>
 * <ul>
 *   <li>Frustum culling state is shared between passes — the second pass
 *       re-applies it for the new camera but transitions may stutter</li>
 *   <li>Iris/Sodium shadow passes intercept early; previews may render
 *       without shader effects (vanilla lighting only) for now</li>
 *   <li>Particles + animated entities tick once per frame, so previews
 *       show the same animation state as the main view (intended)</li>
 * </ul>
 */
public final class CameraPreviewRenderHook
{
    private static final Logger LOG = LoggerFactory.getLogger("bbs/cam-preview/hook");

    /** Per-frame guard against accidental recursion if a third-party
     *  mod hooks LAST and also calls world render. */
    private static boolean RENDERING_PREVIEW;

    private CameraPreviewRenderHook() {}

    public static boolean isRenderingPreview()
    {
        return RENDERING_PREVIEW;
    }

    /* Coded by Afrobama/Claude — performance pass for the public fork.
     *  See {@link mchorse.bbs_mod.client.campreview.CameraPreviewPerf}
     *  for the per-frame skip logic that early-returns when nobody is
     *  looking at the previews or when the window is offscreen / shadow
     *  pass / etc. Each early-return saves a full preview blit + the
     *  per-preview camera mutation overhead. */
    public static void onWorldRenderLast(WorldRenderContext ctx)
    {
        if (CameraPreviewRegistry.list().isEmpty()) return;
        if (RENDERING_PREVIEW) return;
        if (CameraPreviewPerf.shouldSkipFrame()) return;

        /* Skip the blit fallback when the Phase-2.6 pre-pass mixin
         *  already ran a real second world render for each preview this
         *  frame. Otherwise we'd overwrite the real POV with the main
         *  view, defeating the whole purpose. */
        if (com.afrobama.bbstools.client.util.PreviewPassState.isActive()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.worldRenderer == null || mc.world == null) return;

        Framebuffer mainFb = mc.getFramebuffer();
        if (mainFb == null) return;

        Camera camera = ctx.camera();
        if (camera == null) return;

        /* Skip during Iris shadow pass — preview output is never seen by
         *  shadow-mapping consumers, so this is pure waste. */
        if (mchorse.bbs_mod.client.BBSRendering.isIrisShadowPass()) return;

        /* Snapshot current Camera state so we can restore at the end of
         *  this hook. Camera is a singleton mutated in-place. */
        double savedX = camera.getPos().x;
        double savedY = camera.getPos().y;
        double savedZ = camera.getPos().z;
        float savedYaw = camera.getYaw();
        float savedPitch = camera.getPitch();

        CameraInvokerMixin invoker = (CameraInvokerMixin) (Object) camera;

        RENDERING_PREVIEW = true;
        try
        {
            for (CameraPreview p : CameraPreviewRegistry.list())
            {
                if (!p.enabled) continue;
                /* Don't pay the cost for previews the user can't see —
                 *  window minimized/off-screen, dashboard closed, etc. */
                if (CameraPreviewPerf.shouldSkipPreview(p)) continue;

                try
                {
                    CameraPreviewRegistry.ensureFramebuffer(p);
                    if (p.framebuffer == null) continue;

                    renderPreview(mc, ctx, invoker, p);
                }
                catch (Throwable t)
                {
                    LOG.debug("preview render '{}' failed: {}", p.name, t.toString());
                }
            }
        }
        finally
        {
            /* Restore camera. */
            invoker.bbsRenewed$setPos(savedX, savedY, savedZ);
            invoker.bbsRenewed$setRotation(savedYaw, savedPitch);

            /* Restore main framebuffer. */
            mainFb.beginWrite(true);

            int sw = mc.getWindow().getFramebufferWidth();
            int sh = mc.getWindow().getFramebufferHeight();
            RenderSystem.viewport(0, 0, sw, sh);

            RENDERING_PREVIEW = false;
        }
    }

    private static void renderPreview(MinecraftClient mc, WorldRenderContext ctx,
                                      CameraInvokerMixin invoker, CameraPreview p)
    {
        double[] pose = CameraPreviewRegistry.resolvePose(p);

        invoker.bbsRenewed$setPos(pose[0], pose[1], pose[2]);
        invoker.bbsRenewed$setRotation((float) pose[3], (float) pose[4]);

        Framebuffer fb = p.framebuffer;
        fb.beginWrite(true);
        RenderSystem.viewport(0, 0, fb.textureWidth, fb.textureHeight);
        RenderSystem.clearColor(0F, 0F, 0F, 1F);
        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT,
            MinecraftClient.IS_SYSTEM_MAC);

        /* Phase 2.5 attempted to call worldRenderer.render() inside the
         *  LAST event — fatal NPE on Iris because the pipeline state
         *  was already torn down by iris$endLevelRender. Reverted to
         *  Phase 2 safe blit. The real-POV second render needs a
         *  pre-main-render hook (WorldRenderEvents.START or an earlier
         *  GameRenderer mixin) so Iris's pipeline is still alive.
         *
         *  Phase 2.6 (planned, separate session): mixin into
         *  GameRenderer.renderWorld HEAD, do all preview renders BEFORE
         *  Iris kicks in for the main pass. */
        blitMainIntoPreview(mc.getFramebuffer(), fb);
    }

    private static void blitMainIntoPreview(Framebuffer src, Framebuffer dst)
    {
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        RenderSystem.setShaderTexture(0, src.getColorAttachment());
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);

        BufferBuilder bb = Tessellator.getInstance().begin(
            VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        bb.vertex(-1F, -1F, 0F).texture(0F, 0F);
        bb.vertex( 1F, -1F, 0F).texture(1F, 0F);
        bb.vertex( 1F,  1F, 0F).texture(1F, 1F);
        bb.vertex(-1F,  1F, 0F).texture(0F, 1F);
        try { net.minecraft.client.render.BufferRenderer.drawWithGlobalProgram(bb.end()); }
        catch (Throwable ignored) {}

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
    }

}
