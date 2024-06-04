package mchorse.bbs_mod.ui.framework.elements.input.keyframes.generic.factories;

import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.generic.UIPropertyEditor;
import mchorse.bbs_mod.utils.keyframes.generic.GenericKeyframe;

public class UIDoubleKeyframeFactory extends UIKeyframeFactory<Double>
{
    private UITrackpad value;

    public UIDoubleKeyframeFactory(GenericKeyframe<Double> keyframe, UIPropertyEditor editor)
    {
        super(keyframe, editor);

        this.value = new UITrackpad((v) -> this.editor.setValue(v));
        this.value.setValue(keyframe.getValue());

        this.add(this.value);
    }
}