package mchorse.bbs_mod.ui.film.utils.keyframes;

import mchorse.bbs_mod.camera.clips.overwrite.KeyframeClip;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.utils.CameraAxisConverter;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.generic.UIProperty;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.generic.UIPropertyEditor;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.generic.GenericKeyframeChannel;

import java.util.List;

public class UICameraDopeSheetEditor extends UIPropertyEditor<UIDopeSheetView>
{
    public static final int[] COLORS = {Colors.RED, Colors.GREEN, Colors.BLUE, Colors.CYAN, Colors.MAGENTA, Colors.YELLOW, Colors.LIGHTEST_GRAY};
    public static final CameraAxisConverter CONVERTER = new CameraAxisConverter();

    public UICameraDopeSheetEditor(IUIClipsDelegate delegate)
    {
        super(delegate);

        this.properties.editor = delegate;
    }

    public void updateConverter()
    {
        this.setConverter(CONVERTER);
    }

    @Override
    protected UIDopeSheetView createElement(IUIClipsDelegate delegate)
    {
        return new UIDopeSheetView(delegate, this::fillData);
    }

    public void setChannel(GenericKeyframeChannel channel, int color)
    {
        List<UIProperty> sheets = this.properties.properties;

        sheets.clear();
        this.properties.clearSelection();

        sheets.add(new UIProperty(channel.getId(), IKey.raw(channel.getId()), color, channel, null));

        this.frameButtons.setVisible(false);
    }

    public void setClip(KeyframeClip clip)
    {
        List<UIProperty> sheets = this.properties.properties;

        sheets.clear();
        this.properties.clearSelection();

        for (int i = 0; i < clip.channels.length; i++)
        {
            GenericKeyframeChannel channel = clip.channels[i];

            sheets.add(new UIProperty(channel.getId(), IKey.raw(channel.getId()), COLORS[i], channel, null));
        }

        this.frameButtons.setVisible(false);
    }

    public void setChannels(List<GenericKeyframeChannel> channels, List<Integer> colors)
    {
        List<UIProperty> sheets = this.properties.properties;

        sheets.clear();
        this.properties.clearSelection();

        for (int i = 0; i < channels.size(); i++)
        {
            GenericKeyframeChannel channel = channels.get(i);

            sheets.add(new UIProperty(channel.getId(), IKey.raw(channel.getId()), colors.get(i), channel, null));
        }

        this.frameButtons.setVisible(false);
    }
}