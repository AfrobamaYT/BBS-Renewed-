package mchorse.bbs_mod.ui.forms.editors.panels.widgets.states.properties;

import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.properties.BlockStateProperty;
import mchorse.bbs_mod.forms.triggers.StateTrigger;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIBlockStateEditor;
import net.minecraft.block.BlockState;

public class UIBlockStatePropertyEditor extends UIFormPropertyEditor<BlockState, BlockStateProperty>
{
    public UIBlockStateEditor blockState;

    public UIBlockStatePropertyEditor(ModelForm modelForm, StateTrigger trigger, String id, BlockStateProperty property)
    {
        super(modelForm, trigger, id, property);
    }

    @Override
    protected void fillData(BlockStateProperty property)
    {
        this.blockState = new UIBlockStateEditor(this::setValue);
        this.blockState.setBlockState(property.get());

        this.add(this.blockState);
    }
}