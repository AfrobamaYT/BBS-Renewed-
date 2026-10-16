package com.afrobama.bbstools.client.fracture;

import net.minecraft.client.renderer.state.level.CameraRenderState;
import com.afrobama.bbstools.fracture.PhysicsBlockDestructionActionClip;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.client.FormPipelines;
import mchorse.bbs_mod.graphics.SofortZeichner;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Debug-style wireframe overlay for the physics_block_destruction
 * clip. When the user enables {@code showWireframe} on a clip, this
 * renderer draws three things in the world:
 *
 * <ol>
 *   <li>The region's axis-aligned bounding box (white lines)</li>
 *   <li>A small cross at the impact point (yellow lines)</li>
 *   <li>An arrow from impact toward {@code (directionX, Y, Z)} so
 *       the user sees which way debris will fly (orange line)</li>
 * </ol>
 *
 * <p>Visibility is filtered to clips that are currently active in
 * the editor — registered via {@link #setVisible(PhysicsBlockDestructionActionClip)}
 * which the UI calls when the user opens / closes the clip's panel.
 * Multiple visible clips render in parallel for cinematic blocks
 * with several destruction events.</p>
 */
public final class BlockDestructionWireframe
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/block-destruction/wireframe");

    /** Set of clips currently flagged for wireframe rendering. Weak
     *  so closed editors don't keep dead clips alive. */
    private static final Set<PhysicsBlockDestructionActionClip> VISIBLE =
        Collections.newSetFromMap(new WeakHashMap<>());

    private BlockDestructionWireframe()
    {
    }

    public static void register()
    {
        /* ⚠️ PHASE NICHT BELEGT GLEICHWERTIG. Alt war WorldRenderEvents.LAST -- die Phase NACH
         * allem, einschliesslich Wolken, Wetter und Nachbearbeitung. In 26.2 gibt es dafuer
         * kein benanntes Gegenstueck; END_MAIN ist die letzte verfuegbare Phase, aber ob sie
         * an derselben Stelle liegt, ist aus dem Bytecode NICHT ablesbar.
         * Zu pruefen am laufenden Bild: sieht ein Export genauso aus wie das Fenster? */
        LevelRenderEvents.END_MAIN.register(BlockDestructionWireframe::onWorldRender);
        LOG.info("Block-destruction wireframe overlay registered");
    }

    public static synchronized void setVisible(PhysicsBlockDestructionActionClip clip, boolean visible)
    {
        if (clip == null) return;
        if (visible) VISIBLE.add(clip);
        else VISIBLE.remove(clip);
    }

    public static synchronized void clearAll()
    {
        VISIBLE.clear();
    }

    private static void onWorldRender(LevelRenderContext ctx)
    {
        if (VISIBLE.isEmpty()) return;

        CameraRenderState cam = ctx.levelState().cameraRenderState;
        if (cam == null) return;
        Vec3 camPos = cam.pos;

        PoseStack ms = ctx.poseStack();
        if (ms == null) return;

        /* GL state for thick, always-visible debug lines:
         *  - depth test OFF so wireframe shows through walls (user
         *    can see it from any angle)
         *  - depth mask OFF so we don't pollute the depth buffer
         *  - line width 4 for clear visibility
         *  - additive-style blending so the colors pop against any
         *    background */
        /* 26.2: disableDepthTest ist entfallen -- der Zustand steckt in der Pipeline
         * des Zuges. Absicht war: Tiefentest aus. */
        /* 26.2: depthMask ist entfallen -- der Zustand steckt in der Pipeline
         * des Zuges. Absicht war: Tiefe schreiben = false. */
        /* ⚠️ 26.2: RenderSystem.lineWidth ist entfallen und hat KEIN Gegenstueck --
         * die Linienbreite ist kein Pipeline-Zustand. Dicke Linien brauchen
         * Geometrie (Streifen statt GL_LINES); bis dahin zeichnet dies duenn. */
        /* 26.2: enableBlend ist entfallen -- der Zustand steckt in der Pipeline
         * des Zuges. Absicht war: mischen an. */
        /* 26.2: defaultBlendFunc ist entfallen -- der Zustand steckt in der Pipeline
         * des Zuges. Absicht war: Standardmischung (Quell-Alpha ueber Gegen-Alpha). */

        ms.pushPose();
        ms.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f modelMat = ms.last().pose();

        try (ByteBufferBuilder speicher = new ByteBufferBuilder(4096))
        {
            BufferBuilder bb = SofortZeichner.beginnen(
                speicher, PrimitiveTopology.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

            synchronized (VISIBLE)
            {
                for (PhysicsBlockDestructionActionClip clip : VISIBLE)
                {
                    if (clip == null) continue;
                    drawClipWireframe(bb, modelMat, clip);
                }
            }

            /* Der Zustandsblock von oben als Pipeline: mischen (enableBlend +
             * defaultBlendFunc), KEINE Tiefenpruefung (disableDepthTest + depthMask(false)
             * — Test aus heisst hier auch Schreiben aus, genau die Absicht "durch Waende
             * sichtbar, ohne die Tiefe zu verschmutzen"), kein Culling (bei Linien ohne
             * Wirkung).
             *
             * modelMat steckt schon in den Ecken (addVertex(m, ...) in line()), darum geht
             * als Modellansicht NUR der globale Wert mit — der, den
             * BufferUploader.drawWithShader frueher selbst aus RenderSystem nahm. */
            SofortZeichner.zeichnen(
                FormPipelines.holen(PrimitiveTopology.DEBUG_LINES, true, false, false),
                bb.build(),
                RenderSystem.getModelViewMatrixCopy(),
                null,
                null);
        }
        catch (Throwable t)
        {
            LOG.debug("Wireframe draw failed: {}", t.toString());
        }

        ms.popPose();

        /* Linienbreite ist in 26.2 weiterhin globaler Zustand — die wird zurueckgesetzt.
         * Tiefentest und Tiefenmaske sind es NICHT mehr. Sie stehen im DepthStencilState
         * der RenderPipeline des jeweiligen Zeichenaufrufs, also bringt jeder folgende
         * Aufruf seinen Tiefenzustand selbst mit (Vanilla ueber renderType.pipeline(),
         * BBS-Modelle ueber ModelVAOPipeline.DURCHSICHTIG). Es gibt keinen globalen
         * Zustand mehr, den man hier wiederherstellen koennte oder muesste — die beiden
         * Aufrufe entfallen ersatzlos. */
        /* ⚠️ 26.2: RenderSystem.lineWidth ist entfallen und hat KEIN Gegenstueck --
         * die Linienbreite ist kein Pipeline-Zustand. Dicke Linien brauchen
         * Geometrie (Streifen statt GL_LINES); bis dahin zeichnet dies duenn. */
    }

    private static void drawClipWireframe(BufferBuilder bb, Matrix4f m, PhysicsBlockDestructionActionClip clip)
    {
        int x0 = Math.min(clip.minX.get(), clip.maxX.get());
        int y0 = Math.min(clip.minY.get(), clip.maxY.get());
        int z0 = Math.min(clip.minZ.get(), clip.maxZ.get());
        int x1 = Math.max(clip.minX.get(), clip.maxX.get()) + 1;
        int y1 = Math.max(clip.minY.get(), clip.maxY.get()) + 1;
        int z1 = Math.max(clip.minZ.get(), clip.maxZ.get()) + 1;

        /* Region AABB — bright white. */
        drawAabb(bb, m, x0, y0, z0, x1, y1, z1, 1F, 1F, 1F, 1F);

        /* Impact point — small yellow 3-axis cross. If the user
         *  hasn't set Impact (still at world origin 0,0,0) and the
         *  region is far from origin, fall back to region center. */
        float ix = clip.impactX.get() + 0.5F;
        float iy = clip.impactY.get() + 0.5F;
        float iz = clip.impactZ.get() + 0.5F;
        if (clip.impactX.get() == 0 && clip.impactY.get() == 0 && clip.impactZ.get() == 0
            && (Math.abs(x0) > 32 || Math.abs(z0) > 32))
        {
            ix = (x0 + x1) * 0.5F;
            iy = (y0 + y1) * 0.5F;
            iz = (z0 + z1) * 0.5F;
        }
        /* Small marker (3 axis lines, no diagonals → not sphere-like). */
        float r = 0.3F;
        line(bb, m, ix - r, iy, iz, ix + r, iy, iz, 1F, 1F, 0F, 1F);
        line(bb, m, ix, iy - r, iz, ix, iy + r, iz, 1F, 1F, 0F, 1F);
        line(bb, m, ix, iy, iz - r, ix, iy, iz + r, 1F, 1F, 0F, 1F);

        /* Direction line — exactly the same approach as the
         *  PhysicsBlockFractureEmitter renderer (which I authored
         *  for this user previously). Single bright white line from
         *  impact point in the normalized (directionX, Y, Z) direction
         *  with length = max(1, directionForce * 4). Defaults to +X
         *  axis if no direction set so the user always sees an axis. */
        float dx = clip.directionX.get();
        float dy = clip.directionY.get();
        float dz = clip.directionZ.get();
        float force = clip.directionForce.get();

        if (Math.abs(dx) < 0.001F && Math.abs(dy) < 0.001F && Math.abs(dz) < 0.001F)
        {
            dx = 1F;
        }
        float dlen = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (dlen > 0.0001F)
        {
            float fLen = Math.max(4F, force * 4F);
            float ndx = dx / dlen, ndy = dy / dlen, ndz = dz / dlen;
            float ex = ix + ndx * fLen;
            float ey = iy + ndy * fLen;
            float ez = iz + ndz * fLen;

            /* Compute two perpendicular axes so we can offset the
             *  line into a 4-parallel-line "tube" for genuine thickness
             *  that doesn't depend on driver line-width support
             *  (some GPUs cap lineWidth at 1px regardless). */
            float ux = 0F, uy = 1F, uz = 0F;
            if (Math.abs(ndy) > 0.95F) { ux = 1F; uy = 0F; uz = 0F; }
            float p1x = ndy * uz - ndz * uy;
            float p1y = ndz * ux - ndx * uz;
            float p1z = ndx * uy - ndy * ux;
            float pl = (float) Math.sqrt(p1x*p1x + p1y*p1y + p1z*p1z);
            if (pl > 1e-4F) { p1x /= pl; p1y /= pl; p1z /= pl; }
            float p2x = ndy * p1z - ndz * p1y;
            float p2y = ndz * p1x - ndx * p1z;
            float p2z = ndx * p1y - ndy * p1x;

            /* Center line + 4 offset parallels at radius 0.15 = visible
             *  ~0.3-block-thick rod from any angle. */
            float t = 0.15F;
            line(bb, m, ix, iy, iz, ex, ey, ez, 1F, 1F, 1F, 1F);
            line(bb, m, ix + p1x*t, iy + p1y*t, iz + p1z*t, ex + p1x*t, ey + p1y*t, ez + p1z*t, 1F, 1F, 1F, 1F);
            line(bb, m, ix - p1x*t, iy - p1y*t, iz - p1z*t, ex - p1x*t, ey - p1y*t, ez - p1z*t, 1F, 1F, 1F, 1F);
            line(bb, m, ix + p2x*t, iy + p2y*t, iz + p2z*t, ex + p2x*t, ey + p2y*t, ez + p2z*t, 1F, 1F, 1F, 1F);
            line(bb, m, ix - p2x*t, iy - p2y*t, iz - p2z*t, ex - p2x*t, ey - p2y*t, ez - p2z*t, 1F, 1F, 1F, 1F);

            /* Endcap squares to close the tube visually. */
            line(bb, m, ex + p1x*t, ey + p1y*t, ez + p1z*t, ex - p1x*t, ey - p1y*t, ez - p1z*t, 1F, 1F, 1F, 1F);
            line(bb, m, ex + p2x*t, ey + p2y*t, ez + p2z*t, ex - p2x*t, ey - p2y*t, ez - p2z*t, 1F, 1F, 1F, 1F);
        }

        /* Upward-force vector — cyan line + arrowhead. */
        float uf = clip.upwardForce.get();
        if (Math.abs(uf) > 0.05F)
        {
            float kickEnd = iy + uf * 0.8F;
            line(bb, m, ix, iy, iz, ix, kickEnd, iz, 0F, 0.8F, 1F, 1F);
            float ah = uf > 0 ? -0.3F : 0.3F;
            line(bb, m, ix, kickEnd, iz, ix + 0.3F, kickEnd + ah, iz, 0F, 0.8F, 1F, 1F);
            line(bb, m, ix, kickEnd, iz, ix - 0.3F, kickEnd + ah, iz, 0F, 0.8F, 1F, 1F);
            line(bb, m, ix, kickEnd, iz, ix, kickEnd + ah, iz + 0.3F, 0F, 0.8F, 1F, 1F);
            line(bb, m, ix, kickEnd, iz, ix, kickEnd + ah, iz - 0.3F, 0F, 0.8F, 1F, 1F);
        }
    }

    private static void drawAabb(BufferBuilder bb, Matrix4f m,
                                 float x0, float y0, float z0, float x1, float y1, float z1,
                                 float r, float g, float b, float a)
    {
        line(bb, m, x0, y0, z0, x1, y0, z0, r, g, b, a);
        line(bb, m, x1, y0, z0, x1, y0, z1, r, g, b, a);
        line(bb, m, x1, y0, z1, x0, y0, z1, r, g, b, a);
        line(bb, m, x0, y0, z1, x0, y0, z0, r, g, b, a);

        line(bb, m, x0, y1, z0, x1, y1, z0, r, g, b, a);
        line(bb, m, x1, y1, z0, x1, y1, z1, r, g, b, a);
        line(bb, m, x1, y1, z1, x0, y1, z1, r, g, b, a);
        line(bb, m, x0, y1, z1, x0, y1, z0, r, g, b, a);

        line(bb, m, x0, y0, z0, x0, y1, z0, r, g, b, a);
        line(bb, m, x1, y0, z0, x1, y1, z0, r, g, b, a);
        line(bb, m, x1, y0, z1, x1, y1, z1, r, g, b, a);
        line(bb, m, x0, y0, z1, x0, y1, z1, r, g, b, a);
    }

    private static void line(BufferBuilder bb, Matrix4f m,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float r, float g, float b, float a)
    {
        bb.addVertex(m, x1, y1, z1).setColor(r, g, b, a);
        bb.addVertex(m, x2, y2, z2).setColor(r, g, b, a);
    }

}
