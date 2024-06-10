package mchorse.bbs_mod.ui.film.utils.keyframes;

import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.UIClips;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

import java.util.function.Consumer;

public class UIFilmKeyframes extends UIKeyframes
{
    public IUIClipsDelegate editor;

    public UIFilmKeyframes(IUIClipsDelegate delegate, Consumer<Keyframe> callback)
    {
        super(callback);

        this.editor = delegate;
    }

    public long getClipOffset()
    {
        if (this.editor == null || this.editor.getClip() == null)
        {
            return 0;
        }

        return this.editor.getClip().tick.get();
    }

    public int getOffset()
    {
        if (this.editor == null)
        {
            return 0;
        }

        return (int) (this.editor.getCursor() - this.getClipOffset());
    }

    @Override
    protected void moveNoKeyframes(UIContext context)
    {
        if (this.editor != null)
        {
            long offset = this.getClipOffset();

            this.editor.setCursor((int) (this.fromGraphX(context.mouseX) + offset));
        }
    }

    @Override
    protected void renderBackground(UIContext context)
    {
        super.renderBackground(context);

        if (this.editor != null)
        {
            int cx = this.toGraphX(this.getOffset());
            String label = TimeUtils.formatTime(this.getOffset()) + "/" + TimeUtils.formatTime(this.getDuration());

            UIClips.renderCursor(context, label, this.area, cx - 1);
        }
    }
}