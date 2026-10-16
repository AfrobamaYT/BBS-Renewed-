package mchorse.bbs_mod.api.events;

import mchorse.bbs_mod.camera.clips.ClipFactoryData;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.factory.MapFactory;

/**
 * Posted on both sides once BBS has registered its own action clips, the clips of a replay's
 * action track. From BBS FS 83a15831a (Wemppy4/bbs-fs, MIT); {@link #getFactory()} stays for the
 * subscribers written against the older event.
 */
public class RegisterActionClipsEvent extends BaseRegisterClipsEvent
{
    public RegisterActionClipsEvent(MapFactory<Clip, ClipFactoryData> factory)
    {
        super(factory);
    }

    public MapFactory<Clip, ClipFactoryData> getFactory()
    {
        return this.factory;
    }
}
