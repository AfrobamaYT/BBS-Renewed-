package com.afrobama.blendertransform;

import com.afrobama.bbstools.client.campreview.ActiveCameraClipRegistry;
import com.afrobama.bbstools.client.campreview.AltCameraGridRenderer;
import com.afrobama.bbstools.client.campreview.AltCamModeOverrides;
import com.afrobama.bbstools.client.campreview.BoneHoverPickRunner;
import com.afrobama.bbstools.client.campreview.CameraPreviewPerf;
import com.afrobama.bbstools.client.campreview.CameraPreviewProfiler;
import com.afrobama.bbstools.client.campreview.ClipViewportFramebuffers;
import com.afrobama.bbstools.client.mixin.CameraInvokerMixin;
import com.afrobama.bbstools.client.util.PreviewPassState;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.FilmControllerContext;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormSammler;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.graphics.InverseView;
import mchorse.bbs_mod.graphics.Projektion;
import mchorse.bbs_mod.graphics.Zeichenziel;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Renders film actors and the reference grid into each additional camera's own target. */
public final class GameRendererPreviewPrePassLogik
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/cam-preview/pre-pass");

    public static void render(DeltaTracker ticks)
    {
        if (PreviewPassState.PREVIEW_PASS_ACTIVE || !CameraPreviewPerf.ENABLED)
        {
            return;
        }
        List<ActiveCameraClipRegistry.Active> active = ActiveCameraClipRegistry.snapshot();
        if (active.isEmpty() || CameraPreviewPerf.shouldSkipFrame())
        {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.level == null)
        {
            return;
        }
        Set<UUID> rendered = new HashSet<>();
        PreviewPassState.PREVIEW_PASS_ACTIVE = true;
        try
        {
            for (ActiveCameraClipRegistry.Active camera : active)
            {
                if (!camera.poseValid)
                {
                    continue;
                }
                try
                {
                    TextureTarget target = ClipViewportFramebuffers.get(camera.id);
                    if (target != null)
                    {
                        CameraPreviewProfiler.recordPassStart();
                        renderCamera(client, camera, target, ticks);
                        CameraPreviewProfiler.recordPassEnd();
                        rendered.add(camera.id);
                    }
                }
                catch (RuntimeException exception)
                {
                    LOG.error("Additional camera {} failed", camera.id, exception);
                }
            }
        }
        finally
        {
            CameraPreviewProfiler.frameEnd();
            PreviewPassState.PREVIEW_PASS_ACTIVE = false;
            ClipViewportFramebuffers.garbageCollect(rendered);
        }
    }

    private static void renderCamera(Minecraft client, ActiveCameraClipRegistry.Active active,
        TextureTarget target, DeltaTracker ticks)
    {
        Camera camera = client.gameRenderer.mainCamera();
        net.minecraft.world.phys.Vec3 previousPosition = camera.position();
        float previousYaw = camera.yRot();
        float previousPitch = camera.xRot();
        GpuBufferSlice previousProjection = RenderSystem.getProjectionMatrixBuffer();
        ProjectionType previousType = RenderSystem.getProjectionType();
        GpuBufferSlice previousLights = RenderSystem.getShaderLights();
        com.mojang.blaze3d.textures.GpuTextureView previousColor = RenderSystem.outputColorTextureOverride;
        com.mojang.blaze3d.textures.GpuTextureView previousDepth = RenderSystem.outputDepthTextureOverride;
        com.mojang.blaze3d.textures.GpuTextureView modelColor = Zeichenziel.farbe();
        com.mojang.blaze3d.textures.GpuTextureView modelDepth = Zeichenziel.tiefe();
        com.mojang.blaze3d.systems.ScissorState scissor =
            new com.mojang.blaze3d.systems.ScissorState(RenderSystem.getScissorStateForRenderTypeDraws());
        Matrix4f previousView = new Matrix4f(BBSRendering.camera);
        Matrix3f previousInverse = new Matrix3f(InverseView.get());
        boolean queueActive = FormTranslucentQueue.suspend();
        CameraInvokerMixin invoker = (CameraInvokerMixin) (Object) camera;
        double[] pose = AltCamModeOverrides.FREE_POSES.getOrDefault(active.id, active.pose);
        RenderSystem.getModelViewStack().pushMatrix();
        try (ProjectionMatrixBuffer projection = new ProjectionMatrixBuffer("bbs-additional-camera"))
        {
            invoker.bbsRenewed$setPos(pose[0], pose[1], pose[2]);
            invoker.bbsRenewed$setRotation((float) pose[3], (float) pose[4]);
            Matrix4f view = new Matrix4f().rotation(camera.rotation().conjugate(new org.joml.Quaternionf()));
            BBSRendering.camera.set(view);
            InverseView.set(new Matrix3f().rotation(camera.rotation()));
            RenderSystem.getModelViewStack().set(view);
            double fov = com.afrobama.bbstools.client.tracker.SprintFovState.MANUAL_SPRINT
                ? active.clip.fov.get() * 1.3D
                : mchorse.bbs_mod.camera.clips.misc.TrackerClientClip.adjustFovForSprint(active.clip.fov.get());
            Matrix4f perspective = new Matrix4f().perspective((float) Math.toRadians(fov),
                target.width / (float) target.height, 0.05F, 1024F);
            RenderSystem.setProjectionMatrix(projection.getBuffer(Projektion.umgekehrt(perspective)), ProjectionType.PERSPECTIVE);
            RenderSystem.outputColorTextureOverride = target.getColorTextureView();
            RenderSystem.outputDepthTextureOverride = target.getDepthTextureView();
            Zeichenziel.setzen(target.getColorTextureView(), target.getDepthTextureView());
            RenderSystem.disableScissorForRenderTypeDraws();
            RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(target.getColorTexture(),
                new Vector4f(0F, 0F, 0F, 1F), target.getDepthTexture(), 0D);
            AltCameraGridRenderer.render(client, camera, perspective, target);
            renderActors(client, camera, ticks.getGameTimeDeltaPartialTick(false));
            CameraRenderState pickCamera = new CameraRenderState();
            pickCamera.pos = camera.position();
            pickCamera.xRot = camera.xRot();
            pickCamera.yRot = camera.yRot();
            pickCamera.orientation = new org.joml.Quaternionf(camera.rotation());
            BoneHoverPickRunner.runHover(active.id, pickCamera, target.width, target.height);
        }
        finally
        {
            invoker.bbsRenewed$setPos(previousPosition.x, previousPosition.y, previousPosition.z);
            invoker.bbsRenewed$setRotation(previousYaw, previousPitch);
            RenderSystem.getModelViewStack().popMatrix();
            BBSRendering.camera.set(previousView);
            InverseView.set(previousInverse);
            RenderSystem.setProjectionMatrix(previousProjection, previousType);
            RenderSystem.setShaderLights(previousLights);
            RenderSystem.getScissorStateForRenderTypeDraws().setFrom(scissor);
            RenderSystem.outputColorTextureOverride = previousColor;
            RenderSystem.outputDepthTextureOverride = previousDepth;
            Zeichenziel.setzen(modelColor, modelDepth);
            FormTranslucentQueue.restore(queueActive);
        }
    }

    private static void renderActors(Minecraft client, Camera camera, float transition)
    {
        UIFilmPanel panel = BoneHoverPickRunner.findPanel();
        if (panel == null || panel.getData() == null)
        {
            return;
        }
        CameraRenderState state = new CameraRenderState();
        state.pos = camera.position();
        state.xRot = camera.xRot();
        state.yRot = camera.yRot();
        state.orientation = new org.joml.Quaternionf(camera.rotation());
        SubmitNodeStorage collector = new SubmitNodeStorage();
        FormSammler.mitSammler(collector, () ->
        {
            for (java.util.Map.Entry<Integer, IEntity> actor : panel.getController().getEntities().entrySet())
            {
                if (actor.getKey() >= panel.getData().replays.getList().size())
                {
                    continue;
                }
                Replay replay = panel.getData().replays.getList().get(actor.getKey());
                if (replay.enabled.get())
                {
                    BaseFilmController.renderEntity(FilmControllerContext.instance.setup(panel.getController().getEntities(),
                        actor.getValue(), replay, state, new PoseStack(), collector, transition).relative(replay.relative.get()));
                }
            }
        });
        try (FeatureRenderDispatcher dispatcher = new FeatureRenderDispatcher(client.gameRenderer.renderBuffers(),
            client.getModelManager(), client.getAtlasManager(), client.font, client.gameRenderer.gameRenderState()))
        {
            dispatcher.renderAllFeatures(collector);
        }
    }

    private GameRendererPreviewPrePassLogik()
    {}
}
