// Coded by Afrobama/Claude — Phase 14 Blender-style camera frustum mesh
package com.afrobama.bbstools.client.campreview;

import com.afrobama.bbstools.client.util.CameraFrustumVisibility;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Renders a Blender-style wireframe camera mesh at each active
 * {@link com.afrobama.bbstools.clip.BBSToolsAddCameraClip}'s resolved
 * pose. Visualizes where each viewport's camera is in the world so the
 * user can orient cinematic shots.
 *
 * <p>Mesh shape: a body box + a pyramid frustum pointing along the
 * camera's forward direction + an up-triangle indicator on top. Drawn
 * as line segments in vanilla GL (no Sodium/Iris dependency).</p>
 *
 * <p>Hooked at {@code WorldRenderEvents.AFTER_TRANSLUCENT} so the
 * wireframe overlays everything but doesn't fight with shader passes.
 * Skips when {@link CameraFrustumVisibility#visible} is false (toggled
 * via K keybind).</p>
 */
public final class CameraFrustumMeshRenderer
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/cam-preview/frustum-mesh");

    private CameraFrustumMeshRenderer() {}

    public static void register()
    {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(CameraFrustumMeshRenderer::onRender);
        LOG.info("Camera frustum mesh renderer registered (Phase 14)");
    }

    private static void onRender(WorldRenderContext ctx)
    {
        if (!CameraFrustumVisibility.visible) return;

        List<ActiveCameraClipRegistry.Active> active = ActiveCameraClipRegistry.snapshot();
        if (active.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        Camera mainCam = ctx.camera();
        if (mainCam == null) return;

        double camX = mainCam.getPosition().x;
        double camY = mainCam.getPosition().y;
        double camZ = mainCam.getPosition().z;

        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.lineWidth(2F);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        PoseStack stack = ctx.matrixStack();

        for (ActiveCameraClipRegistry.Active a : active)
        {
            if (!a.poseValid) continue;
            try
            {
                drawFrustum(stack, a, camX, camY, camZ);
            }
            catch (Throwable t)
            {
                LOG.debug("frustum mesh draw failed: {}", t.toString());
            }
        }

        RenderSystem.lineWidth(1F);
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
    }

    private static void drawFrustum(PoseStack stack, ActiveCameraClipRegistry.Active a,
                                    double camX, double camY, double camZ)
    {
        /* Translate to clip's pose relative to current main camera so
         *  the mesh aligns with the world position regardless of where
         *  the user is currently viewing from. */
        stack.pushPose();
        stack.translate(a.pose[0] - camX, a.pose[1] - camY, a.pose[2] - camZ);
        stack.mulPose(Axis.YP.rotationDegrees(-(float) a.pose[3]));
        stack.mulPose(Axis.XP.rotationDegrees((float) a.pose[4]));

        Matrix4f m = stack.last().pose();

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder bb = tess.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        /* Body box — small cube at origin (this is the camera's body). */
        float bs = 0.15F;   /* body size */
        addBoxLines(bb, m, -bs, -bs, -bs, bs, bs, bs, 0.3F, 0.7F, 1F);

        /* Frustum pyramid — apex at origin, base out in -Z (camera looks down -Z). */
        float fd = 0.7F;    /* frustum depth */
        float fw = 0.5F;    /* frustum half-width at far end (16:9 aspect approx) */
        float fh = 0.28F;   /* frustum half-height at far end */

        /* Four edges from apex to far-plane corners. */
        addLine(bb, m,  0F,  0F,  0F,   fw,  fh, -fd, 0.95F, 0.95F, 0.2F);
        addLine(bb, m,  0F,  0F,  0F,  -fw,  fh, -fd, 0.95F, 0.95F, 0.2F);
        addLine(bb, m,  0F,  0F,  0F,   fw, -fh, -fd, 0.95F, 0.95F, 0.2F);
        addLine(bb, m,  0F,  0F,  0F,  -fw, -fh, -fd, 0.95F, 0.95F, 0.2F);

        /* Far-plane rectangle. */
        addLine(bb, m,  fw,  fh, -fd,  -fw,  fh, -fd, 1F, 1F, 0.3F);
        addLine(bb, m, -fw,  fh, -fd,  -fw, -fh, -fd, 1F, 1F, 0.3F);
        addLine(bb, m, -fw, -fh, -fd,   fw, -fh, -fd, 1F, 1F, 0.3F);
        addLine(bb, m,  fw, -fh, -fd,   fw,  fh, -fd, 1F, 1F, 0.3F);

        /* "Up" triangle on top of body — Blender-style orientation marker. */
        float ts = 0.18F;
        addLine(bb, m, -ts, bs,       0F,   ts, bs,       0F,  0.4F, 1F, 0.4F);
        addLine(bb, m, -ts, bs,       0F,   0F, bs + ts,  0F,  0.4F, 1F, 0.4F);
        addLine(bb, m,  ts, bs,       0F,   0F, bs + ts,  0F,  0.4F, 1F, 0.4F);

        try { com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(bb.buildOrThrow()); }
        catch (Throwable ignored) {}

        stack.popPose();
    }

    private static void addLine(BufferBuilder bb, Matrix4f m,
                                float x1, float y1, float z1,
                                float x2, float y2, float z2,
                                float r, float g, float bl)
    {
        bb.addVertex(m, x1, y1, z1).setColor(r, g, bl, 1F);
        bb.addVertex(m, x2, y2, z2).setColor(r, g, bl, 1F);
    }

    private static void addBoxLines(BufferBuilder bb, Matrix4f m,
                                    float x1, float y1, float z1,
                                    float x2, float y2, float z2,
                                    float r, float g, float bl)
    {
        /* 12 edges of an axis-aligned box. */
        addLine(bb, m, x1, y1, z1, x2, y1, z1, r, g, bl);
        addLine(bb, m, x1, y2, z1, x2, y2, z1, r, g, bl);
        addLine(bb, m, x1, y1, z2, x2, y1, z2, r, g, bl);
        addLine(bb, m, x1, y2, z2, x2, y2, z2, r, g, bl);
        addLine(bb, m, x1, y1, z1, x1, y2, z1, r, g, bl);
        addLine(bb, m, x2, y1, z1, x2, y2, z1, r, g, bl);
        addLine(bb, m, x1, y1, z2, x1, y2, z2, r, g, bl);
        addLine(bb, m, x2, y1, z2, x2, y2, z2, r, g, bl);
        addLine(bb, m, x1, y1, z1, x1, y1, z2, r, g, bl);
        addLine(bb, m, x2, y1, z1, x2, y1, z2, r, g, bl);
        addLine(bb, m, x1, y2, z1, x1, y2, z2, r, g, bl);
        addLine(bb, m, x2, y2, z1, x2, y2, z2, r, g, bl);
    }

}
