// Coded by Afrobama/Claude — Phase 15 v8: per-tile free-mode pose overrides
package com.afrobama.bbstools.client.campreview;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-tile "free mode" pose state. When a {@link UIClipCameraPreview}
 * tile flips into free mode (F key or mode-toggle button), it publishes
 * its current pose array here. The pre-pass mixin reads this map first
 * for each active clip; if a pose override is present, the alt-cam
 * render uses that pose instead of the clip-animated one. Result: the
 * tile shows a static viewpoint regardless of what the camera clip's
 * angle slider does on the timeline.
 *
 * <p>Map entries are removed when the tile flips back to camera mode
 * or when the clip deactivates.</p>
 *
 * <p>Pose layout: {@code [x, y, z, yaw, pitch]}.</p>
 */
public final class AltCamModeOverrides
{
    /** Per-clip-UUID override pose. {@code null}/missing means camera
     *  mode → use registry's clip-driven pose. */
    public static final ConcurrentHashMap<UUID, double[]> FREE_POSES = new ConcurrentHashMap<>();

    private AltCamModeOverrides() {}

}
