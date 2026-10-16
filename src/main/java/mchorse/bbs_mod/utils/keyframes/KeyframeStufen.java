package mchorse.bbs_mod.utils.keyframes;

import mchorse.bbs_mod.BBSSettings;

/**
 * Maya's stepped preview for blocking: while it is on, every keyed channel holds the value of the
 * key before the playhead until the next key, without touching any key's interpolation. Applied
 * where playback reads channels (KeyframeChannel.find/interpolate, the replay's position and
 * rotation); graph editors set up their own segments and keep drawing the real curves, and model
 * animations (CubicModelAnimator) stay smooth.
 */
public final class KeyframeStufen
{
    private KeyframeStufen()
    {}

    public static boolean an()
    {
        return BBSSettings.editorSteppedPreview != null && BBSSettings.editorSteppedPreview.get();
    }

    public static <T> KeyframeSegment<T> stufe(KeyframeSegment<T> segment)
    {
        if (segment != null && !segment.isSame() && an())
        {
            segment.x = segment.x >= 1F ? 1F : 0F;
        }

        return segment;
    }
}
