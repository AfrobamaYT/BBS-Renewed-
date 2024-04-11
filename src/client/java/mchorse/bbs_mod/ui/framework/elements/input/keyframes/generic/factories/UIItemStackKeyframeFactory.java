package mchorse.bbs_mod.ui.framework.elements.input.keyframes.generic.factories;

import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIItemStackEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.generic.UIPropertyEditor;
import mchorse.bbs_mod.utils.keyframes.generic.GenericKeyframe;
import net.minecraft.item.ItemStack;

public class UIItemStackKeyframeFactory extends UIKeyframeFactory<ItemStack>
{
    private UIItemStackEditor editor;

    public UIItemStackKeyframeFactory(GenericKeyframe<ItemStack> keyframe, UIPropertyEditor editor)
    {
        super(keyframe, editor);

        this.editor = new UIItemStackEditor(keyframe::setValue);

        this.add(this.editor);

        this.editor.setStack(keyframe.getValue());
    }
}