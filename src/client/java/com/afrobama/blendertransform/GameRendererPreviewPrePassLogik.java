package com.afrobama.blendertransform;

import com.afrobama.bbstools.client.campreview.ActiveCameraClipRegistry;
import com.afrobama.bbstools.client.campreview.AltCameraGridRenderer;
import com.afrobama.bbstools.client.campreview.CameraPreviewPerf;
import com.afrobama.bbstools.client.campreview.CameraPreviewProfiler;
import com.afrobama.bbstools.client.campreview.ClipViewportFramebuffers;
import com.afrobama.bbstools.client.mixin.CameraInvokerMixin;
import com.afrobama.bbstools.client.util.PreviewPassState;

import mchorse.bbs_mod.client.BBSRendering;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Logik des welt-skip-alt-kamera-vorschau-mixins — im kernel-paket, damit sie hotswapbar ist.
 *
 * <p>Herausgelöst aus {@code GameRendererPreviewPrePassMixin} (2026-07-26, brücken-umbau). Der
 * handler dort ist jetzt ein einzeiler, der hierher delegiert; die gesamte Phase-15-v3-logik
 * (world-skip alt-camera preview: grid + BBS-replays, kein voller welt-render) lebt hier und
 * kann bei jedem kernel-swap getauscht werden, ohne dass {@code GameRenderer} neu gewoben
 * werden muss.</p>
 *
 * <p>⚠ KEINE KERNEL-TYPEN IN DER SIGNATUR — {@link #render} nimmt nur Minecraft/BBS-typen.
 * {@link CameraInvokerMixin} ist selbst ein auf {@code Camera} GEWOBENES mixin-interface (lebt
 * im stabilen loader wie jeder andere gewobene typ) — kein kernel-typ, daher hier unbedenklich
 * referenzierbar.</p>
 */
public final class GameRendererPreviewPrePassLogik
{
    private GameRendererPreviewPrePassLogik() {}

    private static final Logger LOG = LoggerFactory.getLogger("bbstools/cam-preview/pre-pass");

    public static void render(DeltaTracker tickCounter)
    {
        if (PreviewPassState.PREVIEW_PASS_ACTIVE) return;
        if (!CameraPreviewPerf.ENABLED) return;

        List<ActiveCameraClipRegistry.Active> active = ActiveCameraClipRegistry.snapshot();
        if (active.isEmpty()) return;
        if (CameraPreviewPerf.shouldSkipFrame()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return;

        RenderTarget mainFb = mc.gameRenderer.mainRenderTarget();
        if (mainFb == null) return;

        Camera camera = mc.gameRenderer.mainCamera();
        if (camera == null) return;

        /* Snapshot vanilla state. */
        double savedX = camera.position().x;
        double savedY = camera.position().y;
        double savedZ = camera.position().z;
        float savedYaw = camera.yRot();
        float savedPitch = camera.xRot();
        Matrix4f savedProjection = new Matrix4f(mchorse.bbs_mod.graphics.Projektion.lesen());

        CameraInvokerMixin invoker = (CameraInvokerMixin) (Object) camera;

        Set<UUID> renderedThisFrame = new HashSet<>();

        PreviewPassState.PREVIEW_PASS_ACTIVE = true;
        try
        {
            for (ActiveCameraClipRegistry.Active a : active)
            {
                if (!a.poseValid) continue;

                try
                {
                    TextureTarget fb = ClipViewportFramebuffers.get(a.id);
                    if (fb == null) continue;

                    CameraPreviewProfiler.recordPassStart();

                    /* Pose source: tile in Free Mode publishes an
                     *  override that pins the alt-cam at a static pose
                     *  regardless of clip animation. Falls through to
                     *  the registry's clip-driven pose in Camera Mode. */
                    double[] usePose = a.pose;
                    double[] override = com.afrobama.bbstools.client.campreview.AltCamModeOverrides.FREE_POSES.get(a.id);
                    if (override != null && override.length >= 5)
                    {
                        usePose = override;
                    }

                    /* Mutate Camera singleton — anything that polls
                     *  Camera.getPos()/getYaw() now sees the alt POV. */
                    invoker.bbsRenewed$setPos(usePose[0], usePose[1], usePose[2]);
                    invoker.bbsRenewed$setRotation((float) usePose[3], (float) usePose[4]);

                    /* 26.2: bindWrite/unbindWrite sind entfallen. Ein Renderziel wird nicht mehr global
                     * gebunden, sondern beim Anlegen eines RenderPass benannt -- wer woandershin
                     * zeichnen will, gibt das Ziel dort an. */
                    /* 26.2: viewport, clearColor und clear als globaler GL-Zustand sind entfallen.
                     * Geloescht wird ueber den Befehlsgeber, und der bekommt die Texturen des Ziels;
                     * der Ausschnitt ergibt sich aus dem Ziel selbst. Der macOS-Schalter faellt weg. */
                    RenderSystem.getDevice().createCommandEncoder()
                        .clearColorAndDepthTextures(fb.getColorTexture(), new org.joml.Vector4f(0F, 0F, 0F, 1F),
                            fb.getDepthTexture(), 0.0);

                    /* Build projection matching framebuffer aspect.
                     *  ITER 29: also apply sprint-FOV multiplier here.
                     *  This pre-pass bypasses MC's getFov entirely so
                     *  our GameRendererTrackerSprintFovMixin doesn't
                     *  fire — user reported sprint FOV works in main
                     *  game playback but NOT in editor preview. Apply
                     *  the same multiplier here that the getFov mixin
                     *  would normally apply downstream. */
                    float fov = a.clip.fov.get();
                    float sprintMul = 1F;
                    if (com.afrobama.bbstools.client.tracker.SprintFovState.MANUAL_SPRINT)
                    {
                        sprintMul = 1.3F;
                    }
                    else
                    {
                        /* Read upstream TrackerClientClip.sprintFovFactor
                         *  + lastSprintFovFactor for the auto path. */
                        try
                        {
                            Class<?> cls = Class.forName("mchorse.bbs_mod.camera.clips.misc.TrackerClientClip");
                            float factor = cls.getDeclaredField("sprintFovFactor").getFloat(null);
                            float lastFactor = cls.getDeclaredField("lastSprintFovFactor").getFloat(null);
                            float td = cls.getDeclaredField("currentTickDelta").getFloat(null);
                            sprintMul = lastFactor + (factor - lastFactor) * td;
                        }
                        catch (Throwable ignored) {}
                    }
                    fov *= sprintMul;
                    float aspect = (float) ClipViewportFramebuffers.getWidth()
                        / (float) ClipViewportFramebuffers.getHeight();
                    float near = 0.05F;
                    float far = 1024F;
                    Matrix4f clipProjection = new Matrix4f().perspective(
                        (float) Math.toRadians(fov), aspect, near, far);
                    mchorse.bbs_mod.graphics.Projektion.setzen(clipProjection, com.mojang.blaze3d.ProjectionType.PERSPECTIVE);

                    /* Grid reference plane at player Y. */
                    AltCameraGridRenderer.render(mc, camera, clipProjection);

                    /* BBS overlays (replays/forms) — build the same
                     *  context BBS would receive from Fabric and feed it
                     *  to the BBS render entry point. */
                    /* ⛔ STILLGELEGT IN 26.2 -- hier standen der Aufbau eines Welt-Render-Kontexts
                     * und der Aufruf von BBSRendering.renderCoolStuff, damit BBS-Formen und
                     * Replays IM Blender-Vorschaufenster erscheinen.
                     *
                     * Der Grund ist derselbe wie bei BBSRendering.onRenderChunkLayer und
                     * strukturell, nicht kosmetisch: Geometrie wird in 26.2 bei einem
                     * SubmitNodeCollector EINGEREICHT, und den bekommt man nur INNERHALB eines
                     * Durchgangs gereicht. An dieser Stelle gibt es keinen. Fabrics
                     * WorldRenderContextImpl, aus dem der Kontext frueher gebaut wurde, ist
                     * ebenfalls gestrichen, und sein Fuellstoff (bufferSource) existiert nicht mehr.
                     *
                     * Einen Kontext zusammenzuluegen wuerde uebersetzen und NICHTS zeichnen --
                     * die Vorschau saehe leer aus, ohne dass irgendetwas meldet warum.
                     *
                     * ZURUECKHOLEN: den Sammler von dort durchreichen, wo dieser Vorlauf
                     * aufgehaengt ist, und daraus eine eigene Umsetzung von LevelRenderContext
                     * bauen (fuenf Methoden: submitNodeCollector, poseStack, gameRenderer,
                     * levelRenderer, levelState). Gehoert in die Mixin-Runde. */

                    /* Bone-hover + click pick at alt-cam POV. The
                     *  camera is still mutated and projection still set
                     *  to the clip's perspective here. */
                    try
                    {
                        /* ⛔ MIT STILLGELEGT: der Knochen-Zeiger und der Klick-Griff brauchten
                         * denselben Welt-Render-Kontext, den es hier nicht mehr gibt (Begruendung
                         * oben). Sie haben zusaetzlich ein EIGENES Problem, das auch mit Kontext
                         * bliebe: sie zeichnen einen Stencil-Durchgang und LESEN DANACH SOFORT
                         * PIXEL an der Mausposition. Das setzt voraus, dass der Zug schon durch
                         * ist -- bei eingereichten Auftraegen ist er das nicht.
                         *
                         * ZURUECKHOLEN heisst deshalb nicht nur "Kontext beschaffen", sondern:
                         * eigener Durchgang, danach ein Zaun, dann lesen. Siehe Aufgabe 85. */

                        /* Pick rebinds main FB at end — rebind our
                         *  preview FB so any leftover render hits it
                         *  not the main view. */
                        /* 26.2: bindWrite/unbindWrite sind entfallen. Ein Renderziel wird nicht mehr global
                         * gebunden, sondern beim Anlegen eines RenderPass benannt -- wer woandershin
                         * zeichnen will, gibt das Ziel dort an. */
                        /* 26.2: RenderSystem.viewport ist ersatzlos entfallen. Das Sichtfeld gehoert jetzt zum
                         * Durchgang, nicht zu einem globalen Zustand -- wer in ein anderes Ziel zeichnet, gibt
                         * die Groesse beim Anlegen des Durchgangs an. Hier war es das Zuruecksetzen nach einem
                         * fremden Eingriff; ohne globalen Zustand gibt es nichts zurueckzusetzen. */
                    }
                    catch (Throwable t)
                    {
                        LOG.debug("hover pick failed: {}", t.toString());
                    }

                    CameraPreviewProfiler.recordPassEnd();
                    renderedThisFrame.add(a.id);
                }
                catch (Throwable t)
                {
                    LOG.debug("clip viewport pre-pass failed: {}", t.toString());
                }
            }
            CameraPreviewProfiler.frameEnd();
        }
        finally
        {
            invoker.bbsRenewed$setPos(savedX, savedY, savedZ);
            invoker.bbsRenewed$setRotation(savedYaw, savedPitch);
            mchorse.bbs_mod.graphics.Projektion.setzen(savedProjection, com.mojang.blaze3d.ProjectionType.PERSPECTIVE);

            /* 26.2: bindWrite/unbindWrite sind entfallen. Ein Renderziel wird nicht mehr global
             * gebunden, sondern beim Anlegen eines RenderPass benannt -- wer woandershin
             * zeichnen will, gibt das Ziel dort an. */
            int sw = mc.getWindow().getWidth();
            int sh = mc.getWindow().getHeight();
            /* 26.2: RenderSystem.viewport ist ersatzlos entfallen. Das Sichtfeld gehoert jetzt zum
             * Durchgang, nicht zu einem globalen Zustand -- wer in ein anderes Ziel zeichnet, gibt
             * die Groesse beim Anlegen des Durchgangs an. Hier war es das Zuruecksetzen nach einem
             * fremden Eingriff; ohne globalen Zustand gibt es nichts zurueckzusetzen. */

            PreviewPassState.PREVIEW_PASS_ACTIVE = false;

            ClipViewportFramebuffers.garbageCollect(renderedThisFrame);
        }
    }

    /* buildContext ist ersatzlos entfallen -- siehe die Begruendung oben an der Aufrufstelle.
     * Es baute einen Fabric-internen WorldRenderContextImpl aus einem Sofort-Puffer; beides
     * gibt es in 26.2 nicht mehr. */
}
