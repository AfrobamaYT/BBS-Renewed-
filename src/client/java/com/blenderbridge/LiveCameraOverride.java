// Coded by Afrobama/Claude — Blender<->BBS live-sync bridge (Phase 1 camera bridge).
package com.blenderbridge;

/**
 * LiveCameraOverride — the "live Blender camera" the per-frame mixin re-stamps.
 *
 * <p>WHY THIS EXISTS: the active BBS camera controller re-writes
 * {@code getCameraController().camera} every render frame inside
 * {@code CameraController.setup(...)} (the editor plays its clips, the runner applies its
 * position, etc.). A one-shot write from the inbound tick handler would therefore survive at
 * most one frame. Instead, {@link CameraBridge#applyCamera} parks the requested state HERE and
 * arms it; {@link CameraControllerLiveOverrideMixin} injects at the TAIL of
 * {@code CameraController.setup} and, while {@link #isActive()}, copies these values back onto
 * the camera AFTER the controller wrote it — so it is the last write before the render path
 * reads the camera, and the editor can no longer clobber the Blender pose.</p>
 *
 * <p>All values are BBS space (world-unit position, degrees rotation + fov); the mixin converts
 * to radians via {@link CameraBridge#writeCamera}. Fields are written on the client main thread
 * (from the inbound tick handler) and read on the client main thread (from the render-frame
 * mixin); {@code volatile} keeps the armed flag honest and cheap even though both touches are on
 * the same thread.</p>
 *
 * <p>LIFECYCLE: {@link #set} arms the override (called on every accepted inbound CAMERA frame).
 * {@link #clear} disarms it — call when the Blender link drops or the user wants BBS to take the
 * camera back, so the editor/play camera resumes control on the next frame. The bridge's
 * connection teardown should call {@link #clear}.</p>
 */
public final class LiveCameraOverride
{
    /* OWNERSHIP (2026-07-16): all authority STATE lives in {@link com.blenderbridge.camera.CameraAuthority}
     * — a NEW class, so it lazy-loads and can grow without ever needing another boot window
     * (plan v2 task 1.1, panel H37). This class keeps only the parked pose + the armed flag;
     * its methods consult the authority so the mixin (byte-identical!) stays a dumb stamp. */

    private static volatile boolean active;

    private static volatile double x;
    private static volatile double y;
    private static volatile double z;
    private static volatile double pitch;
    private static volatile double yaw;
    private static volatile double roll;
    private static volatile double fov;

    private LiveCameraOverride()
    {
        /* static-only holder */
    }

    /** Arm the override with a BBS-space pose. Called for every accepted inbound CAMERA frame. */
    public static void set(double x, double y, double z, double pitch, double yaw, double roll, double fov)
    {
        LiveCameraOverride.x = x;
        LiveCameraOverride.y = y;
        LiveCameraOverride.z = z;
        LiveCameraOverride.pitch = pitch;
        LiveCameraOverride.yaw = yaw;
        LiveCameraOverride.roll = roll;
        LiveCameraOverride.fov = fov;
        LiveCameraOverride.active = true;
        /* OWNERSHIP MIGRATION (2026-07-16): an inbound frame historically implied "Blender owns
         * the camera" — that implicit grab pinned the user's camera for a whole session. Until
         * the Blender-side sends explicit CAMERA_OWNER messages, an accepted frame still arms
         * Blender ownership, but anything (BBS user input, disconnect, HELLO) can now take it
         * away via CameraAuthority.request — the grab is no longer permanent. */
        if (com.blenderbridge.camera.CameraAuthority.get() == com.blenderbridge.camera.CameraAuthority.Owner.NOBODY)
        {
            com.blenderbridge.camera.CameraAuthority.request(
                com.blenderbridge.camera.CameraAuthority.Owner.BLENDER, "inbound-frame (legacy implicit grab)");
        }
    }

    /** Disarm the override so the editor/play camera resumes control next frame. */
    public static void clear()
    {
        LiveCameraOverride.active = false;
    }

    /** @return true while a Blender camera pose should override the BBS camera every frame. */
    public static boolean isActive()
    {
        /* owner-gated: only an armed override WITH Blender ownership stamps the camera.
         * owner==BBS or NOBODY -> the mixin is a no-op and the native camera runs. */
        return LiveCameraOverride.active
            && com.blenderbridge.camera.CameraAuthority.get() == com.blenderbridge.camera.CameraAuthority.Owner.BLENDER;
    }

    public static double getX()
    {
        return LiveCameraOverride.x;
    }

    public static double getY()
    {
        return LiveCameraOverride.y;
    }

    public static double getZ()
    {
        return LiveCameraOverride.z;
    }

    public static double getPitch()
    {
        return LiveCameraOverride.pitch;
    }

    public static double getYaw()
    {
        return LiveCameraOverride.yaw;
    }

    public static double getRoll()
    {
        return LiveCameraOverride.roll;
    }

    public static double getFov()
    {
        return LiveCameraOverride.fov;
    }
}
