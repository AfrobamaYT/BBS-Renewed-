// Coded by Afrobama/Claude — BBS Renewed Camera Preview Phase 1
package com.afrobama.bbstools.client.campreview;

import mchorse.bbs_mod.film.replays.Replay;
import com.mojang.blaze3d.pipeline.TextureTarget;
import java.util.UUID;

/**
 * One camera-preview window's state.
 *
 * <p>Holds a small offscreen framebuffer that the world is rendered into
 * once per frame from this camera's pose. Pose comes either from a free
 * keyframe-driven position (manual mode) or from tracking a Replay's
 * keyframes (tracker mode, mirroring tracker-clip semantics).</p>
 *
 * <p>The actual UI window that displays the framebuffer lives in
 * {@code UICameraPreviewWindow}. This class is purely state + framebuffer
 * lifecycle so it can survive screen open/close.</p>
 */
public final class CameraPreview
{
    public final UUID id = UUID.randomUUID();

    /** Display label (shown in window header). */
    public String name;

    /** Pose mode — see {@link Mode}. */
    public Mode mode = Mode.MANUAL;

    /** Manual-mode camera position. Initialized to local player pos + a
     *  small upward offset on first creation so the user sees something. */
    public double posX, posY, posZ;

    /** Manual-mode camera rotation. */
    public float yaw, pitch;

    /** Tracker mode: follow this Replay's keyframes at the current film
     *  scrub-tick + add the offset/angle below. Mirrors TrackerClip
     *  semantics so the muscle-memory transfers. */
    public Replay trackedReplay;

    public float trackOffsetX, trackOffsetY, trackOffsetZ;
    public float trackOffsetYaw, trackOffsetPitch;

    /** Framebuffer resolution. Kept small for low overhead — 320×180 is
     *  enough for "is the framing right" preview. Bumpable later via
     *  resolution toggle in Phase 3. */
    public int fbWidth = 320;
    public int fbHeight = 180;

    /** Allocated lazily on the render thread so we don't touch GL state
     *  from the wrong thread. Null until first render fires. */
    public TextureTarget framebuffer;

    /** UI window position (in screen-space pixels). Free to drag. */
    public int windowX = 8;
    public int windowY = 8;
    public int windowW = 320;
    public int windowH = 180;

    /** When false, skipped during render + UI draw — same effect as
     *  "minimize" on a real window. */
    public boolean enabled = true;

    /** Coded by Afrobama/Claude — Phase 8 hardening.
     *  Set to 60 when a pre-pass render throws (Iris pipeline rebuild,
     *  dimension change race, etc). Decrements per frame; while > 0
     *  we skip rendering this preview so we don't spam exceptions.
     *  Auto-recovery: when it hits 0, the next frame retries. */
    public volatile int errorCooldownFrames;

    /** Last successfully-resolved pose {x, y, z, yaw, pitch}. Used as a
     *  fallback when tracker mode hits a moment where keyframe
     *  interpolation can't produce a valid result (no film loaded,
     *  tick out of range, etc). */
    public final double[] lastResolvedPose = new double[5];
    public boolean hasResolvedPose;

    public CameraPreview(String name)
    {
        this.name = name;
    }

    public void disposeFramebuffer()
    {
        if (this.framebuffer != null)
        {
            try { this.framebuffer.destroyBuffers(); }
            catch (Throwable ignored) {}
            this.framebuffer = null;
        }
    }

    public enum Mode
    {
        /** Manually placed camera — pose is the {@code posX/Y/Z + yaw/pitch}
         *  on this preview directly. User drags handles in the world via
         *  the editor's transform gizmo. */
        MANUAL,

        /** Track a Replay's keyframes; add offset/angle. */
        TRACKER;
    }

}
