// Coded by Afrobama/Claude — Phase 10: clip-driven camera viewport
package com.afrobama.bbstools.clip;

import mchorse.bbs_mod.camera.clips.modifiers.TrackerClip;
import mchorse.bbs_mod.utils.clips.Clip;

/**
 * Camera clip that, when active in a film's camera timeline, drives a
 * separate viewport showing what the camera at this clip's offset/angle
 * (relative to the target replay) sees. Same properties as
 * {@link TrackerClip} — we extend it directly to inherit the selector,
 * offset, angle, fov, group, lookAt, relative, active bitmask, and
 * showHand/showHud fields, plus the keyframe-driven envelope envelope
 * handling from CameraClip.
 *
 * <p>Behavior split:</p>
 * <ul>
 *   <li>This clip does NOT modify the main camera position — its
 *       {@code applyClip} body is a no-op (inherited from TrackerClip,
 *       which is also a no-op on the server side). The pose driven by
 *       this clip is read out-of-band by the camera-preview pre-pass
 *       mixin and used to render a SECOND viewport, leaving the main
 *       camera untouched.</li>
 *   <li>Registered as {@code bbs:bbstools_add_camera} so it sits next
 *       to TrackerClip in the clip-type picker but is identified
 *       distinctly by the viewport / Blender-mesh renderer.</li>
 * </ul>
 */
public class BBSToolsAddCameraClip extends TrackerClip
{
    public BBSToolsAddCameraClip()
    {
        super();
    }

    @Override
    protected Clip create()
    {
        return new BBSToolsAddCameraClip();
    }

}
