// Coded by Afrobama/Claude — Phase 15 v4: reference grid via proper matrix stack
package com.afrobama.bbstools.client.campreview;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Spatial-reference grid drawn into the alt-camera framebuffer between
 * the black clear and the BBS form pass. The grid lives at player-Y
 * with 1-block subdivisions + brighter every-8-block accent lines so
 * the user has a sense of scale/floor when the rest of the world is
 * suppressed.
 *
 * <p>Phase 15 v3 used a manual MV matrix that was double-applied (the
 * shader also multiplies by RenderSystem's modelview stack). Phase 15
 * v4 reverts to the canonical MC pattern: push a view matrix onto
 * {@code RenderSystem.getModelViewStack()}, draw vertices in world
 * coords, pop.</p>
 */
public final class AltCameraGridRenderer
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/cam-preview/grid");
    private static boolean loggedFirstRender;

    /** Half-extent of the grid in blocks. Generous so it reaches the
     *  horizon at most reasonable camera distances; cost is trivial
     *  (2 × HALF_EXTENT × 2 vertices in DEBUG_LINES). */
    private static final int HALF_EXTENT = 256;

    private AltCameraGridRenderer() {}

    public static void render(Minecraft mc, Camera camera, Matrix4f projection)
    {
        if (mc == null || mc.player == null) return;

        try
        {
            double centerX = mc.player.getX();
            double centerY = mc.player.getY();
            double centerZ = mc.player.getZ();

            /* Snap grid origin to integer block coords. */
            int gx = (int) Math.floor(centerX);
            int gy = (int) Math.floor(centerY);
            int gz = (int) Math.floor(centerZ);

            float camX = (float) camera.getPosition().x;
            float camY = (float) camera.getPosition().y;
            float camZ = (float) camera.getPosition().z;
            float pitchRad = (float) Math.toRadians(camera.xRot());
            float yawRad = (float) Math.toRadians(camera.yRot() + 180F);

            /* Build view matrix the same way MC's GameRenderer does:
             *  Rx(pitch) * Ry(yaw+180) * T(-camera). Push onto the
             *  modelview stack so the shader picks it up. */
            Matrix4fStack mvStack = RenderSystem.getModelViewStack();
            mvStack.pushMatrix();
            mvStack.identity();
            mvStack.rotateX(pitchRad);
            mvStack.rotateY(yawRad);
            mvStack.translate(-camX, -camY, -camZ);
            RenderSystem.applyModelViewMatrix();

            RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);

            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableDepthTest();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);

            BufferBuilder bb = Tesselator.getInstance().begin(
                VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

            for (int i = -HALF_EXTENT; i <= HALF_EXTENT; i++)
            {
                int alpha;
                int r = 180, g = 180, b = 200;
                if (i == 0)
                {
                    alpha = 220; r = 100; g = 200; b = 255;
                }
                else if (i % 8 == 0)
                {
                    alpha = 140;
                }
                else
                {
                    alpha = 60;
                }

                float fx = (float) gx + (float) i;
                float fy = (float) gy;
                float fz = (float) gz + (float) i;
                float fzMin = (float) gz - HALF_EXTENT;
                float fzMax = (float) gz + HALF_EXTENT;
                float fxMin = (float) gx - HALF_EXTENT;
                float fxMax = (float) gx + HALF_EXTENT;

                /* Line parallel to Z axis (varying X = fx). */
                bb.addVertex(fx, fy, fzMin).setColor(r, g, b, alpha);
                bb.addVertex(fx, fy, fzMax).setColor(r, g, b, alpha);

                /* Line parallel to X axis (varying Z = fz). */
                bb.addVertex(fxMin, fy, fz).setColor(r, g, b, alpha);
                bb.addVertex(fxMax, fy, fz).setColor(r, g, b, alpha);
            }

            BufferUploader.drawWithShader(bb.buildOrThrow());

            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();

            mvStack.popMatrix();
            RenderSystem.applyModelViewMatrix();

            if (!loggedFirstRender)
            {
                loggedFirstRender = true;
                LOG.info("Grid first-render OK at player Y={} (camera @ {},{},{} pitch={} yaw={})",
                    gy, camX, camY, camZ, camera.xRot(), camera.getYRot());
            }
        }
        catch (Throwable t)
        {
            LOG.error("Grid render FAILED: " + t, t);
        }
    }

}
