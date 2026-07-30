// Coded by Afrobama/Claude — BBS Renewed Camera Preview Phase 2
package com.afrobama.bbstools.client.campreview;

import com.afrobama.bbstools.client.mixin.CameraInvokerMixin;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
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

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.levelRenderer == null || mc.level == null) return;

        RenderTarget mainFb = mc.getMainRenderTarget();
        if (mainFb == null) return;

        Camera camera = ctx.camera();
        if (camera == null) return;

        /* Skip during Iris shadow pass — preview output is never seen by
         *  shadow-mapping consumers, so this is pure waste. */
        if (mchorse.bbs_mod.client.BBSRendering.isIrisShadowPass()) return;

        /* Snapshot current Camera state so we can restore at the end of
         *  this hook. Camera is a singleton mutated in-place. */
        double savedX = camera.getPosition().x;
        double savedY = camera.getPosition().y;
        double savedZ = camera.getPosition().z;
        float savedYaw = camera.getYRot();
        float savedPitch = camera.getXRot();

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
            mainFb.bindWrite(true);

            int sw = mc.getWindow().getWidth();
            int sh = mc.getWindow().getHeight();
            RenderSystem.viewport(0, 0, sw, sh);

            RENDERING_PREVIEW = false;
        }
    }

    private static void renderPreview(Minecraft mc, WorldRenderContext ctx,
                                      CameraInvokerMixin invoker, CameraPreview p)
    {
        double[] pose = CameraPreviewRegistry.resolvePose(p);

        invoker.bbsRenewed$setPos(pose[0], pose[1], pose[2]);
        invoker.bbsRenewed$setRotation((float) pose[3], (float) pose[4]);

        RenderTarget fb = p.framebuffer;
        fb.bindWrite(true);
        RenderSystem.viewport(0, 0, fb.width, fb.height);
        RenderSystem.clearColor(0F, 0F, 0F, 1F);
        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT,
            Minecraft.ON_OSX);

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
        blitMainIntoPreview(mc.getMainRenderTarget(), fb);
    }

    private static void blitMainIntoPreview(RenderTarget src, RenderTarget dst)
    {
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, src.getColorTextureId());
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);

        BufferBuilder bb = Tesselator.getInstance().begin(
            VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        bb.addVertex(-1F, -1F, 0F).setUv(0F, 0F);
        bb.addVertex( 1F, -1F, 0F).setUv(1F, 0F);
        bb.addVertex( 1F,  1F, 0F).setUv(1F, 1F);
        bb.addVertex(-1F,  1F, 0F).setUv(0F, 1F);
        try { com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(bb.buildOrThrow()); }
        catch (Throwable ignored) {}

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
    }

}
