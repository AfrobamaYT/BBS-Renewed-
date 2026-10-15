// Coded by Afrobama/Claude — Blender<->BBS live-sync bridge (Phase 1 camera authority).
package com.blenderbridge.camera;

import com.mojang.logging.LogUtils;

import org.slf4j.Logger;

/**
 * Single source of truth for WHO drives the shared camera — the structural fix for the
 * 2026-07-16 "camera pinned" bug (both sides wrote, last writer won, the user lost).
 *
 * <p>WHY A NEW CLASS (plan v2 task 1.1, panel H37): fields on already-loaded classes can only
 * ship inside the one guaranteed {@code -Pdebug} boot window — a NEW class lazy-loads the moment
 * a hotswapped body first touches it, and can grow MORE state later without ever costing another
 * boot. So every byte of authority state lives here; {@code LiveCameraOverride},
 * {@code CameraBridge} and {@code BlenderBridgeClient} keep only (hotswappable) body changes.</p>
 *
 * <p>Model (research-backed, R4: Fiedler ownership sequences, MeshSync editable-allowlists —
 * last-write-wins is for human text collaboration, never for live property streams): the camera
 * has exactly ONE writer at a time. {@code NOBODY} means fully native BBS behaviour.
 * Transitions are logged with their reason so a stuck camera is diagnosable from the log alone.</p>
 *
 * <p>Wire note: the enum constants are deliberately English ({@code NOBODY}) — they ARE the wire
 * strings of the {@code CAMERA_OWNER} message and the Blender side already speaks them; renaming
 * to the plan's German {@code NIEMAND} would be a silent protocol break.</p>
 */
public final class CameraAuthority
{
    private static final Logger LOGGER = LogUtils.getLogger();

    public enum Owner
    {
        NOBODY, BBS, BLENDER
    }

    private static volatile Owner owner = Owner.NOBODY;

    /** Set when the transport drops; the tick handler consumes it and releases ownership. */
    private static volatile boolean connectionLost;

    /**
     * Pose the ACTIVE BBS controller wrote this frame, parked by {@code CameraBridge.writeCamera}
     * immediately BEFORE the Blender override stamps over it. This is the only place BBS-user
     * input remains visible while Blender owns the camera. Layout:
     * [0]=x [1]=y [2]=z [3]=pitch(rad) [4]=yaw(rad) [5]=roll(rad) [6]=fov(rad).
     * double[] (not the plan's float[]): BBS' Camera exposes doubles — measured, no narrowing.
     */
    private static final double[] controllerWanted = new double[7];
    private static volatile boolean controllerWantedValid;

    /** Previous tick's controller wish — the diff basis for {@link #grabDetected()}. */
    private static double[] lastControllerWanted;

    /** Playback exception for the BBS render path (plan 6.2): render runs may drive the camera. */
    private static volatile boolean renderfahrt;

    private CameraAuthority()
    {
        /* static-only holder */
    }

    public static Owner get()
    {
        return owner;
    }

    /** Change owner, logging the transition and its reason. Null-safe: null releases. */
    public static void request(Owner newOwner, String source)
    {
        Owner effective = newOwner == null ? Owner.NOBODY : newOwner;
        if (effective != owner)
        {
            LOGGER.info("[blenderbridge] camera owner {} -> {} ({})", owner, effective, source);
        }
        owner = effective;
    }

    public static boolean renderfahrt()
    {
        return renderfahrt;
    }

    public static void setRenderfahrt(boolean on)
    {
        renderfahrt = on;
    }

    /** Transport dropped: remember it; the next tick releases ownership on the main thread. */
    public static void onConnectionLost()
    {
        connectionLost = true;
    }

    /** Consume the connection-lost flag (tick handler); true at most once per drop. */
    public static boolean consumeConnectionLost()
    {
        if (!connectionLost)
        {
            return false;
        }
        connectionLost = false;
        return true;
    }

    /** Park what the BBS controller wrote this frame (called by writeCamera BEFORE overwriting). */
    public static void rememberControllerWanted(double x, double y, double z,
                                                double pitch, double yaw, double roll, double fov)
    {
        controllerWanted[0] = x;
        controllerWanted[1] = y;
        controllerWanted[2] = z;
        controllerWanted[3] = pitch;
        controllerWanted[4] = yaw;
        controllerWanted[5] = roll;
        controllerWanted[6] = fov;
        controllerWantedValid = true;
    }

    /**
     * True when the controller's wish moved since the previous tick beyond float-noise —
     * i.e. someone/something in BBS is actively steering while Blender holds the camera.
     * Call once per tick (it advances the comparison baseline).
     */
    public static boolean grabDetected()
    {
        if (!controllerWantedValid)
        {
            return false;
        }

        double[] wanted = controllerWanted.clone();
        boolean moved = false;

        if (lastControllerWanted != null)
        {
            for (int i = 0; i < wanted.length; i++)
            {
                /* epsilon: radian conversions jitter in the last bits; that is not a user grab */
                if (Math.abs(wanted[i] - lastControllerWanted[i]) > 1.0e-4)
                {
                    moved = true;
                    break;
                }
            }
        }

        lastControllerWanted = wanted;
        return moved;
    }
}
