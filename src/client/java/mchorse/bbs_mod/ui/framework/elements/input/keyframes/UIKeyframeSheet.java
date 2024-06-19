package mchorse.bbs_mod.ui.framework.elements.input.keyframes;

import mchorse.bbs_mod.forms.properties.IFormProperty;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.utils.interps.Interpolation;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

import java.util.List;

public class UIKeyframeSheet
{
    /* Meta data */
    public final String id;
    public final IKey title;
    public final int color;
    public final boolean separator;

    public final KeyframeChannel channel;
    public final KeyframeSelection selection;
    public final IFormProperty property;

    public UIKeyframeSheet(int color, boolean separator, KeyframeChannel channel, IFormProperty property)
    {
        this(channel.getId(), IKey.raw(channel.getId()), color, separator, channel, property);
    }

    public UIKeyframeSheet(String id, IKey title, int color, boolean separator, KeyframeChannel channel, IFormProperty property)
    {
        this.id = id;
        this.title = title;
        this.color = color;
        this.separator = separator;

        this.channel = channel;
        this.selection = new KeyframeSelection(channel);
        this.property = property;
    }

    public void sort()
    {
        List<Keyframe> selected = this.selection.getSelected();

        this.channel.sort();
        this.selection.clear();

        List keyframes = this.channel.getKeyframes();

        for (Keyframe keyframe : selected)
        {
            this.selection.add(keyframes.indexOf(keyframe));
        }
    }

    public void setTickBy(long diff)
    {
        for (Keyframe keyframe : this.selection.getSelected())
        {
            keyframe.setTick(keyframe.getTick() + diff);
        }
    }

    public void setValue(Object value)
    {
        for (Keyframe keyframe : this.selection.getSelected())
        {
            keyframe.setValue(this.channel.getFactory().copy(value));
        }
    }

    public void setInterpolation(Interpolation interpolation)
    {
        for (Keyframe keyframe : this.selection.getSelected())
        {
            keyframe.getInterpolation().copy(interpolation);
        }
    }

    public void remove(Keyframe keyframe)
    {
        int index = this.channel.getKeyframes().indexOf(keyframe);

        if (index >= 0)
        {
            this.selection.remove(index);
            this.channel.remove(index);
        }
    }
}
