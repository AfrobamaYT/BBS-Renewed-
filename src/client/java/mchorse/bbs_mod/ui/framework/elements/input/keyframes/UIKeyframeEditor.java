package mchorse.bbs_mod.ui.framework.elements.input.keyframes;

import mchorse.bbs_mod.camera.clips.overwrite.KeyframeClip;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.film.utils.CameraAxisConverter;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class UIKeyframeEditor extends UIElement
{
    public static final int[] COLORS = {Colors.RED, Colors.GREEN, Colors.BLUE, Colors.CYAN, Colors.MAGENTA, Colors.YELLOW, Colors.LIGHTEST_GRAY};
    public static final CameraAxisConverter CONVERTER = new CameraAxisConverter();

    public UIKeyframes view;
    public UIKeyframeFactory editor;
    public UIScrollView scrollView;

    private UIElement target;

    public UIKeyframeEditor(Function<Consumer<Keyframe>, UIKeyframes> factory)
    {
        this.view = factory.apply(this::pickKeyframe);

        this.add(this.view.full(this));
    }

    public UIKeyframeEditor target(UIElement target)
    {
        this.target = target;

        return this;
    }

    private void pickKeyframe(Keyframe keyframe)
    {
        if (this.scrollView != null)
        {
            this.scrollView.removeFromParent();
            this.scrollView = null;
            this.editor = null;
        }

        if (keyframe != null)
        {
            this.editor = UIKeyframeFactory.createPanel(keyframe, this.view);
            this.scrollView = UI.scrollView(5, 10, this.editor);

            this.add(this.scrollView);

            if (this.target != null)
            {
                this.scrollView.full(this.target);

                this.target.resize();
            }
            else
            {
                this.scrollView.relative(this).x(1F, -140).w(140).h(1F);
            }
        }

        this.view.w(1F, keyframe == null || this.target != null ? 0 : -140);
        this.resize();
    }

    public void updateConverter()
    {
        this.view.axisConverter(CONVERTER);
    }

    public void setChannel(KeyframeChannel channel, int color)
    {
        List<UIKeyframeSheet> sheets = this.view.getSheets();

        sheets.clear();
        this.view.addSheet(new UIKeyframeSheet(channel.getId(), IKey.raw(channel.getId()), color, channel, null));

        this.pickKeyframe(null);
    }

    public void setClip(KeyframeClip clip)
    {
        List<UIKeyframeSheet> sheets = this.view.getSheets();

        sheets.clear();

        for (int i = 0; i < clip.channels.length; i++)
        {
            KeyframeChannel channel = clip.channels[i];

            sheets.add(new UIKeyframeSheet(channel.getId(), IKey.raw(channel.getId()), COLORS[i], channel, null));
        }

        this.pickKeyframe(null);
    }
}