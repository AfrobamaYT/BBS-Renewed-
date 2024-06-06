package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.generic.UIPropertyEditor;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

public class UIFloatKeyframeFactory extends UIKeyframeFactory<Float>
{
    private UITrackpad value;

    public UIFloatKeyframeFactory(Keyframe<Float> keyframe, UIPropertyEditor editor)
    {
        super(keyframe, editor);

        this.value = new UITrackpad((v) -> this.editor.setValue(v.floatValue()));
        this.value.setValue(keyframe.getValue());

        this.add(this.value);
    }
}