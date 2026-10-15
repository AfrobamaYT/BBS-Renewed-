// Coded by Afrobama/Claude — BBS Renewed Camera Preview Phase 2
package com.afrobama.bbstools.client.campreview;

import com.afrobama.bbstools.client.mixin.CameraInvokerMixin;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Real second-pass world render for each {@link CameraPreview}.
 *
 * <p>Runs at the end of the main level render, after the main framebuffer
 * is populated. For each enabled preview:
 * <ol>
 *   <li>Save current Camera pose</li>
 *   <li>Mutate the singleton Camera to the preview's pose
 *       (via {@code CameraInvokerMixin})</li>
 *   <li>Clear the preview's framebuffer</li>
 *   <li>Blit the main view into it</li>
 *   <li>Restore Camera state</li>
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
 *
 * <p><b>26.2, offener Punkt (kein Compilerfehler):</b> 26.2 trennt Extract
 * und Render. Das Muten des Camera-Singletons WAEHREND des Renderns wirkt
 * sich auf das Bild dieses Frames nicht mehr aus — der Zustand ist beim
 * Extrahieren schon eingefroren (CameraRenderState). Diese Klasse ist
 * ohnehin nur der Blit-Rueckfallweg (der echte POV kommt aus dem
 * Vorpass), aber die Kamera-Mutation hier ist in 26.2 wirkungslos.</p>
 */
public final class CameraPreviewRenderHook
{
    private static final Logger LOG = LoggerFactory.getLogger("bbs/cam-preview/hook");

    /* 26.2: Loeschfarbe ist ein Vektor, keine vier floats mehr. Reihenfolge
     *  ist RGBA — javap auf ARGB.vector4fFromARGB32: new Vector4f(redFloat,
     *  greenFloat, blueFloat, alphaFloat). Also opakes Schwarz. */
    private static final Vector4f CLEAR_BLACK = new Vector4f(0F, 0F, 0F, 1F);

    /** Per-frame guard against accidental recursion if a third-party
     *  mod hooks the end of the level render and also calls world render. */
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
    public static void onWorldRenderLast(LevelRenderContext ctx)
    {
        if (com.blenderbridge.camera.RenderCameraPreview.isRendering()) return;
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

        /* 26.2: Minecraft.getMainRenderTarget() ist weg, das Ziel haengt am
         *  GameRenderer (javap: GameRenderer.mainRenderTarget()). */
        RenderTarget mainFb = mc.gameRenderer.mainRenderTarget();
        if (mainFb == null) return;

        /* 26.2: LevelRenderContext hat kein camera(). Hier wird die Kamera
         *  MUTIERT, es muss also der lebende Singleton sein, nicht der
         *  eingefrorene CameraRenderState — javap: GameRenderer.mainCamera(). */
        Camera camera = mc.gameRenderer.mainCamera();
        if (camera == null) return;

        /* Skip during Iris shadow pass — preview output is never seen by
         *  shadow-mapping consumers, so this is pure waste. */
        if (mchorse.bbs_mod.client.BBSRendering.isIrisShadowPass()) return;

        /* Snapshot current Camera state so we can restore at the end of
         *  this hook. Camera is a singleton mutated in-place.
         *  26.2: getPosition() heisst jetzt position(). */
        double savedX = camera.position().x;
        double savedY = camera.position().y;
        double savedZ = camera.position().z;
        float savedYaw = camera.yRot();
        float savedPitch = camera.xRot();

        CameraInvokerMixin invoker = (CameraInvokerMixin) (Object) camera;

        RENDERING_PREVIEW = true;
        try
        {
            for (CameraPreview p : CameraPreviewRegistry.list())
            {
                if (com.blenderbridge.camera.RenderCameraPreview.owns(p.id)) continue;
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

            /* 26.2: das Zurueckbinden des Hauptpuffers (bindWrite) und das
             *  Zuruecksetzen des Viewports entfallen ERSATZLOS — es gibt
             *  keinen globalen Bindezustand mehr, den man verstellen koennte.
             *  Jeder Zeichenschritt nennt sein Ziel selbst (RenderPass), also
             *  gibt es hier nichts wiederherzustellen. */

            RENDERING_PREVIEW = false;
        }
    }

    private static void renderPreview(Minecraft mc, LevelRenderContext ctx,
                                      CameraInvokerMixin invoker, CameraPreview p)
    {
        double[] pose = CameraPreviewRegistry.resolvePose(p);

        invoker.bbsRenewed$setPos(pose[0], pose[1], pose[2]);
        invoker.bbsRenewed$setRotation((float) pose[3], (float) pose[4]);

        RenderTarget fb = p.framebuffer;

        clearToBlack(fb);

        /* Phase 2.5 attempted to call worldRenderer.render() inside the
         *  LAST event — fatal NPE on Iris because the pipeline state
         *  was already torn down by iris$endLevelRender. Reverted to
         *  Phase 2 safe blit. The real-POV second render needs a
         *  pre-main-render hook so Iris's pipeline is still alive.
         *
         *  Phase 2.6: mixin into the world render, do all preview renders
         *  BEFORE Iris kicks in for the main pass. */
        blitMainIntoPreview(mc.gameRenderer.mainRenderTarget(), fb);
    }

    /** 26.2-Ersatz fuer {@code bindWrite + clearColor + clear(COLOR|DEPTH)}.
     *
     *  <p>Der Tiefen-Loeschwert ist <b>nicht</b> 1.0: 26.2 rechnet mit
     *  umgekehrter Tiefe. Belegt an drei Stellen — die Konstante
     *  {@code RenderSystem.DEFAULT_DEPTH_CLEAR_VALUE} steht auf {@code 0.0d}
     *  (javap -constants), Vanilla schiebt beim Loeschen ein {@code dconst_0}
     *  auf den Stapel (LevelRenderer), und {@code DepthStencilState.DEFAULT}
     *  vergleicht mit {@code CompareOp.GREATER_THAN_OR_EQUAL}. Darum hier die
     *  Konstante statt eines Literals.</p> */
    private static void clearToBlack(RenderTarget fb)
    {
        if (fb.useDepth && fb.getDepthTexture() != null)
        {
            RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
                fb.getColorTexture(), CLEAR_BLACK,
                fb.getDepthTexture(), RenderSystem.DEFAULT_DEPTH_CLEAR_VALUE);
        }
        else
        {
            RenderSystem.getDevice().createCommandEncoder().clearColorTexture(
                fb.getColorTexture(), CLEAR_BLACK);
        }
    }

    /** 26.2-Ersatz fuer den handgeschriebenen Vollbild-Quad.
     *
     *  <p>Vanilla macht genau das in {@code RenderTarget.blitAndBlendToTexture}:
     *  Renderpass auf die uebergebenen Sichten (ohne Loeschen), Pipeline
     *  {@code ENTITY_OUTLINE_BLIT} (Vertexshader {@code core/screenquad},
     *  Fragmentshader {@code core/blit_screen}), die eigene Farbtextur als
     *  {@code InSampler}, {@code draw(3,1,0,0)}. Ersetzt Tesselator,
     *  BufferUploader, setShader, setShaderTexture und setShaderColor.</p>
     *
     *  <p><b>Ein gemessener Unterschied</b> (Falle 1, kein Compilerfehler):
     *  {@code BlendFunction.ENTITY_OUTLINE_BLIT} ist
     *  {@code (SRC_ALPHA, ONE_MINUS_SRC_ALPHA, ZERO, ONE)}. Der Farbanteil ist
     *  Zeichen fuer Zeichen der alte {@code defaultBlendFunc()}; nur der
     *  Alpha-Anteil ist anders (alt: {@code ONE, ONE_MINUS_SRC_ALPHA}). Das
     *  Ziel wurde direkt davor auf Alpha=1 geloescht und die Quelle ist opak,
     *  das sichtbare RGB ist also identisch — es aendert sich allein das
     *  Alpha des Vorschau-Puffers.</p> */
    private static void blitMainIntoPreview(RenderTarget src, RenderTarget dst)
    {
        //? if >=26.1 {
        src.blitAndBlendToTexture(dst.getColorTextureView(), dst.getDepthTextureView());
        //?} else if >=1.21.5 {
        /*src.blitAndBlendToTexture(dst.getColorTextureView());
        *///?} else {
        /*dst.bindWrite(true);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.blendFuncSeparate(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
            com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ZERO, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        src.blitAndBlendToScreen(dst.width, dst.height);
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        net.minecraft.client.Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
        *///?}
    }

}
