// Coded by Afrobama/Claude — Phase 14 Blender-style camera frustum mesh
package com.afrobama.bbstools.client.campreview;

import com.afrobama.bbstools.client.util.CameraFrustumVisibility;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
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
 * as line segments (no Sodium/Iris dependency).</p>
 *
 * <p>Skips when {@link CameraFrustumVisibility#visible} is false (toggled
 * via K keybind).</p>
 *
 * <p><b>26.2:</b> die Geometrie wird nicht mehr selbst gezeichnet, sondern
 * ueber {@code submitCustomGeometry} EINGEREICHT; wann sie auf dem Bild
 * landet, entscheidet jetzt der RenderType, nicht mehr der Haken. Alle
 * frueher von Hand geschalteten Zustaende stecken in
 * {@code RenderTypes.LINES} — am Bytecode nachgelesen, siehe
 * {@link #register()} und {@link #onRender(LevelRenderContext)}.</p>
 */
public final class CameraFrustumMeshRenderer
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/cam-preview/frustum-mesh");

    /* 26.2: RenderSystem.lineWidth() gibt es nicht mehr. Die Linienbreite ist
     *  jetzt ein VERTEX-ATTRIBUT — RenderPipelines.LINES bindet
     *  DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH (javap auf
     *  RenderPipelines.<clinit>, LINES_SNIPPET). Vanilla setzt es je Vertex,
     *  siehe ShapeOutlineFeatureRenderer (addVertex/setColor/setNormal/setLineWidth). */
    private static final float LINE_WIDTH = 2F;

    private CameraFrustumMeshRenderer() {}

    public static void register()
    {
        /* 26.2: WorldRenderEvents.AFTER_TRANSLUCENT existiert nicht mehr. Der
         *  Nachfolger ist NICHT einfach AFTER_TRANSLUCENT_TERRAIN/_FEATURES:
         *  eingereichte Geometrie muss in der Sammelphase abgegeben werden,
         *  sonst sind die Feature-Renderer schon durch und es wird STILL
         *  nichts gezeichnet (kompiliert, bleibt unsichtbar).
         *
         *  Reihenfolge am Fabric-Mixin abgelesen (javap auf
         *  fabric-rendering-v1-25.3.2 LevelRendererMixin):
         *  afterCollectSubmits(COLLECT_SUBMITS) -> afterRenderSolidFeatures
         *  -> afterRenderTranslucentFeatures -> endMainRender.
         *  Die Zeichenreihenfolge kommt vom RenderType (LINES), nicht vom Haken. */
        LevelRenderEvents.COLLECT_SUBMITS.register(CameraFrustumMeshRenderer::onRender);
        LOG.info("Camera frustum mesh renderer registered (Phase 14)");
    }

    private static void onRender(LevelRenderContext ctx)
    {
        if (!CameraFrustumVisibility.visible) return;

        List<ActiveCameraClipRegistry.Active> active = ActiveCameraClipRegistry.snapshot();
        if (active.isEmpty()) return;

        /* 26.2: LevelRenderContext hat kein camera() mehr. Die Kamera DIESES
         *  Bildes steht im Renderzustand (Extract/Render-Trennung) — der
         *  lebende Camera-Singleton kann zum Zeichenzeitpunkt schon woanders
         *  stehen. javap: LevelRenderState.cameraRenderState,
         *  CameraRenderState.pos (Vec3). */
        CameraRenderState cameraState = ctx.levelState().cameraRenderState;
        if (cameraState == null || cameraState.pos == null) return;

        Vec3 camPos = cameraState.pos;

        /* Frueher standen hier disableCull/enableBlend/defaultBlendFunc/
         *  lineWidth/setShader(getPositionColorShader). Alle fuenf stecken
         *  jetzt in der Pipeline hinter RenderTypes.LINES, am Bytecode geprueft
         *  (RenderPipelines.LINES_SNIPPET): Shader core/rendertype_lines,
         *  BlendFunction.TRANSLUCENT (= SRC_ALPHA/ONE_MINUS_SRC_ALPHA, also
         *  genau der alte defaultBlendFunc), withCull(false), Topologie LINES,
         *  DepthStencilState.DEFAULT. Der alte Aufrufplatz hat den Tiefentest
         *  ebenfalls angelassen — die Zustandslage bleibt damit dieselbe. */
        PoseStack stack = ctx.poseStack();
        SubmitNodeCollector collector = ctx.submitNodeCollector();

        for (ActiveCameraClipRegistry.Active a : active)
        {
            if (!a.poseValid) continue;
            try
            {
                drawFrustum(collector, stack, a, camPos.x, camPos.y, camPos.z);
            }
            catch (Throwable t)
            {
                LOG.debug("frustum mesh draw failed: {}", t.toString());
            }
        }
    }

    private static void drawFrustum(SubmitNodeCollector collector, PoseStack stack,
                                    ActiveCameraClipRegistry.Active a,
                                    double camX, double camY, double camZ)
    {
        /* Translate to clip's pose relative to current main camera so
         *  the mesh aligns with the world position regardless of where
         *  the user is currently viewing from. */
        stack.pushPose();
        stack.translate(a.pose[0] - camX, a.pose[1] - camY, a.pose[2] - camZ);
        stack.mulPose(Axis.YP.rotationDegrees(-(float) a.pose[3]));
        stack.mulPose(Axis.XP.rotationDegrees((float) a.pose[4]));

        /* Der Sammler kopiert die AKTUELLE Pose (javap:
         *  CustomFeatureRenderer$Submit ist ein Record ueber PoseStack$Pose,
         *  nicht ueber den Stack) — popPose() direkt danach ist also sicher. */
        collector.submitCustomGeometry(stack, RenderTypes.LINES,
            (pose, consumer) -> buildMesh(consumer, pose));

        stack.popPose();
    }

    private static void buildMesh(VertexConsumer bb, PoseStack.Pose m)
    {
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
    }

    private static void addLine(VertexConsumer bb, PoseStack.Pose m,
                                float x1, float y1, float z1,
                                float x2, float y2, float z2,
                                float r, float g, float bl)
    {
        /* Die Normale ist bei LINES die Linienrichtung: der Shader
         *  core/rendertype_lines spannt daraus im Bildraum das Band auf.
         *  Ohne sie (oder mit Nullvektor) faellt die Linie zusammen und ist
         *  unsichtbar — ohne Compilerfehler. */
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (len <= 0F) return;

        dx /= len;
        dy /= len;
        dz /= len;

        bb.addVertex(m, x1, y1, z1).setColor(r, g, bl, 1F).setNormal(m, dx, dy, dz).setLineWidth(LINE_WIDTH);
        bb.addVertex(m, x2, y2, z2).setColor(r, g, bl, 1F).setNormal(m, dx, dy, dz).setLineWidth(LINE_WIDTH);
    }

    private static void addBoxLines(VertexConsumer bb, PoseStack.Pose m,
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
