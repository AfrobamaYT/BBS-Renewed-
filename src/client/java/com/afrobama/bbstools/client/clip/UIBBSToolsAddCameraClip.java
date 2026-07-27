// Coded by Afrobama/Claude — Phase 10: clip-driven camera viewport UI editor
package com.afrobama.bbstools.client.clip;

import com.afrobama.bbstools.clip.BBSToolsAddCameraClip;

import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.clips.UITrackerClip;

/**
 * UI editor for {@link BBSToolsAddCameraClip}. We extend
 * {@code UITrackerClip} directly so the panel layout (target selector,
 * offset, angle, fov, lookAt, relative, active bitmask, showHand,
 * showHud) is inherited verbatim — identical authoring experience to
 * a regular tracker clip.
 *
 * <p>The behavioral difference (this clip drives a second viewport
 * instead of the main camera) is wired separately by Phase 11's
 * active-clip registry + Phase 12's pre-pass mixin retarget. From the
 * UI's perspective the clip looks and edits identically to TrackerClip.</p>
 */
public class UIBBSToolsAddCameraClip extends UITrackerClip
{
    public UIBBSToolsAddCameraClip(BBSToolsAddCameraClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

}
