package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.camera.clips.CameraClipContext;
import mchorse.bbs_mod.camera.clips.misc.AudioClip;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.utils.clips.Clip;

/** Camera-image evaluation must never allocate audio sources, including from nested/video clips. */
public final class KameraBildKontext extends CameraClipContext
{
    @Override
    public boolean apply(Clip clip, Position position)
    {
        return AudioClip.NO_AUDIO.test(clip) && super.apply(clip, position);
    }
}
