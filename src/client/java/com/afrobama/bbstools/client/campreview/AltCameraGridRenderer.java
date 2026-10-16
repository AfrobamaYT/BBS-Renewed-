// Coded by Afrobama/Claude — Phase 15 v4: reference grid via proper matrix stack
package com.afrobama.bbstools.client.campreview;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import mchorse.bbs_mod.client.FormPipelines;
import mchorse.bbs_mod.graphics.SofortZeichner;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
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

    /** Strichbreite in Pixeln des Zielpuffers. Wirkt erst, seit das Vertexformat das
     *  Attribut LineWidth ueberhaupt traegt — vorher war jede Linie einen Pixel breit,
     *  egal welcher Wert hier stand (am Frame gemessen 2026-08-03). */
    private static final float LINIENBREITE = 2F;

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

            /* 26.2: Camera.getPosition() heisst jetzt position()
             *  (javap: public net.minecraft.world.phys.Vec3 position()). */
            float camX = (float) camera.position().x;
            float camY = (float) camera.position().y;
            float camZ = (float) camera.position().z;
            float pitchRad = (float) Math.toRadians(camera.xRot());
            float yawRad = (float) Math.toRadians(camera.yRot() + 180F);

            /* ⛔ 26.2 — AB HIER STEHENGELASSEN, MIT GRUND (nicht erfunden).
             *
             *  Was hier fehlt, ist keine Umbenennung, sondern ein fehlendes
             *  Argument: 26.2 kennt kein "gerade gebundenes Ziel" mehr. Jeder
             *  Zeichenschritt oeffnet einen RenderPass auf KONKRETE Sichten
             *  (createRenderPass(name, colorView, .., depthView, ..)). Diese
             *  Methode bekommt aber nur (Minecraft, Camera, Matrix4f) — das
             *  Alt-Kamera-Ziel liegt beim Aufrufer
             *  (com.afrobama.blendertransform.GameRendererPreviewPrePassLogik:156,
             *  dort als TextureTarget fb), und der ist NICHT in meinem Manifest.
             *  Ich kann die Schnittstelle nicht einseitig aendern und darf das
             *  Ziel nicht raten — auf den Hauptpuffer zu zeichnen waere ein
             *  falsches Bild ohne Fehlermeldung.
             *
             *  Der Aufrufer uebersetzt in 26.2 selbst nicht (bindWrite, viewport,
             *  clearColor, ON_OSX, setProjectionMatrix(Matrix4f, VertexSorting),
             *  WorldRenderContext). Wer ihn portiert, entscheidet die neue
             *  Schnittstelle; danach ist das hier in einem Zug zu machen.
             *
             *  Alles dafuer Noetige ist am 26.2-Jar bereits nachgemessen:
             *   - Zustand: RenderTypes.LINES traegt Shader core/rendertype_lines,
             *     BlendFunction.TRANSLUCENT (== altes defaultBlendFunc),
             *     withCull(false), Topologie LINES, DepthStencilState.DEFAULT.
             *     Ueber RenderType.pipeline()/format()/primitiveTopology()
             *     bekommt man Pipeline, Vertexformat und Topologie, ohne eine
             *     eigene Pipeline bauen zu muessen. ACHTUNG: das Format ist
             *     POSITION_COLOR_NORMAL_LINE_WIDTH — Linienrichtung als Normale
             *     und Linienbreite gehoeren jetzt an JEDEN Vertex
             *     (RenderSystem.lineWidth() ist weg; disableDepthTest hat kein
             *     Gegenstueck mehr, das muesste eine eigene Pipeline sein).
             *   - Modelview: RenderSystem.getDynamicUniforms().writeTransform(
             *     viewMatrix, farbe) -> GpuBufferSlice, im Pass als
             *     setUniform("DynamicTransforms", slice). applyModelViewMatrix()
             *     existiert nicht mehr; der Stack allein bewirkt nichts.
             *   - Projektion: RenderSystem.setProjectionMatrix erwartet
             *     GpuBufferSlice + ProjectionType. Den Slice liefert
             *     net.minecraft.client.renderer.ProjectionMatrixBuffer
             *     (getBuffer(Matrix4f)), nicht mehr die Matrix direkt.
             *   - Umgekehrte Tiefe beachten: DEFAULT_DEPTH_CLEAR_VALUE = 0.0d,
             *     DepthStencilState.DEFAULT vergleicht GREATER_THAN_OR_EQUAL.
             *
             *  Build view matrix the same way MC's GameRenderer does:
             *  Rx(pitch) * Ry(yaw+180) * T(-camera). Push onto the
             *  modelview stack so the shader picks it up. */
            Matrix4fStack mvStack = RenderSystem.getModelViewStack();
            mvStack.pushMatrix();
            mvStack.identity();
            mvStack.rotateX(pitchRad);
            mvStack.rotateY(yawRad);
            mvStack.translate(-camX, -camY, -camZ);
            /* 26.2: applyModelViewMatrix ist ersatzlos entfallen -- es lud den Modelview-Stapel nach GL hoch. Jetzt liest jeder Zeichenweg den Stapel selbst. */

            /* Umkehren: BBS-eigene Matrix in 26.2s GREATER_EQUAL-Test, Beleg an Projektion#setzenUndUmkehren. In BILDPUNKTEN an dieser Stelle ungemessen. */
            mchorse.bbs_mod.graphics.Projektion.setzenUndUmkehren(projection, com.mojang.blaze3d.ProjectionType.PERSPECTIVE);

            /* 26.2: Cull und Blend sind kein Schalter mehr, sondern Teil der
             *  Pipeline — ersatzlos entfallen. Fuer diese Zeichnung traegt den
             *  Zustand die LINES-Pipeline (oben nachgemessen: withCull(false)
             *  == altes disableCull, BlendFunction.TRANSLUCENT == altes
             *  enableBlend + defaultBlendFunc). Wer den Zeichenpfad unten
             *  portiert, muss genau die waehlen — sonst wird das Gitter still
             *  undurchsichtig und die Sortierung kippt. */

            /* ⛔ OFFEN 26.2 — steht absichtlich noch da, nicht geraten:
             *  Das Gitter will OHNE Tiefenpruefung zeichnen, und das ist hier
             *  doppelt tragend. Erstens soll es durch Geometrie sichtbar
             *  bleiben. Zweitens schreibt GL bei abgeschalteter Pruefung auch
             *  keine Tiefe — sonst legt das 256-Bloecke-Gitter eine Tiefenwand
             *  in den Alt-Kamera-Puffer und verdeckt den nachfolgenden
             *  Form-Pass. LINES traegt DepthStencilState.DEFAULT (Pruefung UND
             *  Schreiben an), trifft es also nicht, und unter den fertigen
             *  RenderPipelines ist keine ohne Tiefe. Das waere eine eigene
             *  Pipeline — zu bauen zusammen mit dem RenderPass oben. */
            /* 26.2: disableDepthTest ist entfallen -- der Zustand steckt in der Pipeline
             * des Zuges. Absicht war: Tiefentest aus. */
            /* 26.2: setShader ist entfallen -- der Shader steht in der Pipeline, die der
             * Zeichenaufruf waehlt. Frueher galt er fuer alles Folgende. */

            /* 26.2: SofortZeichner statt Tesselator + BufferUploader. Den Zustand traegt die
             *  Pipeline, und zwar genau den oben nachgemessenen: mischen (LINES faehrt
             *  BlendFunction.TRANSLUCENT == altes enableBlend + defaultBlendFunc), KEINE
             *  Tiefenpruefung — und damit auch kein Tiefenschreiben, was hier der eigentliche
             *  Punkt ist (siehe ⛔ oben: sonst legt das 256-Bloecke-Gitter eine Tiefenwand in
             *  den Puffer und verdeckt den nachfolgenden Form-Pass) — und KEIN Culling
             *  (withCull(false) == altes disableCull).
             *  Die Modellansicht ist die oben aufgebaute Sichtmatrix: der Modelview-Stack
             *  allein bewirkt in 26.2 nichts mehr, SofortZeichner schreibt sie als
             *  DynamicTransforms in den Durchgang.
             *
             *  ⚠️ WEITERHIN OFFEN — das ZIEL, nicht der Zeichenweg: SofortZeichner oeffnet
             *  seinen Durchgang gegen gameRenderer.mainRenderTarget(), nicht gegen den
             *  Alt-Kamera-Puffer des Aufrufers. Wer die Schnittstelle dieser Methode um das
             *  Ziel erweitert (siehe ⛔ oben), muss den Durchgang dorthin fuehren; bis dahin
             *  ist diese Methode NICHT portiert, auch wenn der Zeichenblock uebersetzt ist. */
            try (ByteBufferBuilder speicher = new ByteBufferBuilder((2 * HALF_EXTENT + 1) * 4 * 24))
            {
                /* Vier Ecken je Schritt, 24 Byte je Ecke in POSITION_COLOR_NORMAL_LINE_WIDTH
                 * — von vornherein gross genug, damit der Puffer waehrend des Aufbaus nicht
                 * nachwaechst.
                 *
                 * ⛔ FRUEHER STANDEN HIER POSITION_COLOR UND 16 BYTE. Das war der Grund,
                 * warum die Gitterlinien haarfein waren: der Vanilla-Linienshader rechnet
                 * `lineOffset = richtung * LineWidth / ScreenSize`, und ein Format ohne
                 * LineWidth liefert dort eine Null. Am Frame gemessen (RenderDoc,
                 * 2026-08-03): der einzige Linienaufruf des Bildes hatte stride 16, zwei
                 * Attribute und die Shader-Eingaenge "Color, Position" — 2052 Indizes,
                 * exakt (2*256+1)*4, also dieses Gitter. */
                BufferBuilder bb = SofortZeichner.beginnen(
                    speicher, PrimitiveTopology.LINES, RenderTypes.LINES.format());

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

                    /* Die Normale ist bei Linien die LINIENRICHTUNG, nicht die
                     * Flaechennormale — der Shader spannt die Linie senkrecht dazu auf.
                     * Mit Nullvektor faellt sie zusammen. */

                    /* Line parallel to Z axis (varying X = fx). */
                    bb.addVertex(fx, fy, fzMin).setColor(r, g, b, alpha)
                        .setNormal(0F, 0F, 1F).setLineWidth(LINIENBREITE);
                    bb.addVertex(fx, fy, fzMax).setColor(r, g, b, alpha)
                        .setNormal(0F, 0F, 1F).setLineWidth(LINIENBREITE);

                    /* Line parallel to X axis (varying Z = fz). */
                    bb.addVertex(fxMin, fy, fz).setColor(r, g, b, alpha)
                        .setNormal(1F, 0F, 0F).setLineWidth(LINIENBREITE);
                    bb.addVertex(fxMax, fy, fz).setColor(r, g, b, alpha)
                        .setNormal(1F, 0F, 0F).setLineWidth(LINIENBREITE);
                }

                /* Vanillas Linien-Pipeline statt der eigenen POSITION_COLOR-Fassung: nur
                 * sie traegt den Shader, der LineWidth ueberhaupt auswertet. Tiefenpruefung
                 * bleibt AUS wie bisher (das Gitter soll durch Geometrie sichtbar bleiben),
                 * Tiefe wird weiterhin nicht geschrieben. */
                SofortZeichner.zeichnen(
                    FormPipelines.linien(false),
                    bb.build(),
                    mvStack,
                    null,
                    null);
            }

            /* 26.2: ersatzlos entfallen. Die drei Aufrufe haben nur den
             *  globalen Zustand aufgeraeumt — den gibt es nicht mehr. Blend,
             *  Tiefe und Cull haengen an der Pipeline, und die bringt jede
             *  folgende Zeichnung selbst mit. Nichts zurueckzusetzen. */

            mvStack.popMatrix();
            /* 26.2: applyModelViewMatrix ist ersatzlos entfallen -- es lud den Modelview-Stapel nach GL hoch. Jetzt liest jeder Zeichenweg den Stapel selbst. */

            if (!loggedFirstRender)
            {
                loggedFirstRender = true;
                /* 26.2: getYRot() heisst jetzt yRot() (javap: public float yRot()). */
                LOG.info("Grid first-render OK at player Y={} (camera @ {},{},{} pitch={} yaw={})",
                    gy, camX, camY, camZ, camera.xRot(), camera.yRot());
            }
        }
        catch (Throwable t)
        {
            LOG.error("Grid render FAILED: " + t, t);
        }
    }

}
