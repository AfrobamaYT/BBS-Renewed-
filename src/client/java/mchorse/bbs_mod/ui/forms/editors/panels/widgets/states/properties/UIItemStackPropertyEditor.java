package mchorse.bbs_mod.ui.forms.editors.panels.widgets.states.properties;

import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.properties.ItemStackProperty;
import mchorse.bbs_mod.forms.triggers.StateTrigger;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIItemStack;
import net.minecraft.item.ItemStack;

public class UIItemStackPropertyEditor extends UIFormPropertyEditor<ItemStack, ItemStackProperty>
{
    public UIItemStack itemStack;

    public UIItemStackPropertyEditor(ModelForm modelForm, StateTrigger trigger, String id, ItemStackProperty property)
    {
        super(modelForm, trigger, id, property);
    }

    @Override
    protected void fillData(ItemStackProperty property)
    {
        this.itemStack = new UIItemStack(this::setValue);
        this.itemStack.setStack(property.get());

        this.add(this.itemStack);
    }
}