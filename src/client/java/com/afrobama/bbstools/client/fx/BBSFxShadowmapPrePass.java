// Coded by Afrobama/Claude — sun-POV depth pre-pass for the deferred-
// lighting shadows.fsh. Phase 4b stage 2: REAL depth render via
// VeilLevelPerspectiveRenderer.render(). Reverse-engineered from
// destroying-minecraft's ShadowMapRenderer (com.sp.render.* in the
// destroying-minecraft-1.0.0.jar) — same matrix construction, same
// VeilRenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS subscription
// point, same isRenderingPerspective() recursion guard.
//
// Per-frame flow:
//   1. Subscribe to VeilRenderLevelStageEvent.AFTER_SOLID_BLOCKS.
//   2. Guard against recursion (the perspective-renderer also fires
//      level-stage events; without the guard we'd re-enter forever).
//   3. Build orthographic projection matrix (160 block width, near
//      0.01, far 256) and a model-view matrix with sun rotation
//      (X+20°, Z-75°) and a Z=-100 translation so geometry around
//      the camera lands in front of the sun "camera".
//   4. Disable back-face culling (depth-only shadow pass — we want
//      front-side depth even from inside meshes).
//   5. Bind the bbstools:shadowmap framebuffer and call
//      VeilLevelPerspectiveRenderer.render() — this re-renders the
//      world from the sun's POV.
//   6. Restore back-face culling.
//   7. Push shadowViewMatrix / IShadowViewMatrix / shadowProjMat
//      uniforms into bbstools:fx/shadows so the deferred shader
//      can map fragments back into shadow space.
package com.afrobama.bbstools.client.fx;

import com.afrobama.bbstools.diag.BBSDiag;

import foundry.veil.api.client.render.VeilLevelPerspectiveRenderer;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import foundry.veil.api.event.VeilRenderLevelStageEvent;
import foundry.veil.platform.VeilEventPlatform;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import org.lwjgl.opengl.GL11;

public final class BBSFxShadowmapPrePass
{
    private static final ResourceLocation SHADOWS_SHADER     = new ResourceLocation("bbstools", "fx/shadows");
    private static final ResourceLocation SHADOWMAP_FRAMEBUF = new ResourceLocation("bbstools", "shadowmap");

    /** Re-render the shadowmap every N frames. 1 = every frame (full
     *  quality, doubles render cost). 2-3 = nearly invisible quality
     *  loss with significant FPS recovery. 0 disables the depth
     *  re-render entirely (matrices still pushed; PCF reads stale
     *  depth — useful for static-scene cinematic shots). Tunable
     *  live via /bbstools fx shadow-interval. */
    public static volatile int shadowInterval = 1;
    private static int intervalCounter;

    /** Ortho frustum half-width in world blocks. 160 matches
     *  destroying-minecraft default — covers ~80 blocks each side
     *  of the camera with a 4096x4096 shadowmap = ~50 texels/block. */
    private static final float ORTHO_WIDTH = 160F;
    private static final float NEAR_PLANE  = 0.01F;
    private static final float FAR_PLANE   = 256F;

    /** "Sun is here" translation along -Z so the world sits in front
     *  of the orthographic camera. */
    private static final float Z_TRANSLATE = -100F;

    /* Sun direction is read each frame from BBSFxState.sunRotXDeg /
     *  sunRotZDeg so /bbstools fx sundir can adjust it per cinematic
     *  shot without re-launching MC. */

    private static boolean registered;

    /* Reused buffers to avoid per-frame allocation. */
    private static final Matrix4f viewBuf  = new Matrix4f();
    private static final Matrix4f iViewBuf = new Matrix4f();
    private static final Matrix4f projBuf  = new Matrix4f();
    private static final Quaternionf identityQuat = new Quaternionf();

    private BBSFxShadowmapPrePass() {}

    public static synchronized void register()
    {
        if (registered) return;
        registered = true;
        try
        {
            VeilEventPlatform.INSTANCE.onVeilRenderLevelStage(
                (stage, levelRenderer, bufferSource, matrixStack, frustumMatrix,
                 projectionMatrix, renderTick, deltaTracker, camera, frustum) ->
                {
                    if (stage != VeilRenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) return;
                    if (VeilLevelPerspectiveRenderer.isRenderingPerspective()) return;
                    if (!BBSFxPipelineRegistrar.ENABLED) return;
                    if (camera == null) return;
                    renderShadowmap(camera, deltaTracker);
                }
            );
            BBSDiag.feature("fx-shadowmap").event("register").emit();
        }
        catch (Throwable t)
        {
            BBSDiag.feature("fx-shadowmap").sticky("register-error")
                .field("err", t.toString()).asWarn().emit();
        }
    }

    private static void renderShadowmap(Camera camera, net.minecraft.client.DeltaTracker tickCounter)
    {
        try
        {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return;

            AdvancedFbo shadowFbo = VeilRenderSystem.renderer()
                .getFramebufferManager().getFramebuffer(SHADOWMAP_FRAMEBUF);
            if (shadowFbo == null)
            {
                BBSDiag.feature("fx-shadowmap").sticky("no-fbo")
                    .field("err", "bbstools:shadowmap framebuffer missing").asWarn().emit();
                return;
            }

            Vec3 camPos = camera.position();

            buildOrthoProjection(projBuf);
            buildSunViewMatrix(viewBuf);
            viewBuf.invert(iViewBuf);

            BBSFxState.shadowViewMatrix  = new Matrix4f(viewBuf);
            BBSFxState.iShadowViewMatrix = new Matrix4f(iViewBuf);
            BBSFxState.shadowProjMat     = new Matrix4f(projBuf);

            /* Push matrix uniforms into our deferred shader. Done here
             *  rather than from a separate AFTER_SETUP hook so the
             *  matrices and the depth render stay in sync — they're
             *  built from the same camera pose. */
            ShaderProgram shadows = VeilRenderSystem.renderer()
                .getShaderManager().getShader(SHADOWS_SHADER);
            if (shadows != null)
            {
                shadows.bind();
                trySetMat4(shadows, "shadowViewMatrix",  viewBuf);
                trySetMat4(shadows, "IShadowViewMatrix", iViewBuf);
                trySetMat4(shadows, "shadowProjMat",     projBuf);
            }

            /* Throttle: only re-render the world-depth every
             *  shadowInterval frames. Matrices are still up-to-date
             *  on every frame above; this just skips the expensive
             *  level re-render. Skipping means shadows lag camera
             *  motion by N-1 frames (typically imperceptible at 2-3
             *  for cinematic shots; very visible at 4+). */
            intervalCounter++;
            if (shadowInterval == 0 || intervalCounter % Math.max(1, shadowInterval) != 0)
            {
                return;
            }

            /* Front-face culling during the shadow pass — same as
             *  destroying-mc. Eliminates the classic "self-shadow
             *  acne" by sampling depth from back faces of occluders
             *  instead of front faces. */
            GL11.glCullFace(GL11.GL_FRONT);
            try
            {
                Vector3d camPosD = new Vector3d(camPos.x, camPos.y, camPos.z);
                VeilLevelPerspectiveRenderer.render(
                    shadowFbo,
                    projBuf,           /* projection */
                    viewBuf,           /* model-view */
                    camPosD,           /* camera position */
                    identityQuat,      /* camera rotation (we baked rotation into model-view) */
                    20F,               /* fov — destroying-mc uses 20, ignored for ortho */
                    tickCounter,
                    false              /* don't render hand */
                );
            }
            finally
            {
                GL11.glCullFace(GL11.GL_BACK);
            }

            BBSDiag.feature("fx-shadowmap").throttled1Hz().event("rendered")
                .field("camX", (float) camPos.x)
                .field("camY", (float) camPos.y)
                .field("camZ", (float) camPos.z)
                .emit();
        }
        catch (Throwable t)
        {
            BBSDiag.feature("fx-shadowmap").sticky("render-error")
                .field("err", t.toString()).asWarn().emit();
        }
    }

    /** Orthographic projection: 1/(width), 0, 0, 0  /  0, 1/(width),
     *  0, 0  /  0, 0, 2/(near-far), 0  /  0, 0, -(near+far)/(near-far),
     *  1. Matches destroying-mc's orthographicMatrix(160, 0.01, 256). */
    private static void buildOrthoProjection(Matrix4f out)
    {
        float w = ORTHO_WIDTH;
        float n = NEAR_PLANE;
        float f = FAR_PLANE;
        out.set(
            1F / w, 0F, 0F, 0F,
            0F, 1F / w, 0F, 0F,
            0F, 0F, 2F / (f - n), 0F,
            0F, 0F, -(f + n) / (f - n), 1F
        );
    }

    /** Sun view matrix: identity, translate Z=-100, then rotate around
     *  X by BBSFxState.sunRotXDeg + around Z by BBSFxState.sunRotZDeg.
     *  Matches createShadowModelView + rotateShadowModelView default
     *  branch in destroying-minecraft's ShadowMapRenderer; only the
     *  angles are now live-tunable. */
    private static void buildSunViewMatrix(Matrix4f out)
    {
        out.identity();
        out.translate(0F, 0F, Z_TRANSLATE);
        out.rotate((float) Math.toRadians(BBSFxState.sunRotXDeg), 1F, 0F, 0F);
        out.rotate((float) Math.toRadians(BBSFxState.sunRotZDeg), 0F, 0F, 1F);
    }

    private static void trySetMat4(ShaderProgram p, String name, Matrix4f m)
    {
        try
        {
            p.getUniform(name).setMatrix(m);
        }
        catch (Throwable t)
        {
            BBSDiag.feature("fx-shadowmap").sticky("missing:" + name)
                .field("uniform", name).field("err", t.toString()).asWarn().emit();
        }
    }

}
