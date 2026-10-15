package com.afrobama.blendertransform;

import com.afrobama.bbstools.client.campreview.ActiveCameraClipRegistry;
import com.afrobama.bbstools.client.campreview.AltCameraGridRenderer;
import com.afrobama.bbstools.client.campreview.CameraPreviewPerf;
import com.afrobama.bbstools.client.campreview.CameraPreviewProfiler;
import com.afrobama.bbstools.client.campreview.ClipViewportFramebuffers;
import com.afrobama.bbstools.client.mixin.CameraInvokerMixin;
import com.afrobama.bbstools.client.util.PreviewPassState;

import mchorse.bbs_mod.client.BBSRendering;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
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
        if (PreviewPassState.PREVIEW_PASS_ACTIVE || ZweitKamera.aktiv()) return;
        if (!CameraPreviewPerf.ENABLED) return;

        List<ActiveCameraClipRegistry.Active> active = ActiveCameraClipRegistry.snapshot();
        if (active.isEmpty()) return;
        if (CameraPreviewPerf.shouldSkipFrame()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) return;
        /* A video export owns the frame (same rule as RenderCameraPreview). */
        if (mchorse.bbs_mod.BBSModClient.getVideoRecorder().isRecording()) return;

        Set<UUID> renderedThisFrame = new HashSet<>();

        PreviewPassState.PREVIEW_PASS_ACTIVE = true;
        try
        {
            for (ActiveCameraClipRegistry.Active a : active)
            {
                if (!a.poseValid) continue;

                TextureTarget fb = ClipViewportFramebuffers.get(a.id);
                if (fb == null) continue;

                /* Tile in Free Mode pins the alt-cam at a static pose (AltCamModeOverrides). */
                double[] usePose = a.pose;
                double[] override = com.afrobama.bbstools.client.campreview.AltCamModeOverrides.FREE_POSES.get(a.id);
                if (override != null && override.length >= 5)
                {
                    usePose = override;
                }

                CameraPreviewProfiler.recordPassStart();

                try
                {
                    ZweitKamera.zeichnen(tickCounter, fb, usePose, a.clip.fov.get());
                    renderedThisFrame.add(a.id);
                }
                catch (Throwable t)
                {
                    /* Loud, once per clip: a black window with nothing in the log cost a whole audit round. */
                    if (GEMELDET.add(a.id))
                    {
                        LOG.error("Preview camera {}: world pass failed, window stays black", a.id, t);
                    }
                }

                CameraPreviewProfiler.recordPassEnd();
            }
            CameraPreviewProfiler.frameEnd();
        }
        finally
        {
            PreviewPassState.PREVIEW_PASS_ACTIVE = false;
            ClipViewportFramebuffers.garbageCollect(renderedThisFrame);
        }
    }

    private static final Set<UUID> GEMELDET = new HashSet<>();
}
