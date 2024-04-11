package mchorse.bbs_mod.ui.framework.elements.input.keyframes.generic.factories;

import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIBlockStateEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.generic.UIPropertyEditor;
import mchorse.bbs_mod.utils.keyframes.generic.GenericKeyframe;
import net.minecraft.block.BlockState;

public class UIBlockStateKeyframeFactory extends UIKeyframeFactory<BlockState>
{
    private UIBlockStateEditor editor;

    public UIBlockStateKeyframeFactory(GenericKeyframe<BlockState> keyframe, UIPropertyEditor editor)
    {
        super(keyframe, editor);

        this.editor = new UIBlockStateEditor(keyframe::setValue);

        this.add(this.editor);

        this.editor.setBlockState(keyframe.getValue());
    }
}