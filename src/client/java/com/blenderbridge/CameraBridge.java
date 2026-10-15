// Coded by Afrobama/Claude — Blender<->BBS live-sync bridge (Phase 1 camera bridge).
package com.blenderbridge;

import com.google.gson.JsonObject;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.controller.CameraController;
import mchorse.bbs_mod.utils.MathUtils;

/**
 * CameraBridge — reads the live BBS editor/play camera and applies inbound Blender
 * camera state onto it.
 *
 * <p>WHICH CAMERA: BBS drives a single shared {@link Camera} owned by the global
 * {@link CameraController} ({@code BBSModClient.getCameraController().camera}). Every render
 * frame {@code CameraMixin.onUpdate} (mixin on {@code net.minecraft.client.render.Camera})
 * calls {@code controller.setup(controller.camera, tickDelta)} and then copies that camera's
 * position/rotation into the vanilla MC camera. The active {@code ICameraController}
 * (PlayCameraController during playback, RunnerCameraController in the film editor,
 * OrbitCameraController for orbit) writes the frame's final values into that {@link Camera}
 * via {@code setup(...)}. So {@code getCameraController().camera} IS "the current camera" —
 * position (x,y,z world units), rotation (x=pitch, y=yaw, z=roll, all radians) and fov (rad).
 * This is the exact object the {@code PositionFovMixin} FOV work and the whole render path
 * read from, verified by that mixin's 29-iter comment.</p>
 *
 * <p>COORDINATE SPACE — IMPORTANT: this class works purely in BBS space. The Blender&lt;-&gt;BBS
 * axis/angle remap (bbs_x=blender_x; bbs_y=blender_z; bbs_z=-blender_y; bbs_pitch=90-blender_pitch;
 * bbs_yaw=-blender_yaw; bbs_roll=blender_roll — from the Fingle reference exporter) is done on
 * the Blender add-on side / in the message construction, NOT here. The CAMERA envelope
 * ({@link BridgeProtocol#camera}) already carries BBS-space degrees + world-unit position.
 * {@link #readCurrentCamera()} emits BBS space; {@link #applyCamera} consumes BBS space.</p>
 *
 * <p>ANGLE MAPPING (BBS Camera radians &lt;-&gt; BBS Angle degrees), verified against
 * {@code Position.apply(Camera)} and {@code Angle.set(Camera)} in the BBS source:</p>
 * <pre>
 *   pitch_deg = toDeg(camera.rotation.x)      camera.rotation.x = toRad(pitch_deg)
 *   yaw_deg   = toDeg(camera.rotation.y)      camera.rotation.y = toRad(yaw_deg)
 *   roll_deg  = toDeg(camera.rotation.z)      camera.rotation.z = toRad(roll_deg)
 *   fov_deg   = toDeg(camera.fov)             camera.fov        = toRad(fov_deg)
 * </pre>
 * These are the raw BBS keyframe-channel degrees the Fingle exporter reads/writes (the BBS
 * clip's x/y/z/pitch/yaw/roll/fov channels feed {@code Angle} which feeds {@code rotation}).
 * NOTE: this is intentionally NOT {@code CameraController.getYaw()} — that helper subtracts PI
 * (the Minecraft camera hand-off convention) and would be wrong for the Blender bridge.
 *
 * <p>THREADING: every method here MUST be called on the client main thread (the socket threads
 * never touch BBS state). {@link #readCurrentCamera()} runs from the outbound tick handler;
 * {@link #applyCamera} runs from the inbound tick handler while draining
 * {@link BridgeState#takePendingCamera()}.</p>
 */
public final class CameraBridge
{
    /* ---- Dirty-check state (last camera we PUSHED to Blender) ---- */

    /**
     * Snapshot of the last camera state emitted by {@link #readCurrentCamera()}. Compared in
     * {@link #cameraChanged()} so we only push on real change. Indexed by the CAM_* channels:
     * [0]=x [1]=y [2]=z [3]=pitch [4]=yaw [5]=roll [6]=fov. All in BBS space (see class-doc).
     * Main-thread-only, so a plain array is fine.
     */
    private static final double[] lastPushed = new double[7];

    /** True once {@link #readCurrentCamera()} has recorded a baseline into {@link #lastPushed}. */
    private static boolean hasBaseline;

    /**
     * Epsilon for the dirty-check. BBS camera values are floats; sub-epsilon jitter (fov in
     * degrees, sub-millimetre position) is not worth a network frame. Position and angles share
     * one threshold — both are O(1..1000) magnitude here, so 1e-4 is comfortably below any
     * user-visible move while still killing float noise.
     */
    private static final double EPSILON = 1.0E-4D;

    private CameraBridge()
    {
        /* static-only helper */
    }

    /* ================================================================= *
     *  READ — BBS camera -> Blender                                     *
     * ================================================================= */

    /**
     * Read the current BBS camera into a flat {@link JsonObject} of BBS-space fields
     * ({@code x,y,z,pitch,yaw,roll,fov}). Returns {@code null} if no camera is available
     * (no client camera controller yet, e.g. before a world/editor is open).
     *
     * <p>This reads the SAME {@link Camera} the render path uses this frame; call it AFTER the
     * frame's {@code controller.setup(...)} has run (i.e. from a per-tick/per-frame handler) so
     * the values reflect what is on screen. The returned object is the {@code fields} payload
     * the outbound tick handler wraps in a {@link BridgeProtocol#camera} envelope; it also
     * records the pushed baseline for {@link #cameraChanged()}.</p>
     *
     * @return the BBS-space camera fields, or {@code null} if there is no camera to read
     */
    public static JsonObject readCurrentCamera()
    {
        Camera camera = getCamera();

        if (camera == null)
        {
            return null;
        }

        double x = camera.position.x;
        double y = camera.position.y;
        double z = camera.position.z;
        double pitch = MathUtils.toDeg(camera.rotation.x);
        double yaw = MathUtils.toDeg(camera.rotation.y);
        double roll = MathUtils.toDeg(camera.rotation.z);
        double fov = MathUtils.toDeg(camera.fov);

        /* Record baseline so cameraChanged() reflects "changed since last push". */
        CameraBridge.lastPushed[0] = x;
        CameraBridge.lastPushed[1] = y;
        CameraBridge.lastPushed[2] = z;
        CameraBridge.lastPushed[3] = pitch;
        CameraBridge.lastPushed[4] = yaw;
        CameraBridge.lastPushed[5] = roll;
        CameraBridge.lastPushed[6] = fov;
        CameraBridge.hasBaseline = true;

        JsonObject fields = new JsonObject();

        fields.addProperty(BridgeProtocol.CAM_X, x);
        fields.addProperty(BridgeProtocol.CAM_Y, y);
        fields.addProperty(BridgeProtocol.CAM_Z, z);
        fields.addProperty(BridgeProtocol.CAM_PITCH, pitch);
        fields.addProperty(BridgeProtocol.CAM_YAW, yaw);
        fields.addProperty(BridgeProtocol.CAM_ROLL, roll);
        fields.addProperty(BridgeProtocol.CAM_FOV, fov);

        return fields;
    }

    /**
     * Dirty-check: has the live BBS camera changed since the last {@link #readCurrentCamera()}
     * push? The outbound tick handler calls this first and only reads+sends when it returns
     * true, so a still camera produces no traffic.
     *
     * <p>ECHO GUARD: returns {@code false} while a remote (Blender) value is being applied
     * ({@link BridgeState#isApplyingRemote()}) — a value we just wrote from Blender must not be
     * detected as a local change and bounced straight back. The caller passes its
     * {@link BridgeState} instance (the bridge owns exactly one).</p>
     *
     * @param state the shared bridge state (for the echo guard); may be {@code null} to skip it
     * @return true if the camera differs from the last pushed snapshot beyond {@link #EPSILON}
     */
    public static boolean cameraChanged(BridgeState state)
    {
        if (state != null && state.isApplyingRemote())
        {
            return false;
        }

        Camera camera = getCamera();

        if (camera == null)
        {
            return false;
        }

        if (!CameraBridge.hasBaseline)
        {
            /* First observation — nothing pushed yet, so it "changed". */
            return true;
        }

        return differs(CameraBridge.lastPushed[0], camera.position.x)
            || differs(CameraBridge.lastPushed[1], camera.position.y)
            || differs(CameraBridge.lastPushed[2], camera.position.z)
            || differs(CameraBridge.lastPushed[3], MathUtils.toDeg(camera.rotation.x))
            || differs(CameraBridge.lastPushed[4], MathUtils.toDeg(camera.rotation.y))
            || differs(CameraBridge.lastPushed[5], MathUtils.toDeg(camera.rotation.z))
            || differs(CameraBridge.lastPushed[6], MathUtils.toDeg(camera.fov));
    }

    /* ================================================================= *
     *  WRITE — Blender -> BBS                                            *
     * ================================================================= */

    /**
     * Apply an inbound Blender camera onto BBS. {@code fields} is the CAMERA envelope's
     * {@code fields} object (BBS-space {@code x,y,z,pitch,yaw,roll,fov}, degrees + world units).
     * Missing fields keep the camera's current value for that channel.
     *
     * <p>ECHO GUARD: the whole apply runs inside {@link BridgeState#withRemoteApply(Runnable)}
     * so {@link BridgeState#applyingRemote} is raised before any BBS mutation and lowered in a
     * {@code finally} even on exception — the change-detection in {@link #cameraChanged(BridgeState)}
     * then skips re-emitting this value.</p>
     *
     * <p>WHERE THE VALUE STICKS: writing straight onto {@code getCameraController().camera} here
     * would be clobbered the very next frame, because {@code CameraMixin.onUpdate} re-runs
     * {@code controller.setup(...)} and the active editor/play controller re-writes the camera
     * from its clips. So this method ALSO parks the requested state in {@link LiveCameraOverride}
     * and arms it; {@link CameraControllerLiveOverrideMixin} (TAIL of
     * {@code CameraController.setup}) re-stamps these values every frame AFTER the editor has
     * written, which is the last write before the render path reads the camera. The immediate
     * write below makes the change visible within the same frame it arrives; the override keeps
     * it from being overwritten on subsequent frames.</p>
     *
     * @param fields BBS-space camera fields (may be a partial set); {@code null} is a no-op
     * @param state  the shared bridge state, providing the {@code applyingRemote} echo guard.
     *               Must not be {@code null} — the guard is mandatory (class contract).
     */
    public static void applyCamera(JsonObject fields, BridgeState state)
    {
        if (fields != null && fields.has("preview") && fields.get("preview").getAsBoolean())
        {
            com.blenderbridge.camera.RenderCameraPreview.receive(fields);
            return;
        }
        if (fields == null || state == null)
        {
            return;
        }

        state.withRemoteApply(() -> applyCameraGuarded(fields));
    }

    /**
     * The actual mutation, run inside the {@code applyingRemote} guard by
     * {@link #applyCamera(JsonObject, BridgeState)}. Reads each channel from {@code fields},
     * falling back to the camera's current value when a channel is absent, then both writes the
     * live camera immediately and updates {@link LiveCameraOverride} so the per-frame mixin
     * keeps re-applying it.
     */
    private static void applyCameraGuarded(JsonObject fields)
    {
        Camera camera = getCamera();

        if (camera == null)
        {
            return;
        }

        /* Fall back to the current on-screen value per-channel, so a partial CAMERA message
         * (e.g. fov-only) leaves the other channels untouched. */
        double x = BridgeProtocol.getDouble(fields, BridgeProtocol.CAM_X, camera.position.x);
        double y = BridgeProtocol.getDouble(fields, BridgeProtocol.CAM_Y, camera.position.y);
        double z = BridgeProtocol.getDouble(fields, BridgeProtocol.CAM_Z, camera.position.z);
        double pitch = BridgeProtocol.getDouble(fields, BridgeProtocol.CAM_PITCH, MathUtils.toDeg(camera.rotation.x));
        double yaw = BridgeProtocol.getDouble(fields, BridgeProtocol.CAM_YAW, MathUtils.toDeg(camera.rotation.y));
        double roll = BridgeProtocol.getDouble(fields, BridgeProtocol.CAM_ROLL, MathUtils.toDeg(camera.rotation.z));
        double fov = BridgeProtocol.getDouble(fields, BridgeProtocol.CAM_FOV, MathUtils.toDeg(camera.fov));

        /* Immediate write — makes the change visible this frame (before the next setup()). */
        writeCamera(camera, x, y, z, pitch, yaw, roll, fov);

        /* Persistent write — LiveCameraOverride is re-stamped at the TAIL of
         * CameraController.setup every frame (see CameraControllerLiveOverrideMixin), so the
         * editor/play controller cannot overwrite it on subsequent frames. */
        LiveCameraOverride.set(x, y, z, pitch, yaw, roll, fov);

        /* ECHO-FIX (2026-07-16): adopt the remote values as our own push baseline.
         *
         * Without this, the next tick's cameraChanged() compares the freshly-applied remote pose
         * against the STALE lastPushed baseline, sees a difference, and pushes it straight back to
         * Blender. Blender then re-applies it with float-rounding drift and pushes again — a
         * feedback loop the user observes as the camera jittering left/right for 30-60s until the
         * values happen to converge. The applyingRemote guard alone is too short-lived: it only
         * covers the apply itself, not the tick that follows it.
         *
         * Adopting the baseline here means "this pose came from Blender, it is NOT our change" —
         * the same origin-tagging idea the multi-user addon uses (applied_updates), reduced to a
         * single sync channel. Genuine BBS-side movement still differs from this baseline and
         * pushes normally. */
        CameraBridge.lastPushed[0] = x;
        CameraBridge.lastPushed[1] = y;
        CameraBridge.lastPushed[2] = z;
        CameraBridge.lastPushed[3] = pitch;
        CameraBridge.lastPushed[4] = yaw;
        CameraBridge.lastPushed[5] = roll;
        CameraBridge.lastPushed[6] = fov;
        CameraBridge.hasBaseline = true;
    }

    /**
     * Write BBS-space camera fields onto a {@link Camera} (degrees -&gt; radians for rotation
     * and fov). This is the single conversion point, mirroring {@code Position.apply(Camera)}.
     * Used by both the immediate apply above and the per-frame override mixin.
     */
    public static void writeCamera(Camera camera, double x, double y, double z, double pitch, double yaw, double roll, double fov)
    {
        /* DETECTION CHANNEL (2026-07-16, panel BLOCKER 1): the mixin calls this at setup()-RETURN,
         * i.e. the camera still holds what the ACTIVE CONTROLLER just wrote this frame. Park that
         * wish BEFORE overwriting — it is the only place the editor's own input is still visible
         * while Blender owns the camera (everything later in the frame sees only the override).
         * The tick handler diffs successive wishes; a change while an input-type controller runs
         * means the user is grabbing the camera in BBS -> ownership flips to BBS.
         * Body-only change (hotswappable); ALL state lives in the lazy-loadable CameraAuthority. */
        com.blenderbridge.camera.CameraAuthority.rememberControllerWanted(
            camera.position.x, camera.position.y, camera.position.z,
            camera.rotation.x, camera.rotation.y, camera.rotation.z,
            camera.fov);

        camera.position.set(x, y, z);
        camera.rotation.set(MathUtils.toRad((float) pitch), MathUtils.toRad((float) yaw), MathUtils.toRad((float) roll));
        camera.fov = MathUtils.toRad((float) fov);
    }

    /* ================================================================= *
     *  Helpers                                                          *
     * ================================================================= */

    /**
     * The live BBS camera, or {@code null} if the client camera controller is not up yet
     * (e.g. no world/editor). {@code getCameraController()} itself is a non-null static, but the
     * camera controller only exists on the client; guard for a null client controller so this is
     * safe to call from any tick.
     */
    private static Camera getCamera()
    {
        CameraController controller = BBSModClient.getCameraController();

        if (controller == null)
        {
            return null;
        }

        return controller.camera;
    }

    private static boolean differs(double a, double b)
    {
        return Math.abs(a - b) > EPSILON;
    }
}
