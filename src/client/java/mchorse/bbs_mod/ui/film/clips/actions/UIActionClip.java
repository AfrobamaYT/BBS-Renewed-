package mchorse.bbs_mod.ui.film.clips.actions;

import mchorse.bbs_mod.actions.types.ActionClip;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.clips.UIClip;

public abstract class UIActionClip <T extends ActionClip> extends UIClip<T>
{
    public UIActionClip(T clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void addEnvelopes()
    {}
}