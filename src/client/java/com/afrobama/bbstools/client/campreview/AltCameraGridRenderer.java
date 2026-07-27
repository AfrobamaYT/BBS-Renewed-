// Coded by Afrobama/Claude — Phase 15 v4: reference grid via proper matrix stack
package com.afrobama.bbstools.client.campreview;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;

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

    public static void render(MinecraftClient mc, Camera camera, Matrix4f projection)
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

            float camX = (float) camera.getPos().x;
            float camY = (float) camera.getPos().y;
            float camZ = (float) camera.getPos().z;
            float pitchRad = (float) Math.toRadians(camera.getPitch());
            float yawRad = (float) Math.toRadians(camera.getYaw() + 180F);

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

            RenderSystem.setProjectionMatrix(projection, VertexSorter.BY_DISTANCE);

            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableDepthTest();
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);

            BufferBuilder bb = Tessellator.getInstance().begin(
                VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

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
                bb.vertex(fx, fy, fzMin).color(r, g, b, alpha);
                bb.vertex(fx, fy, fzMax).color(r, g, b, alpha);

                /* Line parallel to X axis (varying Z = fz). */
                bb.vertex(fxMin, fy, fz).color(r, g, b, alpha);
                bb.vertex(fxMax, fy, fz).color(r, g, b, alpha);
            }

            BufferRenderer.drawWithGlobalProgram(bb.end());

            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();

            mvStack.popMatrix();
            RenderSystem.applyModelViewMatrix();

            if (!loggedFirstRender)
            {
                loggedFirstRender = true;
                LOG.info("Grid first-render OK at player Y={} (camera @ {},{},{} pitch={} yaw={})",
                    gy, camX, camY, camZ, camera.getPitch(), camera.getYaw());
            }
        }
        catch (Throwable t)
        {
            LOG.error("Grid render FAILED: " + t, t);
        }
    }

}
