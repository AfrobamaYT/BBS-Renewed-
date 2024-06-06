package mchorse.bbs_mod.ui.framework.elements.input.keyframes;

import mchorse.bbs_mod.forms.properties.IFormProperty;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

public class UIKeyframeSheet
{
    /* Meta data */
    public final String id;
    public final IKey title;
    public final int color;

    public final KeyframeChannel channel;
    public final UIKeyframeSelection selection;
    public final IFormProperty property;

    public UIKeyframeSheet(String id, IKey title, int color, KeyframeChannel channel, IFormProperty property)
    {
        this.id = id;
        this.title = title;
        this.color = color;

        this.channel = channel;
        this.selection = new UIKeyframeSelection(channel);
        this.property = property;
    }
}
