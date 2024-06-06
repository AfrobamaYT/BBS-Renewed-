package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.ui.framework.elements.input.keyframes.generic.UIPropertyEditor;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

public class UIStringKeyframeFactory extends UIKeyframeFactory<String>
{
    private UITextbox string;

    public UIStringKeyframeFactory(Keyframe<String> keyframe, UIPropertyEditor editor)
    {
        super(keyframe, editor);

        this.string = new UITextbox(1000, (t) -> this.editor.setValue(t));
        this.string.setText(keyframe.getValue());

        this.add(this.string);
    }
}