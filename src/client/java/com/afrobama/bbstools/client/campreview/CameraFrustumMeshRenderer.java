// Coded by Afrobama/Claude — Phase 14 Blender-style camera frustum mesh
package com.afrobama.bbstools.client.campreview;

import com.afrobama.bbstools.client.util.CameraFrustumVisibility;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
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
 * {@link #register()} und {@link #onRender(WorldRenderContext)}.</p>
 */
public final class CameraFrustumMeshRenderer
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/cam-preview/frustum-mesh");

    /* 26.2: RenderSystem.lineWidth() gibt es nicht mehr. Die Linienbreite ist
     *  jetzt ein VERTEX-ATTRIBUT — RenderPipelines.LINES bindet
     *  DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH (javap auf
     *  RenderPipelines.<clinit>, LINES_SNIPPET). Vanilla setzt es je Vertex,
     *  siehe ShapeOutlineFeatureRenderer (addVertex/setColor/setNormal/setLineWidth). */
    /** Breite in Pixeln des ZIELPUFFERS — nicht des Bildschirms. Siehe {@link #linienbreite()}. */
    private static final float LINE_WIDTH = 2F;

    /**
     * Die Linienbreite, hochgerechnet auf die Verkleinerung des Vorschaufeldes.
     *
     * <h2>⛔ Warum eine feste Zahl hier falsch ist (gemessen 2026-08-03)</h2>
     *
     * <p>1.21.1 rief {@code RenderSystem.lineWidth(2F)} und zeichnete direkt ins Fenster: zwei
     * Pixel blieben zwei Pixel. In 26.2 ist die Breite ein Vertexattribut, der Shader rechnet
     * {@code lineOffset = LineWidth / ScreenSize} — auch das ergibt Pixel, aber Pixel <b>des
     * Zielpuffers</b>. Und der Film-Editor zeichnet nicht ins Fenster, sondern in den
     * Videopuffer (1280x720) und blittet den danach ins Vorschaufeld.</p>
     *
     * <p>Gemessen mit unveraendertem {@code LINE_WIDTH = 2F}:</p>
     * <pre>
     *   im Videopuffer 1280x720 : Median 3 px   (Shader arbeitet korrekt)
     *   im Vorschaufeld  472 px : etwa 1 px     (Puffer um Faktor 2,7 geschrumpft)
     * </pre>
     *
     * <p>Die Zahl wurde also richtig portiert und trotzdem duenner — der Umweg ueber den
     * Videopuffer kam dazwischen. Deshalb wird sie hier gegen das Verhaeltnis gerechnet
     * statt hochgesetzt: eine feste 5F waere bei einem anderen Fenster oder einer anderen
     * Videoaufloesung wieder falsch, nur in die andere Richtung.</p>
     *
     * <p>⚠ Nach oben begrenzt: bei einem sehr kleinen Vorschaufeld liefe der Faktor sonst
     * davon und der Frustum wuerde zum Klotz.</p>
     */
    private static float linienbreite()
    {
        try
        {
            mchorse.bbs_mod.ui.film.UIFilmPanel panel = mchorse.bbs_mod.BBSModClient.getDashboard()
                .getPanel(mchorse.bbs_mod.ui.film.UIFilmPanel.class);

            int feld = Math.round(panel.preview.getViewport().w * mchorse.bbs_mod.BBSModClient.getGUIScale());
            int puffer = mchorse.bbs_mod.client.BBSRendering.getVideoWidth();

            if (feld <= 0 || puffer <= 0)
            {
                return LINE_WIDTH;
            }

            return LINE_WIDTH * Math.min(4F, Math.max(1F, puffer / (float) feld));
        }
        catch (Throwable t)
        {
            /* Kein Filmpanel (Weltansicht, Hauptmenue): dann wird direkt gezeichnet und die
             * unveraenderte Breite ist die richtige. Ein fehlendes Panel ist kein Fehler. */
            return LINE_WIDTH;
        }
    }

    private CameraFrustumMeshRenderer() {}

    public static void register()
    {
        /* 26.2: AFTER_TRANSLUCENT_TERRAIN EXISTIERT (javap auf WorldRenderEvents) --
         *  taugt hier aber nicht:
         *  eingereichte Geometrie muss in der Sammelphase abgegeben werden,
         *  sonst sind die Feature-Renderer schon durch und es wird STILL
         *  nichts gezeichnet (kompiliert, bleibt unsichtbar).
         *
         *  Reihenfolge am Fabric-Mixin abgelesen (javap auf
         *  fabric-rendering-v1-25.3.2 LevelRendererMixin):
         *  afterCollectSubmits(COLLECT_SUBMITS) -> afterRenderSolidFeatures
         *  -> afterRenderTranslucentFeatures -> endMainRender.
         *  Die Zeichenreihenfolge kommt vom RenderType (LINES), nicht vom Haken. */
        WorldRenderEvents.AFTER_ENTITIES.register(CameraFrustumMeshRenderer::onRender);
        LOG.info("Camera frustum mesh renderer registered (Phase 14)");
    }

    private static void onRender(WorldRenderContext ctx)
    {
        if (!CameraFrustumVisibility.visible) return;

        List<ActiveCameraClipRegistry.Active> active = ActiveCameraClipRegistry.snapshot();
        if (active.isEmpty()) return;

        /* 26.2: WorldRenderContext hat kein camera() mehr. Die Kamera DIESES
         *  Bildes steht im Renderzustand (Extract/Render-Trennung) — der
         *  lebende Camera-Singleton kann zum Zeichenzeitpunkt schon woanders
         *  stehen. javap: LevelRenderState.cameraRenderState,
         *  CameraRenderState.pos (Vec3). */
        CameraRenderState cameraState = ctx.worldState().cameraRenderState;
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
        PoseStack stack = ctx.matrices();
        SubmitNodeCollector collector = ctx.commandQueue();

        if (mchorse.bbs_mod.graphics.Messpunkt.istAn())
        {
            /* V1-Messpunkt: Submit-Eingaenge (Collector, Pose, Pass, Rufer) — zum
             * Nebeneinanderlegen mit anderen Zeichnern bei Sichtbarkeits-Jagden. */
            org.joml.Matrix4f pose = stack.last().pose();
            String pass = (mchorse.bbs_mod.film.FilmControllerContext.sammelDurchgang ? "sammel" : "direkt")
                + (mchorse.bbs_mod.forms.FormSammler.aktiv() != null ? "+fs" : "");

            mchorse.bbs_mod.graphics.Messpunkt.zaehle("frustum.ein." + pass,
                "col=" + System.identityHashCode(collector)
                    + " pose=" + pose.m30() + "/" + pose.m31() + "/" + pose.m32()
                    + " kam=" + camPos.x + "/" + camPos.y + "/" + camPos.z
                    + " von=" + mchorse.bbs_mod.graphics.Messpunkt.rufer(3));
        }

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
        stack.rotate(Axis.YP.rotationDegrees(-(float) a.pose[3]));
        stack.rotate(Axis.XP.rotationDegrees((float) a.pose[4]));

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

        bb.addVertex(m, x1, y1, z1).setColor(r, g, bl, 1F).setNormal(m, dx, dy, dz).setLineWidth(linienbreite());
        bb.addVertex(m, x2, y2, z2).setColor(r, g, bl, 1F).setNormal(m, dx, dy, dz).setLineWidth(linienbreite());
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
