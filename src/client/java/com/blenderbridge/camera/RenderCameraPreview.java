package com.blenderbridge.camera;

import com.afrobama.bbstools.client.campreview.CameraPreview;
import com.afrobama.bbstools.client.campreview.CameraPreviewRegistry;
import com.blenderbridge.BridgeProtocol;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.pipeline.RenderTarget;
import mchorse.bbs_mod.BBSModClient;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import java.util.UUID;

/** One independent, bounded render view; never writes a film or editor camera. */
public final class RenderCameraPreview
{
    private static CameraPreview preview;
    private static String session = "";
    private static boolean rendering;
    private static boolean dismissed;
    private static double roll;
    private static double fov = 70;
    private static long received;
    private static long nextFrame;
    private static long frames;

    private RenderCameraPreview() {}

    public static void receive(JsonObject fields)
    {
        String incoming = BridgeProtocol.getFieldString(fields, "previewSession", "");
        close();
        if (fields.has("enabled") && !fields.get("enabled").getAsBoolean())
        {
            return;
        }
        if (dismissed && incoming.equals(session)) return;
        session = incoming;
        dismissed = true;
        JsonObject status = new JsonObject();
        status.addProperty("previewSession", incoming);
        status.addProperty("closed", true);
        status.addProperty("error", "Separate render preview disabled after render crash");
        JsonObject message = BridgeProtocol.envelope("CAMERA_PREVIEW_STATUS", "render-camera",
            com.blenderbridge.BlenderBridgeClient.STATE.nextOutboundSeq(), "bbs");
        message.add("fields", status);
        com.blenderbridge.BlenderBridgeClient.SERVER.send(message);
    }

    public static boolean isRendering() { return rendering || com.afrobama.blendertransform.ZweitKamera.aktiv(); }
    public static boolean owns(UUID id) { return preview != null && preview.id.equals(id); }
    public static RenderTarget target() { return com.afrobama.blendertransform.ZweitKamera.aktiv() ? com.afrobama.blendertransform.ZweitKamera.ziel() : rendering ? preview.framebuffer : null; }
    public static double[] position()
    {
        if (com.afrobama.blendertransform.ZweitKamera.aktiv()) return com.afrobama.blendertransform.ZweitKamera.stellung();
        // CameraController.getYaw() subtracts PI before handing BBS angles to Minecraft.
        return new double[]{preview.posX, preview.posY, preview.posZ, preview.yaw-180, preview.pitch};
    }
    public static float fov() { return com.afrobama.blendertransform.ZweitKamera.aktiv() ? com.afrobama.blendertransform.ZweitKamera.sichtfeld() : (float) fov; }
    public static float roll() { return com.afrobama.blendertransform.ZweitKamera.aktiv() ? 0F : (float) roll; }

    public static void dismiss()
    {
        dismissed = true;
        close();
        JsonObject fields = new JsonObject();
        fields.addProperty("previewSession", session);
        fields.addProperty("closed", true);
        JsonObject message = BridgeProtocol.envelope("CAMERA_PREVIEW_STATUS", "render-camera",
            com.blenderbridge.BlenderBridgeClient.STATE.nextOutboundSeq(), "bbs");
        message.add("fields", fields);
        com.blenderbridge.BlenderBridgeClient.SERVER.send(message);
    }

    private static void close()
    {
        if (preview != null)
        {
            var dashboard = com.blenderbridge.BBSCompat.peekDashboard();
            if (dashboard != null)
            {
                for (var child : new java.util.ArrayList<>(dashboard.main.getChildren()))
                    if (child instanceof com.afrobama.bbstools.client.campreview.UICameraPreviewWindow window
                        && window.getPreview().id.equals(preview.id)) window.removeFromParent();
            }
            CameraPreviewRegistry.remove(preview.id);
        }
        preview = null;
    }

    public static void render(DeltaTracker delta)
    {
        if (rendering || preview == null) return;
        long now = System.nanoTime();
        if (now - received > 3_000_000_000L) { close(); return; }
        Minecraft mc = Minecraft.getInstance();
        if (now < nextFrame || mc.level == null || mc.player == null) return;
        if (BBSModClient.getVideoRecorder().isRecording())
        {
            preview.name = "Blender-Vorschau pausiert (Aufnahme)";
            return;
        }
        nextFrame = now + 100_000_000L;
        CameraPreviewRegistry.ensureFramebuffer(preview);
        if (preview.framebuffer == null) return;
        rendering = true;
        boolean previousWorld = mchorse.bbs_mod.client.BBSRendering.renderingWorld;
        boolean previousCanRender = mchorse.bbs_mod.client.BBSRendering.canRender;
        try
        {
            //? if >=26.1 {
            /*mc.gameRenderer.mainCamera().update(delta);
            mc.gameRenderer.extract(delta, true);
            mc.gameRenderer.gameRenderState().windowRenderState.width = preview.fbWidth;
            mc.gameRenderer.gameRenderState().windowRenderState.height = preview.fbHeight;
            //? if >=26.3 {
            /^mc.gameRenderer.renderLevel();
            ^///?} else {
            mc.gameRenderer.renderLevel(mc.getTimer());
            //?}
            *///?} else {
            /** Vor 26.1 holt renderLevel die Kamera selbst; das Seitenverhaeltnis bleibt das des
             * Fensters (kein Fenster-Zustand zum Umstellen). **/
            mc.gameRenderer.renderLevel(mc.getTimer());
            //?}
            frames++;
            preview.name = "Blender-Kamera";
        }
        catch (RuntimeException error)
        {
            preview.name = "Blender-Kamera: " + error.getClass().getSimpleName();
            nextFrame = now + 1_000_000_000L;
            org.slf4j.LoggerFactory.getLogger(RenderCameraPreview.class).error("Render camera failed", error);
        }
        finally
        {
            rendering = false;
            //? if >=26.1 {
            /*mc.gameRenderer.mainCamera().update(delta);
            mc.gameRenderer.extract(delta, true);
            *///?}
            mchorse.bbs_mod.client.BBSRendering.renderingWorld = previousWorld;
            mchorse.bbs_mod.client.BBSRendering.canRender = previousCanRender;
        }
    }
}
